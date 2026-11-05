package me.aleksilassila.litematica.printer.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FlowingFluid.class)
public interface FlowingFluidInvoker {

    /**
     * Whether fluid connects from {@code pos} to the neighbor in {@code direction}, i.e. their shared face is not closed.
     */
    @Invoker("canPassThroughWall")
    static boolean litematica_printer$canPassThroughWall(Direction direction, BlockGetter level, BlockPos pos, BlockState state,
                                                         BlockPos neighborPos, BlockState neighborState) {
        throw new UnsupportedOperationException();
    }
}
