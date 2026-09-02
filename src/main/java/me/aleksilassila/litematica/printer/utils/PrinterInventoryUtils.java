package me.aleksilassila.litematica.printer.utils;

import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import me.aleksilassila.litematica.printer.mixin.InventoryUtilsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public class PrinterInventoryUtils {

    private static int[] slotRoundRobin;
    static {
        resetSlotRoundRobin();
    }

    public static void resetSlotRoundRobin() {
        slotRoundRobin = new int[Inventory.SELECTION_SIZE];
        for (int i = 0; i < slotRoundRobin.length; i++) {
            slotRoundRobin[i] = i;
        }
    }

    private static void moveIndexToEnd(int index) {
        // if the chosen is the last then no movement required
        if (index < 0 || index >= slotRoundRobin.length -1) {
            return;
        }
        int selected = slotRoundRobin[index];
        System.arraycopy(
                slotRoundRobin,
                index + 1,
                slotRoundRobin,
                index,
                slotRoundRobin.length - index - 1
        );
        slotRoundRobin[slotRoundRobin.length - 1] = selected;
    }

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

    // Creative things are already done in PrepareAction, only need to do survival things
    public static void setPickedItemToHand(int sourceSlot, ItemStack stack, Minecraft mc) {
        if (mc.player == null) return;
        Player player = mc.player;
        Inventory inventory = player.getInventory();

        int hotbarSlot = sourceSlot;
        if (Inventory.isHotbarSlot(sourceSlot)) {
            inventory.setSelectedSlot(sourceSlot);
        }
        else {
            if (InventoryUtilsAccessor.getPickBlockableSlots().isEmpty()) {
                InfoUtils.showGuiOrInGameMessage(Message.MessageType.WARNING, "litematica.message.warn.pickblock.no_valid_slots_configured");
                return;
            }
            if (sourceSlot == -1 || !Inventory.isHotbarSlot(sourceSlot)) {
                hotbarSlot = InventoryUtilsAccessor.getEmptyPickBlockableHotbarSlot(inventory);
            }

            if (hotbarSlot == -1) {
                hotbarSlot = getPickBlockTargetSlot(player);
            }

            if (hotbarSlot != -1) {
                inventory.setSelectedSlot(hotbarSlot);
                mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, sourceSlot, hotbarSlot, ContainerInput.SWAP, mc.player);
            }
            else {
                InfoUtils.showGuiOrInGameMessage(Message.MessageType.WARNING, "litematica.message.warn.pickblock.no_suitable_slot_found");
                return;
            }
        }
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(inventory.getSelectedSlot()));
    }

    private static int getPickBlockTargetSlot(Player player) {
        if (InventoryUtilsAccessor.getPickBlockableSlots().isEmpty() || player == null) {
            return -1;
        }
        int slotNum;
        for (int index = 0; index < slotRoundRobin.length; ++index) {
            slotNum = slotRoundRobin[index];
            if (InventoryUtilsAccessor.canPickToSlot(player.getInventory(), slotNum))
            {
                moveIndexToEnd(index);
                return slotNum;
            }
        }
        return -1;
    }
}
