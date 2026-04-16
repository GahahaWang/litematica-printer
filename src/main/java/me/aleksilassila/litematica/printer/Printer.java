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
import me.aleksilassila.litematica.printer.utils.PositionCache;
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
import java.util.List;

public class Printer {
    public static final Logger logger = LogManager.getLogger(PrinterReference.MOD_ID);
    @Nonnull
    public final LocalPlayer player;
    public final ActionHandler actionHandler;
    private final Guides interactionGuides = new Guides();
    private final PositionCache positionCache = new PositionCache();

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

        positionCache.pruneExpiredCacheEntries();
        List<BlockPos> positions = getReachablePositions();
        findBlock:
        for (BlockPos position : positions) {
            if (positionCache.isPositionCached(position)) {
                continue;
            }
            SchematicBlockState state = new SchematicBlockState(player.level(), worldSchematic, position);
            BlockState cs = state.currentState;
            BlockState ts = state.targetState;
            if (ts.equals(cs) || cs.is(Blocks.MOVING_PISTON)) {
                continue;
            }
            if (!cs.isAir() && !cs.is(ts.getBlock()) && !Configs.BREAK_BLOCKS.getBooleanValue()) {
                continue;
            }

            Guide[] guides = interactionGuides.getInteractionGuides(state);

            for (Guide guide : guides) {
                // Add INTERACT_BLOCKS pull by DarkReaper231
                if (guide.canExecute(player) && Configs.INTERACT_BLOCKS.getBooleanValue()) {
                    printDebug("Executing {} for {}", guide, state);
                    List<Action> actions = guide.execute(player);
                    actionHandler.addActions(actions.toArray(Action[]::new));
                    positionCache.cachePosition(position);
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
        List<BoxBounds> activeBounds = getActiveSchematicBounds();

        if (activeBounds.isEmpty()) {
            return List.of();
        }

        BlockPos playerBlockPos = this.player.blockPosition();
        int baseX = playerBlockPos.getX();
        int baseY = playerBlockPos.getY();
        int baseZ = playerBlockPos.getZ();
        Vec3 playerPos = this.player.position();
        Vec3 eyePos = this.player.getEyePosition();

        int diameter = maxReach * 2 + 1;
        ArrayList<BlockPos> positions = new ArrayList<>(diameter * diameter * diameter);

        for (int y = -maxReach; y <= maxReach; y++) {
            int blockY = baseY + y;
            for (int x = -maxReach; x <= maxReach; x++) {
                int blockZ = baseZ - x;

                for (int z = -maxReach; z <= maxReach; z++) {
                    int blockX = baseX - z;
                    BlockPos blockPos = new BlockPos(blockX, blockY, blockZ);

                    if (!DataManager.getRenderLayerRange().isPositionWithinRange(blockPos)) {
                        continue;
                    }

                    double centerX = blockX + 0.5D;
                    double centerY = blockY + 0.5D;
                    double centerZ = blockZ + 0.5D;

                    double eyeDistanceSquared = eyePos.distanceToSqr(centerX, centerY, centerZ);
                    if (eyeDistanceSquared > maxReachSquared || eyeDistanceSquared <= 1D) {
                        continue;
                    }

                    if (playerPos.distanceToSqr(centerX, centerY, centerZ) <= 1D) {
                        continue;
                    }

                    if (!isPositionInSchematicBounds(blockPos, activeBounds)) {
                        continue;
                    }

                    positions.add(blockPos);
                }
            }
        }

        positions.sort((a, b) -> {
            double aDistance = playerPos.distanceToSqr(a.getX() + 0.5D, a.getY() + 0.5D, a.getZ() + 0.5D);
            double bDistance = playerPos.distanceToSqr(b.getX() + 0.5D, b.getY() + 0.5D, b.getZ() + 0.5D);
            return Double.compare(aDistance, bDistance);
        });

        return positions;
    }

    private List<BoxBounds> getActiveSchematicBounds() {
        List<SchematicPlacement> placements = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();
        if (placements.isEmpty()) {
            return List.of();
        }

        ArrayList<BoxBounds> bounds = new ArrayList<>();

        for (SchematicPlacement placement : placements) {
            if (!placement.isEnabled()) {
                continue;
            }

            ImmutableMap<String, Box> boxes = placement.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.PLACEMENT_ENABLED);
            for (Box box : boxes.values()) {
                BlockPos pos1 = box.getPos1();
                BlockPos pos2 = box.getPos2();
                if (pos1 == null || pos2 == null) {
                    continue;
                }
                bounds.add(new BoxBounds(
                        Math.min(pos1.getX(), pos2.getX()),
                        Math.max(pos1.getX(), pos2.getX()),
                        Math.min(pos1.getY(), pos2.getY()),
                        Math.max(pos1.getY(), pos2.getY()),
                        Math.min(pos1.getZ(), pos2.getZ()),
                        Math.max(pos1.getZ(), pos2.getZ())
                ));
            }
        }
        return bounds;
    }

    private boolean isPositionInSchematicBounds(BlockPos pos, List<BoxBounds> bounds) {
        for (BoxBounds boxBounds : bounds) {
            if (boxBounds.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    public static void printDebug(String key, Object... args) {
        if (Configs.PRINT_DEBUG.getBooleanValue()) {
            logger.info(key, args);
        }
    }

    private static final class BoxBounds {
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;

        private BoxBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        private boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }
}
