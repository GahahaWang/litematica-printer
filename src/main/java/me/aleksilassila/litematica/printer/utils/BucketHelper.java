package me.aleksilassila.litematica.printer.utils;

import me.aleksilassila.litematica.printer.multiversion.Multiversion;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A bucket is not used on a clicked block: the server raycasts it from the player's eye with the player's rotation,
 * so placing a fluid means looking at the right block.
 */
public class BucketHelper {

    /**
     * Returns {yaw, pitch} looking from {@code from} straight at {@code to}.
     */
    public static float[] getRotation(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        // the server wraps the yaw of the use packet the same way
        return new float[]{Mth.wrapDegrees(yaw), pitch};
    }

    /**
     * Returns the block a filled bucket of {@code fluid} is emptied against when used with this rotation,
     * worked out like {@code BucketItem#use}, or null if the fluid would not end up in {@code target}.
     */
    public static @Nullable BlockHitResult getHitFilling(LocalPlayer player, Fluid fluid, float yaw, float pitch, BlockPos target) {
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(Multiversion.viewVector(player, pitch, yaw).scale(player.blockInteractionRange()));
        BlockHitResult hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState clicked = player.level().getBlockState(pos);
        // water goes into a clicked waterloggable block that takes it, unless sneaking; otherwise in front of it
        boolean intoClicked = fluid == Fluids.WATER && !player.isShiftKeyDown()
                && clicked.getBlock() instanceof LiquidBlockContainer container
                && (clicked.canBeReplaced(fluid) || container.canPlaceLiquid(player, player.level(), pos, clicked, fluid));
        BlockPos placePos = intoClicked ? pos : pos.relative(hit.getDirection());
        return placePos.equals(target) ? hit : null;
    }
}
