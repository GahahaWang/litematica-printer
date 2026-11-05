package me.aleksilassila.litematica.printer.guides.placement;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import fi.dy.masa.litematica.util.ItemUtils;
import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.implementation.PrinterPlacementContext;
import me.aleksilassila.litematica.printer.utils.FallingBlockSupportRegistry;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Places a support block under a falling block when nothing would hold it up,
 * i.e. the position below is air or replaceable in both the world and the schematic.
 */
public class FallingBlockSupportGuide extends GeneralPlacementGuide {
    private final SchematicBlockState fallingState;
    private List<ItemStack> supportItemsCache = null;

    public FallingBlockSupportGuide(SchematicBlockState state) {
        super(state.offset(Direction.DOWN));
        this.fallingState = state;
    }

    @Override
    protected @Nonnull List<ItemStack> getRequiredItems() {
        if (supportItemsCache == null) {
            supportItemsCache = new FallingBlockSupportRegistry().getItemStacks();
        }
        return supportItemsCache;
    }

    @Override
    public boolean canExecute(LocalPlayer player) {
        if (!Configs.FALLING_BLOCK_SUPPORT.getBooleanValue()) {
            return false;
        }
        if (!isSupportPosition(state) || !currentState.canBeReplaced()) {
            return false;
        }
        // only support a falling block that can be placed right after
        if (!fallingState.currentState.canBeReplaced() || !playerHasFallingBlock(player)) {
            return false;
        }
        double range = Configs.PRINTING_RANGE.getDoubleValue();
        if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(state.blockPos)) > Mth.square(range)) {
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
        return resultState != null && resultState.canSurvive(state.world, state.blockPos)
                && state.world.isUnobstructed(resultState, state.blockPos, CollisionContext.empty());
    }

    private boolean playerHasFallingBlock(LocalPlayer player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        ItemStack stack = ItemUtils.getItemForBlock(fallingState.world, fallingState.blockPos, fallingState.targetState, true);
        return !stack.isEmpty() && getSlotWithItem(player, stack) != -1;
    }

    /**
     * Whether the schematic leaves {@code state} empty under a falling block,
     * so whatever is in the world there is the only thing holding the falling block up.
     */
    public static boolean isSupportPosition(SchematicBlockState state) {
        return state.targetState.canBeReplaced()
                && state.schematic.getBlockState(state.blockPos.above()).getBlock() instanceof FallingBlock;
    }
}
