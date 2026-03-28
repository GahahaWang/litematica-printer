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
        BlockState below = state.world.getBlockState(state.blockPos.relative(Direction.DOWN));
        return !FallingBlock.isFree(below) || !getRequiresSupport();
    }

    @Override
    public boolean getRequiresSupport() {
        return !Configs.FALLING_BLOCK_PRINT_IN_AIR.getBooleanValue();
    }

    @Override
    public boolean canExecute(LocalPlayer player) {
        return blockPlacement() && super.canExecute(player);
    }

    @Override
    public boolean skipOtherGuides() {
        return !blockPlacement() || super.skipOtherGuides();
    }
}
