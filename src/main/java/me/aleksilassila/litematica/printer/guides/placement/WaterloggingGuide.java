package me.aleksilassila.litematica.printer.guides.placement;

import java.util.List;
import me.aleksilassila.litematica.printer.SchematicBlockState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * Waterlogs a block that should be waterlogged by emptying a water bucket on the block itself.
 */
public class WaterloggingGuide extends BucketGuide {
    public WaterloggingGuide(SchematicBlockState state) {
        super(state, Fluids.WATER);
    }

    @Override
    public boolean canExecute(LocalPlayer player) {
        if (!getProperty(targetState, BlockStateProperties.WATERLOGGED).orElse(false)
                || getProperty(currentState, BlockStateProperties.WATERLOGGED).orElse(true)) {
            return false;
        }
        // only once the block itself is right
        if (!statesEqualIgnoreProperties(targetState, currentState, BlockStateProperties.WATERLOGGED)) {
            return false;
        }

        return super.canExecute(player);
    }

    @Override
    protected List<Vec3> getAimPoints(Vec3 eye) {
        Vec3 corner = Vec3.atLowerCornerOf(state.blockPos);
        return currentState.getShape(state.world, state.blockPos).toAabbs().stream()
                .map(box -> corner.add(box.getCenter()))
                .toList();
    }
}
