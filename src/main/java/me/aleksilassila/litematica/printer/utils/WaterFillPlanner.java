package me.aleksilassila.litematica.printer.utils;

import net.minecraft.tags.BlockTags;
import cc.gahaha.fastmultiversion.Mc;
import me.aleksilassila.litematica.printer.multiversion.Multiversion;
import static fi.dy.masa.litematica.config.Configs.Visuals.IGNORE_EXISTING_BLOCKS;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.util.IgnoreBlockRegistry;
import fi.dy.masa.litematica.world.WorldSchematic;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.guides.interaction.BreakBlockGuide;
import me.aleksilassila.litematica.printer.mixin.FlowingFluidInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Util;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CoralBlock;
import net.minecraft.world.level.block.CoralFanBlock;
import net.minecraft.world.level.block.CoralPlantBlock;
import net.minecraft.world.level.block.CoralWallFanBlock;
import net.minecraft.world.level.block.FrostedIceBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * With {@link Configs#FLUID_MINIMIZE_BUCKETS}, decides which water blocks of the schematic get a bucket. Each connected
 * body of water is planned with {@link InfiniteWaterSolver} once the blocks around it are built, the plan is checked with
 * Fluid Predict when it is installed, and only the planned blocks are filled: the rest is left to infinite water.
 */
public class WaterFillPlanner {
    // waiting for the floor and walls, check again after this long
    private static final int WAIT_TICKS = 40;
    // plans are redone this often to follow changes in the world and the schematic
    private static final int PLAN_TICKS = 600;
    // all buckets are in but nothing filled for this long: fill the rest directly
    private static final int STALL_TICKS = 200;
    private static final int PROGRESS_TICKS = 20;
    // a block that infinite water fills gets a bucket anyway if it is still not a source after this long, in case the
    // game rule is off
    private static final int SELF_FILL_TICKS = 40;
    private static final long SIMULATION_BUDGET_NANOS = 3_000_000L;
    private static final BlockState WATER_SOURCE = Blocks.WATER.defaultBlockState();
    private static final Direction[] AROUND = new Direction[]{
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private enum Stage {
        /** The floor and walls around the water are not built yet. */
        WAITING,
        /** The solver runs on a background thread, it can take seconds on big bodies. */
        PLANNING,
        /** Fluid Predict is checking the plan. */
        SIMULATING,
        READY,
        /** Too big to plan, every block gets a bucket. */
        UNPLANNED
    }

    private record Plan(IntArrayList buckets, int lowerBound) {}

    private static final class Body {
        private final long[] cells;
        private final BlockPos start;
        private final LongOpenHashSet buckets = new LongOpenHashSet();
        // ice the plan expects to turn into a water source, other ice is planned as a wall
        private final LongOpenHashSet iceToWater = new LongOpenHashSet();
        private Stage stage;
        private int expiresAt;
        private @Nullable CompletableFuture<Plan> plan;
        private FluidPredictCompact.@Nullable Simulation simulation;
        private int filledCount = -1;
        private int lastProgressTick;
        private boolean bucketsFilled = false;

        private Body(long[] cells, BlockPos start) {
            this.cells = cells;
            this.start = start;
        }
    }

    private final Long2ObjectOpenHashMap<Body> bodyByCell = new Long2ObjectOpenHashMap<>();
    private final List<Body> bodies = new ArrayList<>();
    // when each water block was first seen ready to fill by itself
    private final Long2IntOpenHashMap selfFillSince = new Long2IntOpenHashMap();
    // planned buckets that are not filled yet, for the renderer
    private final LongArrayList plannedBuckets = new LongArrayList();
    private int tick = 0;

    /**
     * Water source blocks and waterlogged blocks, the blocks infinite water can fill.
     */
    public static boolean isWaterCell(BlockState state) {
        return state.getFluidState().isSourceOfType(Fluids.WATER)
                && (state.getBlock() instanceof LiquidBlock || state.hasProperty(BlockStateProperties.WATERLOGGED));
    }

    /**
     * Whether the printer should empty a bucket into this block now.
     */
    public boolean shouldFill(SchematicBlockState state) {
        if (!isWaterCell(state.targetState)) {
            return true;
        }
        if (waitsForInfiniteWater(state)) {
            return false;
        }
        if (!Configs.FLUID_MINIMIZE_BUCKETS.getBooleanValue()) {
            return true;
        }

        if (savesCoral(state)) {
            return true;
        }

        long pos = state.blockPos.asLong();
        Body body = bodyByCell.get(pos);
        if (body == null) {
            body = plan(state.world, state.schematic, state.blockPos);
        }

        return switch (body.stage) {
            case UNPLANNED -> true;
            case WAITING, PLANNING, SIMULATING -> false;
            case READY -> body.buckets.contains(pos) || body.bucketsFilled && tick - body.lastProgressTick > STALL_TICKS;
        };
    }

    /**
     * A block between water sources turns into one by infinite water within a few ticks, a waterloggable block is
     * waterlogged, so it gets no bucket unless it is still not a source after {@link #SELF_FILL_TICKS}.
     */
    private boolean waitsForInfiniteWater(SchematicBlockState state) {
        long pos = state.blockPos.asLong();
        if (!fillsByItself(state.world, state.blockPos, state.currentState)) {
            selfFillSince.remove(pos);
            return false;
        }

        if (!selfFillSince.containsKey(pos)) {
            selfFillSince.put(pos, tick);
        }
        return tick - selfFillSince.get(pos) < SELF_FILL_TICKS;
    }

    /**
     * Whether {@code FlowingFluid#getNewLiquid} turns the block into a source: two horizontal sources it connects to, and
     * something solid or a source below.
     */
    @SuppressWarnings("deprecation") // BlockStateBase#isSolid, which FlowingFluid#getNewLiquid itself checks
    private static boolean fillsByItself(Level level, BlockPos pos, BlockState state) {
        int sources = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getFluidState().isSourceOfType(Fluids.WATER)
                    && FlowingFluidInvoker.litematica_printer$canPassThroughWall(direction, level, pos, state, neighborPos, neighborState)) {
                sources++;
            }
        }
        if (sources < 2) {
            return false;
        }

        BlockState below = level.getBlockState(pos.below());
        return below.isSolid() || below.getFluidState().isSourceOfType(Fluids.WATER);
    }

    /**
     * Live coral dies a few seconds after it is placed with no water next to it, long before a body waiting for its
     * floor and walls gets any. A block next to such a coral of the schematic, or the coral itself when it is
     * waterlogged in the schematic, gets its bucket right away. The water flowing from it keeps nearby coral alive too.
     */
    private static boolean savesCoral(SchematicBlockState state) {
        if (isDryCoral(state, state.blockPos)) {
            return true;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            if (isDryCoral(state, pos.setWithOffset(state.blockPos, direction))) {
                return true;
            }
        }
        return false;
    }

    // Mirrors CoralBlock#scanForWater and BaseCoralPlantTypeBlock#scanForWater
    private static boolean isDryCoral(SchematicBlockState state, BlockPos pos) {
        BlockState current = state.world.getBlockState(pos);
        Block block = current.getBlock();
        if (!(block instanceof CoralBlock || block instanceof CoralPlantBlock
                || block instanceof CoralFanBlock || block instanceof CoralWallFanBlock)) {
            return false;
        }
        if (!state.schematic.getBlockState(pos).is(block) || current.getValueOrElse(BlockStateProperties.WATERLOGGED, false)) {
            return false;
        }

        for (Direction direction : Direction.values()) {
            if (state.world.getFluidState(pos.relative(direction)).is(FluidTags.WATER)) {
                return false;
            }
        }
        return true;
    }

    public void onClientTick() {
        tick++;
        Level level = Minecraft.getInstance().level;
        long deadline = System.nanoTime() + SIMULATION_BUDGET_NANOS;
        if (level == null) {
            selfFillSince.clear();
        } else if (tick % PROGRESS_TICKS == 0) {
            // blocks that filled are never asked about again
            selfFillSince.long2IntEntrySet().removeIf(entry -> tick - entry.getIntValue() > PLAN_TICKS);
        }
        Iterator<Body> iterator = bodies.iterator();
        plannedBuckets.clear();
        boolean render = Configs.FLUID_MINIMIZE_RENDER.getBooleanValue();

        while (iterator.hasNext()) {
            Body body = iterator.next();
            boolean busy = body.stage == Stage.PLANNING || body.stage == Stage.SIMULATING;
            if (level == null || !busy && tick > body.expiresAt) {
                for (long cell : body.cells) {
                    bodyByCell.remove(cell, body);
                }
                iterator.remove();
                continue;
            }

            if (body.stage == Stage.PLANNING) {
                finishPlan(level, body);
            } else if (body.stage == Stage.SIMULATING) {
                long budget = deadline - System.nanoTime();
                if (budget > 0) {
                    advanceSimulation(body, budget);
                }
            } else if (body.stage == Stage.READY) {
                if (tick % PROGRESS_TICKS == 0) {
                    updateProgress(level, body);
                }
                if (render) {
                    collectPlannedBuckets(level, body);
                }
            }
        }
    }

    /**
     * Planned buckets of the bodies ready to fill that are not water sources yet, updated every tick.
     */
    public LongList getPlannedBuckets() {
        return plannedBuckets;
    }

    private void collectPlannedBuckets(Level level, Body body) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (long cell : body.buckets) {
            // a bucket can't go into water that froze
            BlockState state = level.getBlockState(pos.set(cell));
            if (!state.getFluidState().isSourceOfType(Fluids.WATER) && !isIce(state)) {
                plannedBuckets.add(cell);
            }
        }
    }

    private Body plan(Level level, WorldSchematic schematic, BlockPos start) {
        int maxCells = Configs.FLUID_MINIMIZE_MAX_BLOCKS.getIntegerValue();
        LongOpenHashSet cellSet = findBody(level, schematic, start, maxCells);
        Body body = new Body(cellSet.toLongArray(), start.immutable());
        bodies.add(body);
        for (long cell : body.cells) {
            bodyByCell.put(cell, body);
        }

        if (body.cells.length > maxCells) {
            Printer.printDebug("WaterFillPlanner: water at {} is over {} blocks, filling all of it", start, maxCells);
            setStage(body, Stage.UNPLANNED);
            return body;
        }

        int unbuilt = countUnbuilt(level, schematic, body.cells, cellSet);
        if (unbuilt > 0) {
            Printer.printDebug("WaterFillPlanner: water at {} waits for {} blocks around it", start, unbuilt);
            setStage(body, Stage.WAITING);
            return body;
        }

        IceBreaking iceBreaking = new IceBreaking();
        for (long cell : body.cells) {
            BlockPos pos = BlockPos.of(cell);
            BlockState state = level.getBlockState(pos);
            if (isIce(state) && turnsIntoWater(level, pos, state, iceBreaking)) {
                body.iceToWater.add(cell);
            }
        }

        // the world is read here, only the solver itself runs on the other thread
        InfiniteWaterSolver solver = createSolver(level, body.cells, body.iceToWater);
        body.plan = CompletableFuture.supplyAsync(() -> new Plan(solver.solve(), solver.lowerBound()), Util.backgroundExecutor());
        setStage(body, Stage.PLANNING);
        return body;
    }

    private void finishPlan(Level level, Body body) {
        CompletableFuture<Plan> future = body.plan;
        if (future == null || !future.isDone()) {
            return;
        }
        body.plan = null;

        Plan plan;
        try {
            plan = future.join();
        } catch (CompletionException | CancellationException e) {
            Printer.logger.error("WaterFillPlanner: planning water at {} failed, filling all of it", body.start, e);
            setStage(body, Stage.UNPLANNED);
            return;
        }

        for (int i = 0; i < plan.buckets().size(); i++) {
            body.buckets.add(body.cells[plan.buckets().getInt(i)]);
        }
        Printer.printDebug("WaterFillPlanner: water at {}, {} blocks: {} buckets (at least {})",
                body.start, body.cells.length, body.buckets.size(), plan.lowerBound() >= 0 ? plan.lowerBound() : "?");

        body.simulation = simulate(level, body);
        setStage(body, body.simulation != null ? Stage.SIMULATING : Stage.READY);
    }

    private void setStage(Body body, Stage stage) {
        body.stage = stage;
        body.expiresAt = tick + (stage == Stage.WAITING ? WAIT_TICKS : PLAN_TICKS);
        body.lastProgressTick = tick;
    }

    /**
     * The water blocks connected to {@code start}, stopping a bit over {@code maxCells}.
     */
    private LongOpenHashSet findBody(Level level, WorldSchematic schematic, BlockPos start, int maxCells) {
        LongOpenHashSet cells = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        cells.add(start.asLong());
        queue.enqueue(start.asLong());

        while (!queue.isEmpty() && cells.size() <= maxCells) {
            long cell = queue.dequeueLong();
            for (Direction direction : Direction.values()) {
                pos.set(cell).move(direction);
                long next = pos.asLong();
                if (!cells.contains(next) && !bodyByCell.containsKey(next) && level.isLoaded(pos)
                        && DataManager.getRenderLayerRange().isPositionWithinRange(pos)
                        && isWaterCell(schematic.getBlockState(pos))) {
                    cells.add(next);
                    queue.enqueue(next);
                }
            }
        }

        return cells;
    }

    /**
     * Counts what still has to be built before infinite water can fill the body: the waterloggable blocks themselves,
     * room for the water blocks, and what the schematic puts below and beside the water. Only what the printer can
     * build before the water comes counts, anything else would wait forever.
     */
    private static int countUnbuilt(Level level, WorldSchematic schematic, long[] cells, LongOpenHashSet cellSet) {
        int unbuilt = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos around = new BlockPos.MutableBlockPos();

        for (long cell : cells) {
            pos.set(cell);
            BlockState target = schematic.getBlockState(pos);
            BlockState current = level.getBlockState(pos);
            // grass, flowers and the like are washed away by the water, as by the bucket. Ice either turns into water or
            // stays as a wall, which the plan works out
            boolean built = target.getBlock() instanceof LiquidBlock
                    ? current.is(Blocks.WATER) || current.canBeReplaced(Fluids.WATER) || isIce(current)
                    : current.is(target.getBlock());
            if (!built) {
                unbuilt++;
            }

            for (Direction direction : AROUND) {
                around.setWithOffset(pos, direction);
                if (cellSet.contains(around.asLong()) || !level.isLoaded(around)
                        || !DataManager.getRenderLayerRange().isPositionWithinRange(around)) {
                    continue;
                }
                // only what the schematic builds there: fluids come by themselves, and seagrass, kelp and bubble columns
                // only go into water, which infinite water brings there once the body around them fills
                BlockState aroundTarget = schematic.getBlockState(around);
                if (aroundTarget.isAir() || !aroundTarget.getFluidState().isEmpty()
                        && !aroundTarget.hasProperty(BlockStateProperties.WATERLOGGED)) {
                    continue;
                }
                if (!level.getBlockState(around).is(aroundTarget.getBlock())) {
                    unbuilt++;
                }
            }
        }

        return unbuilt;
    }

    @SuppressWarnings("deprecation") // BlockStateBase#isSolid, which FlowingFluid#getNewLiquid itself checks
    private static InfiniteWaterSolver createSolver(Level level, long[] cells, LongOpenHashSet iceToWater) {
        int size = cells.length;
        Long2IntOpenHashMap index = new Long2IntOpenHashMap(size);
        index.defaultReturnValue(-1);
        for (int i = 0; i < size; i++) {
            index.put(cells[i], i);
        }

        int[] neighbors = new int[size * 4];
        int[] outsideSources = new int[size];
        int[] below = new int[size];
        boolean[] solidBelow = new boolean[size];
        boolean[] filled = new boolean[size];

        for (int i = 0; i < size; i++) {
            BlockPos pos = BlockPos.of(cells[i]);
            BlockState state = iceToWater.contains(cells[i]) ? WATER_SOURCE : level.getBlockState(pos);
            // ice that stays is a wall that holds the water above it up, there is nothing to fill
            filled[i] = state.getFluidState().isSourceOfType(Fluids.WATER) || isIce(state);

            int k = 0;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = pos.relative(direction);
                int j = index.get(neighborPos.asLong());
                BlockState neighborState = iceToWater.contains(neighborPos.asLong()) ? WATER_SOURCE : level.getBlockState(neighborPos);
                boolean connected = FlowingFluidInvoker.litematica_printer$canPassThroughWall(direction, level, pos, state, neighborPos, neighborState);
                neighbors[i * 4 + k++] = connected ? j : -1;
                if (connected && j < 0 && neighborState.getFluidState().isSourceOfType(Fluids.WATER)) {
                    outsideSources[i]++;
                }
            }

            BlockPos belowPos = pos.below();
            below[i] = index.get(belowPos.asLong());
            BlockState belowState = level.getBlockState(belowPos);
            solidBelow[i] = below[i] < 0 && (belowState.isSolid() || belowState.getFluidState().isSourceOfType(Fluids.WATER));
        }

        return new InfiniteWaterSolver(cells, neighbors, outsideSources, below, solidBelow, filled);
    }

    /**
     * Ice and frosted ice, which can turn into a water source. A bucket can't go into them.
     */
    private static boolean isIce(BlockState state) {
        return state.getBlock() instanceof IceBlock;
    }

    /**
     * Whether ice where the schematic has water turns into a water source: the printer breaks it, or light melts it.
     */
    private static boolean turnsIntoWater(Level level, BlockPos pos, BlockState state, IceBreaking iceBreaking) {
        return iceBreaking.leavesWater(state) && holdsMeltWater(level, pos) || meltsFromLight(level, pos, state);
    }

    // IceBlock#playerDestroy only leaves water above something solid or liquid; since 26.3, above a block
    // tagged ice_melts_when_destroyed_above or liquid
    @SuppressWarnings("deprecation") // BlockStateBase#blocksMotion and #liquid, which IceBlock#playerDestroy itself checks
    private static boolean holdsMeltWater(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (Mc.is("[26.3,)")) {
            return below.is(BlockTags.ICE_MELTS_WHEN_DESTROYED_ABOVE) || below.liquid();
        }
        return below.blocksMotion() || below.liquid();
    }

    // Mirrors IceBlock#randomTick and FrostedIceBlock#tick
    private static boolean meltsFromLight(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof FrostedIceBlock) {
            int light = level.dimension() == Level.END
                    ? level.getBrightness(LightLayer.BLOCK, pos)
                    : level.getMaxLocalRawBrightness(pos);
            return light > 11 - state.getValue(FrostedIceBlock.AGE) - state.getLightDampening();
        }
        return level.getBrightness(LightLayer.BLOCK, pos) > 11 - state.getLightDampening();
    }

    /**
     * Whether the printer breaks ice into water, worked out once per plan: Break Blocks is on, the ice isn't excluded
     * from breaking, and it isn't broken in creative or with Silk Touch, which leave no water.
     */
    private static final class IceBreaking {
        private final Map<Block, Boolean> leavesWater = new IdentityHashMap<>();
        private @Nullable BreakBlockListRegistry breakList;
        private @Nullable IgnoreBlockRegistry ignored;

        private boolean leavesWater(BlockState state) {
            return leavesWater.computeIfAbsent(state.getBlock(), block -> compute(state));
        }

        private boolean compute(BlockState state) {
            // with the HOLD breaker option only while the key is held, the plan is redone every PLAN_TICKS
            Minecraft mc = Minecraft.getInstance();
            if (!BreakBlockGuide.isBreakerAllowed() || mc.player == null || mc.player.getAbilities().instabuild) {
                return false;
            }

            // what BreakBlockGuide checks before breaking a block the schematic doesn't have
            if (breakList == null) {
                breakList = new BreakBlockListRegistry();
            }
            if (!breakList.isBreakable(state.getBlock())) {
                return false;
            }
            if (IGNORE_EXISTING_BLOCKS.getBooleanValue()) {
                if (ignored == null) {
                    ignored = new IgnoreBlockRegistry();
                }
                if (ignored.hasBlock(state.getBlock())) {
                    return false;
                }
            }

            return !EnchantmentHelper.hasTag(Breaker.getMiningTool(mc, state), EnchantmentTags.PREVENTS_ICE_MELTING);
        }
    }

    /**
     * Starts Fluid Predict on the planned buckets, or returns null if it isn't installed.
     */
    private static FluidPredictCompact.@Nullable Simulation simulate(Level level, Body body) {
        if (!FluidPredictCompact.isAvailable()) {
            return null;
        }

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (long cell : body.cells) {
            minX = Math.min(minX, BlockPos.getX(cell));
            maxX = Math.max(maxX, BlockPos.getX(cell));
            minZ = Math.min(minZ, BlockPos.getZ(cell));
            maxZ = Math.max(maxZ, BlockPos.getZ(cell));
        }
        BlockPos origin = new BlockPos((minX + maxX) / 2, BlockPos.getY(body.cells[0]), (minZ + maxZ) / 2);
        int radius = Math.max(maxX - minX, maxZ - minZ) / 2 + 8;

        FluidPredictCompact.Simulation simulation = FluidPredictCompact.create(level, origin, radius, Math.max(40_000, body.cells.length * 8));
        if (simulation == null) {
            return null;
        }

        // simulate as planned: ice that turns into water is already the source, other ice stays a wall
        for (long cell : body.iceToWater) {
            simulation.placeFluid(BlockPos.of(cell), Fluids.WATER);
        }
        // waterlogged blocks are built before the plan is made
        for (long bucket : body.buckets) {
            BlockPos pos = BlockPos.of(bucket);
            if (level.getBlockState(pos).hasProperty(BlockStateProperties.WATERLOGGED)) {
                simulation.waterlog(pos, Fluids.WATER);
            } else {
                simulation.placeFluid(pos, Fluids.WATER);
            }
        }
        return simulation;
    }

    private void advanceSimulation(Body body, long budgetNanos) {
        FluidPredictCompact.Simulation simulation = body.simulation;
        if (simulation == null || !simulation.run(budgetNanos)) {
            return;
        }

        body.simulation = null;
        setStage(body, Stage.READY);
        BlockGetter result = simulation.isSettled() ? simulation.world() : null;
        if (result == null) {
            Printer.printDebug("WaterFillPlanner: Fluid Predict could not finish, plan for {} unchecked", body.start);
            return;
        }

        // Whatever the real fluid rules leave empty gets a bucket too, but not the ice that stays
        int added = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (long cell : body.cells) {
            BlockState state = result.getBlockState(pos.set(cell));
            if (!state.getFluidState().isSourceOfType(Fluids.WATER) && !isIce(state) && body.buckets.add(cell)) {
                added++;
            }
        }
        Printer.printDebug("WaterFillPlanner: Fluid Predict checked plan for {}, {} buckets added", body.start, added);
    }

    private void updateProgress(Level level, Body body) {
        int filledCount = 0;
        boolean bucketsFilled = true;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // the ice the plan counts on is waited for like a bucket, other ice (water that froze too) can't take one
        for (long cell : body.cells) {
            BlockState state = level.getBlockState(pos.set(cell));
            if (state.getFluidState().isSourceOfType(Fluids.WATER)) {
                filledCount++;
            } else if (isIce(state) ? body.iceToWater.contains(cell) : body.buckets.contains(cell)) {
                bucketsFilled = false;
            }
        }

        if (filledCount > body.filledCount) {
            body.filledCount = filledCount;
            body.lastProgressTick = tick;
        }
        body.bucketsFilled = bucketsFilled;
    }
}
