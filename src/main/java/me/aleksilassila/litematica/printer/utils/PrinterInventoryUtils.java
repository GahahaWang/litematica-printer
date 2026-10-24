package me.aleksilassila.litematica.printer.utils;

import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.mixin.InventoryUtilsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class PrinterInventoryUtils {

    private static int[] slotRoundRobin;
    static {
        resetSlotRoundRobin();
    }

    public static void resetSlotRoundRobin() {
        slotRoundRobin = new int[Inventory.SELECTION_SIZE + 1];
        for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
            slotRoundRobin[i] = i;
        }
        slotRoundRobin[Inventory.SELECTION_SIZE] = Inventory.SLOT_OFFHAND;
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

    /**
     * Whether the offhand may hold picked items: enabled in config, survival only,
     * and never while the offhand holds a totem.
     */
    public static boolean canUseOffhand(Player player) {
        return Configs.USE_OFFHAND.getBooleanValue()
                && !player.getAbilities().instabuild
                && !player.getOffhandItem().has(DataComponents.DEATH_PROTECTION);
    }

    /**
     * Swaps the item in inventory {@code slot} into the offhand. No-op if it is already there.
     */
    private static void swapToOffhand(Minecraft mc, Player player, int slot) {
        if (slot == Inventory.SLOT_OFFHAND) {
            return;
        }
        // Inventory hotbar 0-8 maps to InventoryMenu slots 36-44; main inventory 9-35 maps 1:1.
        int menuSlot = Inventory.isHotbarSlot(slot) ? InventoryMenu.USE_ROW_SLOT_START + slot : slot;
        mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot, Inventory.SLOT_OFFHAND, ContainerInput.SWAP, player);
    }

    /**
     * Moves the item in {@code sourceSlot} into a hand. Creative things are already done in PrepareAction,
     * only survival is handled here.
     *
     * @return the hand now holding the item, or {@code null} if no suitable slot was found
     */
    public static @Nullable InteractionHand setPickedItemToHand(int sourceSlot, ItemStack stack, Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return null;
        Player player = mc.player;
        Inventory inventory = player.getInventory();
        boolean offhandAllowed = canUseOffhand(player);

        if (sourceSlot == inventory.getSelectedSlot()) {
            return InteractionHand.MAIN_HAND;
        }
        if (sourceSlot == Inventory.SLOT_OFFHAND) {
            return offhandAllowed ? InteractionHand.OFF_HAND : null;
        }
        if (Inventory.isHotbarSlot(sourceSlot)) {
            inventory.setSelectedSlot(sourceSlot);
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(sourceSlot));
            return InteractionHand.MAIN_HAND;
        }

        boolean hotbarAllowed = !InventoryUtilsAccessor.getPickBlockableSlots().isEmpty();
        if (!hotbarAllowed && !offhandAllowed) {
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.WARNING, "litematica.message.warn.pickblock.no_valid_slots_configured");
            return null;
        }

        int targetSlot = hotbarAllowed ? InventoryUtilsAccessor.getEmptyPickBlockableHotbarSlot(inventory) : -1;
        if (targetSlot == -1 && offhandAllowed && player.getOffhandItem().isEmpty()) {
            targetSlot = Inventory.SLOT_OFFHAND;
        }
        if (targetSlot == -1) {
            targetSlot = getPickBlockTargetSlot(player, hotbarAllowed, offhandAllowed);
        }
        if (targetSlot == -1) {
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.WARNING, "litematica.message.warn.pickblock.no_suitable_slot_found");
            return null;
        }

        if (targetSlot == Inventory.SLOT_OFFHAND) {
            swapToOffhand(mc, player, sourceSlot);
            return InteractionHand.OFF_HAND;
        }
        inventory.setSelectedSlot(targetSlot);
        mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, sourceSlot, targetSlot, ContainerInput.SWAP, player);
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(targetSlot));
        return InteractionHand.MAIN_HAND;
    }

    private static int getPickBlockTargetSlot(Player player, boolean hotbarAllowed, boolean offhandAllowed) {
        for (int index = 0; index < slotRoundRobin.length; ++index) {
            int slotNum = slotRoundRobin[index];
            boolean canPick = slotNum == Inventory.SLOT_OFFHAND
                    ? offhandAllowed
                    : hotbarAllowed && InventoryUtilsAccessor.canPickToSlot(player.getInventory(), slotNum);
            if (canPick) {
                moveIndexToEnd(index);
                return slotNum;
            }
        }
        return -1;
    }
}
