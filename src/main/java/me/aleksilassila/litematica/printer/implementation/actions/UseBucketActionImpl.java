package me.aleksilassila.litematica.printer.implementation.actions;

import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.actions.InteractAction;
import me.aleksilassila.litematica.printer.implementation.PrinterPlacementContext;
import me.aleksilassila.litematica.printer.utils.BucketHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Empties the bucket at {@code context.hitResult}, aiming again from where the player is now:
 * the bucket is raycast from the eye, so moving since the guide ran could pour the fluid elsewhere.
 */
public class UseBucketActionImpl extends InteractAction {
    private final Fluid fluid;
    private final BlockPos target;

    public UseBucketActionImpl(PrinterPlacementContext context, Fluid fluid, BlockPos target) {
        super(context);
        this.fluid = fluid;
        this.target = target;
    }

    @Override
    public void send(Minecraft client, LocalPlayer player) {
        // never use whatever else ended up in the hand
        if (!(player.getItemInHand(context.interactionHand).getItem() instanceof BucketItem bucket) || bucket.getContent() != fluid) {
            return;
        }

        float[] rotation = BucketHelper.getRotation(player.getEyePosition(), context.hitResult.getLocation());
        if (BucketHelper.getHitFilling(player, fluid, rotation[0], rotation[1], target) == null) {
            Printer.printDebug("UseBucketAction.send: {} can no longer be filled from here", target);
            return;
        }

        float xRot = player.getXRot();
        float yRot = player.getYRot();
        player.setYRot(rotation[0]);
        player.setXRot(rotation[1]);
        interact(client, player, context.interactionHand, context.hitResult);
        player.setXRot(xRot);
        player.setYRot(yRot);

        Printer.printDebug("UseBucketAction.send: Target: {} Against: {} Side: {}", target, context.hitResult.getBlockPos(), context.hitResult.getDirection());
    }

    @Override
    protected void interact(Minecraft client, LocalPlayer player, InteractionHand hand, BlockHitResult hitResult) {
        if (client.gameMode != null) {
            // the use packet carries the rotation, the server raycasts the bucket with it
            client.gameMode.useItem(player, hand);
        }
    }
}
