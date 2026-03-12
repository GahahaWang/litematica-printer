package me.aleksilassila.litematica.printer;

import com.google.common.collect.ImmutableMap;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.util.RayTraceUtils;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.config.Hotkeys;
import me.aleksilassila.litematica.printer.guides.Guide;
import me.aleksilassila.litematica.printer.guides.Guides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class Printer {
    public static final Logger logger = LogManager.getLogger(PrinterReference.MOD_ID);
    @Nonnull
    public final LocalPlayer player;
    public final ActionHandler actionHandler;
    private final Guides interactionGuides = new Guides();
    private static final LinkedHashMap<Long, PositionCache> positionCache = new LinkedHashMap<>();

    public Printer(@Nonnull Minecraft client, @Nonnull LocalPlayer player) {
        this.player = player;
        this.actionHandler = new ActionHandler(client, player);
    }

    public boolean onGameTick() {
        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();

        if (!actionHandler.acceptsActions()) {
            return false;
        }

        if (worldSchematic == null) {
            return false;
        }

        if (!Configs.PRINT_MODE.getBooleanValue() && !Hotkeys.PRINT.getKeybind().isPressed()) {
            return false;
        }

        if (LitematicaMixinMod.breaker.isBreakingBlock()) {
            printDebug("Breaker is currently breaking a block, skipping other operations");
            return false;
        }

        Abilities abilities = player.getAbilities();
        if (!abilities.mayBuild) {
            return false;
        }

        List<BlockPos> positions = getReachablePositions();
        findBlock:
        for (BlockPos position : positions) {
            if (isPositionCached(position)) {
                continue;
            }
            SchematicBlockState state = new SchematicBlockState(player.level(), worldSchematic, position);
            BlockState cs = state.currentState;
            BlockState ts = state.targetState;
            if (ts.equals(cs) || (cs.is(Blocks.MOVING_PISTON))) {
                continue;
            }
            if(!cs.isAir() && !cs.is(ts.getBlock()) && !Configs.BREAK_BLOCKS.getBooleanValue()) {
                continue ;
            }

            Guide[] guides = interactionGuides.getInteractionGuides(state);

            BlockHitResult result = RayTraceUtils.traceToSchematicWorld(player, 10, true, true);
            boolean isCurrentlyLookingSchematic = result != null && result.getBlockPos().equals(position);

            for (Guide guide : guides) {
                // Add INTERACT_BLOCKS pull by DarkReaper231
                if (guide.canExecute(player) && Configs.INTERACT_BLOCKS.getBooleanValue()) {
                    printDebug("Executing {} for {}", guide, state);
                    List<Action> actions = guide.execute(player);
                    actionHandler.addActions(actions.toArray(Action[]::new));
                    cachePosition(position);
                    return true;
                }
                if (guide.skipOtherGuides()) {
                    continue findBlock;
                }
            }
        }

        return false;
    }

    private List<BlockPos> getReachablePositions() {
        int maxReach = (int) Math.ceil(Configs.PRINTING_RANGE.getDoubleValue());
        double maxReachSquared = Mth.square(Configs.PRINTING_RANGE.getDoubleValue());

        ArrayList<BlockPos> positions = new ArrayList<>();

        for (int y = -maxReach; y < maxReach + 1; y++) {
            for (int x = -maxReach; x < maxReach + 1; x++) {
                for (int z = -maxReach; z < maxReach + 1; z++) {
                    BlockPos blockPos = player.blockPosition().north(x).west(z).above(y);

                    if (!DataManager.getRenderLayerRange().isPositionWithinRange(blockPos)) {
                        continue;
                    }
                    if (this.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(blockPos)) > maxReachSquared) {
                        continue;
                    }

                    positions.add(blockPos);
                }
            }
        }

        return positions.stream()
                .filter(p ->
                {
                    Vec3 vec = Vec3.atCenterOf(p);
                    return this.player.position().distanceToSqr(vec) > 1
                            && this.player.getEyePosition().distanceToSqr(vec) > 1
                            && isPositionInSchematicBounds(p);
                })
                .sorted((a, b) ->
                {
                    double aDistance = this.player.position().distanceToSqr(Vec3.atCenterOf(a));
                    double bDistance = this.player.position().distanceToSqr(Vec3.atCenterOf(b));
                    return Double.compare(aDistance, bDistance);
                }).toList();
    }

    private boolean isPositionInSchematicBounds(BlockPos pos) {
        List<SchematicPlacement> placements = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();
        
        if (placements.isEmpty()) {
            return false;
        }
        
        for (SchematicPlacement placement : placements) {
            if (!placement.isEnabled()) {
                continue;
            }
            
            ImmutableMap<String, Box> boxes = placement.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.PLACEMENT_ENABLED);
            
            for (Box box : boxes.values()) {
                if (isPositionWithinBox(box, pos)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    

    private boolean isPositionWithinBox(Box box, BlockPos pos) {
        if (box == null) {
            return false;
        }
        
        BlockPos pos1 = box.getPos1();
        BlockPos pos2 = box.getPos2();
        
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());
        
        return pos.getX() >= minX && pos.getX() <= maxX &&
               pos.getY() >= minY && pos.getY() <= maxY &&
               pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public static void printDebug(String key, Object... args) {
        if (Configs.PRINT_DEBUG.getBooleanValue()) {
            logger.info(key, args);
        }
    }

    public static boolean isPositionCached(BlockPos pos) {
        long currentTime = System.nanoTime();
        boolean cached = false;
        
        for (Long key : List.copyOf(positionCache.keySet())) {
            PositionCache val = positionCache.get(key);
            boolean expired = val.hasExpired(currentTime);

            if (expired) {
                positionCache.remove(key);
            } else if (val.getPos().equals(pos)) {
                cached = true;
                // Keep checking and removing old entries if there are a fair amount
                if (positionCache.size() < 16) {
                    break;
                }
            }
        }
        return cached;
    }

    public static void cachePosition(BlockPos pos) {
        cachePosition(pos, Configs.PRINTER_CACHE_MS.getIntegerValue());
    }

    public static void cachePosition(BlockPos pos, int milliseconds) {
        PositionCache item = new PositionCache(pos, System.nanoTime(), milliseconds * 1000000L);
        positionCache.put(pos.asLong(), item);
    }

    public static class PositionCache {
        private final BlockPos pos;
        private final long timeout;

        private PositionCache(BlockPos pos, long time, long timeout) {
            this.pos = pos;
            this.timeout = time + timeout;
        }

        public BlockPos getPos() {
            return pos;
        }

        public boolean hasExpired(long currentTime) {
            return currentTime > this.timeout;
        }
    }
}
