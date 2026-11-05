package me.aleksilassila.litematica.printer.guides.placement;

import java.util.ArrayList;
import java.util.List;
import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Places a fluid source block by emptying a bucket against a neighboring block.
 */
public class FluidPlacementGuide extends BucketGuide {
    // Points to aim at on the face of the neighbor, the component along the face normal is dropped
    private static final Vec3[] AIM_OFFSETS = new Vec3[]{
            Vec3.ZERO,
            new Vec3(-0.25, -0.25, -0.25),
            new Vec3(+0.25, +0.25, -0.25),
            new Vec3(-0.25, +0.25, +0.25),
            new Vec3(+0.25, -0.25, +0.25),
    };

    public FluidPlacementGuide(SchematicBlockState state) {
        super(state, state.targetState.getFluidState().getType());
    }

    @Override
    public boolean canExecute(LocalPlayer player) {
        // flowing fluid only comes from a source, a bucket can't place it
        if (!targetState.getFluidState().isSource() || statesEqual(targetState, currentState)) {
            return false;
        }
        if (!currentState.canBeReplaced(fluid)) {
            return false;
        }
        if (!Configs.REPLACE_FLUIDS_SOURCE_BLOCKS.getBooleanValue() && currentState.getFluidState().isSource()) {
            return false;
        }

        return super.canExecute(player);
    }

    @Override
    protected List<Vec3> getAimPoints(Vec3 eye) {
        List<Vec3> aimPoints = new ArrayList<>();

        for (Direction side : Direction.values()) {
            BlockPos neighborPos = state.blockPos.relative(side);
            if (state.world.getBlockState(neighborPos).getShape(state.world, neighborPos).isEmpty()) {
                continue;
            }

            Vec3 normal = Vec3.atLowerCornerOf(side.getUnitVec3i());
            Vec3 faceCenter = Vec3.atCenterOf(state.blockPos).add(normal.scale(0.5));
            // only a face looking back at the player can be hit
            if (eye.subtract(faceCenter).dot(normal) >= 0) {
                continue;
            }

            Vec3 multiplier = new Vec3(normal.x == 0 ? 1 : 0, normal.y == 0 ? 1 : 0, normal.z == 0 ? 1 : 0);
            for (Vec3 offset : AIM_OFFSETS) {
                aimPoints.add(faceCenter.add(offset.multiply(multiplier)));
            }
        }

        return aimPoints;
    }

    /**
     * Other placement guides would use the bucket on a block like a block item, which does nothing.
     */
    @Override
    public boolean skipOtherGuides() {
        return true;
    }
}
