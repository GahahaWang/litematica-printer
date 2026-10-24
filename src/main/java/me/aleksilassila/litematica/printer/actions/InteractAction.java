package me.aleksilassila.litematica.printer.actions;

import me.aleksilassila.litematica.printer.LitematicaMixinMod;
import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.implementation.PrinterPlacementContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

abstract public class InteractAction extends Action {
    public final PrinterPlacementContext context;

    public InteractAction(PrinterPlacementContext context) {
        this.context = context;
    }

    protected abstract void interact(Minecraft client, LocalPlayer player, InteractionHand hand, BlockHitResult hitResult);

    @Override
    public void send(Minecraft client, LocalPlayer player) {
        float xRot = player.getXRot();
        float yRot = player.getYRot();
        PrepareAction lookAction = LitematicaMixinMod.printer.actionHandler.lookAction;
        if (lookAction != null) {
            if (lookAction.modifyPitch) {
                player.setXRot(lookAction.pitch);
            }
            if (lookAction.modifyYaw) {
                player.setYRot(lookAction.yaw);
            }
        }
        interact(client, player, context.interactionHand, context.hitResult);
        player.setXRot(xRot);
        player.setYRot(yRot);

        Printer.printDebug("InteractAction.send: Blockpos: {} Side: {} HitPos: {}", context.getClickedPos(), context.getClickedFace(), context.getClickLocation());
    }

    @Override
    public String toString() {
        return "InteractAction{" +
                "context=" + context +
                '}';
    }
}
