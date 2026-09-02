package me.aleksilassila.litematica.printer.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;

public class InventoryUtils {
    public static void survivalSwap(Minecraft client, LocalPlayer player, int slot) {
        if(client == null || player == null) return;
        final Inventory inv = player.getInventory();
        if (inv.getSelectedSlot() == slot) return;
        if (Inventory.isHotbarSlot(slot)) {
            inv.setSelectedSlot(slot);
            client.getConnection().send(new ServerboundSetCarriedItemPacket(inv.getSelectedSlot()));
        }
        else {
            client.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, inv.getSuitableHotbarSlot(), ContainerInput.SWAP, player);
        }
    }
}
