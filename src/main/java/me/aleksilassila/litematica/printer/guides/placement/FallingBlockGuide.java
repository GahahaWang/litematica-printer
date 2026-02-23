package me.aleksilassila.litematica.printer.guides.placement;

import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

public class FallingBlockGuide extends GuesserGuide {

    public FallingBlockGuide(SchematicBlockState state) {
        super(state);
    }

    boolean blockPlacement() {
        if (targetState.getBlock() instanceof FallingBlock) {
            BlockState below = state.world.getBlockState(state.blockPos.relative(Direction.DOWN));
            return FallingBlock.isFree(below);
        }

        return false;
    }

    @Override
    public boolean canExecute(LocalPlayer player) {
        if (blockPlacement() && !Configs.FALLING_BLOCK_PRINT_IN_AIR.getBooleanValue())
            return false;

        return super.canExecute(player);
    }

    @Override
    public boolean skipOtherGuides() {
        if (blockPlacement() && !Configs.FALLING_BLOCK_PRINT_IN_AIR.getBooleanValue())
            return true;

        return super.skipOtherGuides();
    }
}
