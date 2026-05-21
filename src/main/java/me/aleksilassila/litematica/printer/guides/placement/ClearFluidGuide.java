package me.aleksilassila.litematica.printer.guides.placement;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.implementation.PrinterPlacementContext;
import me.aleksilassila.litematica.printer.utils.BlocksClearFluidRegistry;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

public class ClearFluidGuide extends GeneralPlacementGuide {

    private static final long BLOCKS_CLEAR_FLUID_REGISTRY_REFRESH_INTERVAL_MS = 1000L;
    private BlocksClearFluidRegistry blocksClearFluidRegistry = new BlocksClearFluidRegistry();
    private long blocksClearFluidRegistryLastRefreshMs = System.currentTimeMillis();

    public ClearFluidGuide(SchematicBlockState state) {
        super(state);
    }

    private BlocksClearFluidRegistry getBlocksClearFluidRegistry() {
        long now = System.currentTimeMillis();
        if (now - blocksClearFluidRegistryLastRefreshMs >= BLOCKS_CLEAR_FLUID_REGISTRY_REFRESH_INTERVAL_MS) {
            blocksClearFluidRegistry = new BlocksClearFluidRegistry();
            blocksClearFluidRegistryLastRefreshMs = now;
        }
        return blocksClearFluidRegistry;
    }

    @Override
    protected @Nonnull List<ItemStack> getRequiredItems() {
        return getBlocksClearFluidRegistry().getItemStacks();
    }

    @Override
    public boolean canExecute(LocalPlayer player) {
        if (!Configs.CLEAR_FLUID.getBooleanValue()) {
            return false;
        }
        if (!(currentState.getBlock() instanceof LiquidBlock)) {
            return false;
        }
        // only clear source
        if (!currentState.getFluidState().isSource()) {
            return false;
        }
        // skip if target is not waterlogged-able
        if(!targetState.isAir() && !targetState.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return false;
        }

        PrinterPlacementContext ctx = getPlacementContext(player);
        if (ctx == null || !ctx.canPlace()) {
            return false;
        }
        Optional<Block> placementBlock = getRequiredItemAsBlock(player);
        if (placementBlock.isEmpty()) {
            return false;
        }

        BlockState resultState = placementBlock.get().getStateForPlacement(ctx);
        return resultState != null && resultState.canSurvive(state.world, state.blockPos);
    }

    public static boolean wouldReformSource(SchematicBlockState state) {
        int count = 0;
        for(Direction direction : Direction.Plane.HORIZONTAL) {
            if (state.offset(direction).currentState.getFluidState().isSourceOfType(Fluids.WATER)) {
                count++;
            }
        }
        return count >= 2;
    }
}
