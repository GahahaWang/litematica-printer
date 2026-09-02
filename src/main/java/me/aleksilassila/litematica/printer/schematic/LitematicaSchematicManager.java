package me.aleksilassila.litematica.printer.schematic;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Builds real, in-memory-only {@link LitematicaSchematic}s from
 * /litematicaprinter setblock|fill (never touching disk) and registers them as
 * ordinary {@link SchematicPlacement}s in Litematica's own
 * {@link SchematicPlacementManager}. Because it's a normal placement, the
 * existing printer pipeline, Litematica's ghost-block rendering, and the
 * verifier all treat it exactly like a loaded .litematic file - no special
 * casing needed anywhere else.
 * <p>
 * {@link LitematicaBlockStateContainer} is fixed-size once created, so each
 * setblock/fill call creates its own small schematic+placement covering just
 * that operation's box, rather than trying to grow one shared schematic.
 */
public class LitematicaSchematicManager {
    public enum FillMode {
        REPLACE,
        DESTROY,
        HOLLOW,
        OUTLINE
    }

    private static final String REGION_NAME = "main";
    private static final AtomicInteger counter = new AtomicInteger();

    public static void setBlock(BlockPos pos, BlockState state) {
        SchematicPlacement placement = createPlacement(pos, pos);
        LitematicaBlockStateContainer container = requireContainer(placement);
        container.set(0, 0, 0, state);
        DataManager.getSchematicPlacementManager().markChunksForRebuild(placement);
    }

    /**
     * @param filter tested against the real world's current state at each position;
     *               only meaningful (and only ever non-null) for REPLACE
     */
    public static int fill(BlockPos from, BlockPos to, BlockState state, FillMode mode, Predicate<BlockPos> filter) {
        BlockPos min = min(from, to);
        BlockPos max = max(from, to);
        SchematicPlacement placement = createPlacement(min, max);
        LitematicaBlockStateContainer container = requireContainer(placement);

        // structure_void marks a cell as "not part of the target" - no item can ever
        // place it, so the printer pipeline naturally leaves those positions alone.
        BlockState untouched = Blocks.STRUCTURE_VOID.defaultBlockState();

        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;
        int count = 0;

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    boolean isShell = x == 0 || x == sizeX - 1 || y == 0 || y == sizeY - 1 || z == 0 || z == sizeZ - 1;

                    if (mode == FillMode.OUTLINE && !isShell) {
                        container.set(x, y, z, untouched);
                        continue;
                    }

                    if (mode == FillMode.HOLLOW && !isShell) {
                        container.set(x, y, z, Blocks.AIR.defaultBlockState());
                        count++;
                        continue;
                    }

                    if (mode == FillMode.REPLACE && filter != null && !filter.test(min.offset(x, y, z))) {
                        container.set(x, y, z, untouched);
                        continue;
                    }

                    container.set(x, y, z, state);
                    count++;
                }
            }
        }

        DataManager.getSchematicPlacementManager().markChunksForRebuild(placement);
        return count;
    }

    /**
     * @param min inclusive minimum corner - also becomes the sub-region's local (0,0,0) origin
     * @param max inclusive maximum corner
     */
    private static SchematicPlacement createPlacement(BlockPos min, BlockPos max) {
        Box box = new Box(min, max, REGION_NAME);

        AreaSelection area = new AreaSelection();
        area.setExplicitOrigin(min);
        area.addSubRegionBox(box, true);

        String name = "printer-schematic-" + counter.incrementAndGet();
        LitematicaSchematic schematic = LitematicaSchematic.createEmptySchematic(area, name);

        SchematicPlacement placement = SchematicPlacement.createFor(schematic, min, name, true, true);
        DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, false);

        return placement;
    }

    /** The region is always freshly created by {@link #createPlacement}, so a miss here means something is broken. */
    private static LitematicaBlockStateContainer requireContainer(SchematicPlacement placement) {
        LitematicaBlockStateContainer container = placement.getSchematic().getSubRegionContainer(REGION_NAME);
        if (container == null) {
            throw new IllegalStateException("Missing sub-region container '" + REGION_NAME + "' on a schematic we just created");
        }
        return container;
    }

    private static BlockPos min(BlockPos a, BlockPos b) {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    private static BlockPos max(BlockPos a, BlockPos b) {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }
}
