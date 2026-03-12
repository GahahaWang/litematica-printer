package me.aleksilassila.litematica.printer.utils;

import fi.dy.masa.litematica.config.Hotkeys;
import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import fi.dy.masa.malilib.util.EquipmentUtils;
import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.*;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class Breaker implements IClientTickHandler {
    private final Minecraft mc;
    private boolean breakingBlock = false;
    private BlockPos pos;

    public Breaker(Minecraft mc) {
        this.mc = mc;
    }

    @SuppressWarnings("DataFlowIssue")
    public boolean startBreakingBlock(BlockPos pos) {
        this.breakingBlock = true;
        this.pos = pos;


        // Check if block can be broken instantly
        if (this.mc.level.getBlockState(pos).getDestroySpeed(this.mc.level, pos) == 0) {
            this.mc.gameMode.startDestroyBlock(pos, Direction.UP);
            return false;
        }

        // Find best tool in inventory
        int bestSlotId = getBestItemSlotIdToMineBlock(this.mc, pos);

        // Swap to best tool using InventoryUtils2 swap logic (same interaction style as printer2)
        if (bestSlotId != -1) {
            ItemStack stack = this.mc.player.getInventory().getItem(bestSlotId);
            InventoryUtils2.swapToItem(mc, stack);
        }

        // Check if can break instantly with this tool
        BlockState blockState = this.mc.level.getBlockState(pos);
        if (blockState.getDestroyProgress(this.mc.player, this.mc.player.level(), pos) >= 1.0F) {
            this.mc.gameMode.startDestroyBlock(pos, Direction.UP);
            return false;
        }

        return true;
    }

    public boolean isBreakingBlock() {
        if (this.pos == null || this.mc.level == null) {
            return false;
        }

        if (!Configs.PRINT_MODE.getBooleanValue() || !Hotkeys.EASY_PLACE_ACTIVATION.getKeybind().isPressed()) {
            cancelBreaking();
            return false;
        }

        // Check if player is out of range
        if (this.mc.player != null) {
            Vec3 playerPos = this.mc.player.position();
            Vec3 blockCenter = Vec3.atCenterOf(this.pos);
            double distance = playerPos.distanceTo(blockCenter);
            double maxReach = Configs.PRINTING_RANGE.getDoubleValue();

            if (distance > maxReach) {
                cancelBreaking();
                return false;
            }
        }

        BlockState state = this.mc.level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) {
            this.breakingBlock = false;
        }

        return this.breakingBlock;
    }

    /**
     * Cancel the current breaking operation.
     * Called when print mode is disabled.
     */
    public void cancelBreaking() {
        if (this.breakingBlock) {
            this.breakingBlock = false;
            this.pos = null;

            if (this.mc.gameMode != null) {
                this.mc.gameMode.stopDestroyBlock();
            }
        }
    }

    public static int getBestItemSlotIdToMineBlock(Minecraft mc, BlockPos blockToMine) {
        int bestSlot = -1;
        float bestSpeed = 0;
        BlockState state = mc.level.getBlockState(blockToMine);
        return getFastestToolSlot(mc, bestSlot, bestSpeed, state);
    }

    private static int getFastestToolSlot(Minecraft mc, int bestSlot, float bestSpeed, BlockState state) {
        if (mc.player == null) {
            return bestSlot;
        }

        Inventory inventory = mc.player.getInventory();

        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            float speed = getBlockBreakingSpeed(state, mc, i);
            if ((speed > bestSpeed && speed > 1.0F)
                    || (speed >= bestSpeed && !inventory.getItem(i).isDamageableItem())) {
                bestSlot = i;
                bestSpeed = speed;
            }
        }

        return bestSlot;
    }

    public static float getBlockBreakingSpeed(BlockState block, Minecraft mc, int slotId) {
        if (slotId < 0 || slotId >= 36 || mc.player == null) {
            return 0;
        }

        ItemStack stack = mc.player.getInventory().getItem(slotId);
        float f = stack.getDestroySpeed(block);

        if (f > 1.0F) {

            Optional<Holder.Reference<Enchantment>> optional = mc.level.registryAccess().get(Enchantments.EFFICIENCY);
            int i = (Integer) optional.map((enchantmentReference) -> EnchantmentHelper.getItemEnchantmentLevel(enchantmentReference, stack)).orElse(0);
            if (i > 0 && !stack.isEmpty()) {
                f += (float) (i * i + 1);
            }
        }

        return f;
    }

    @Override
    public void onClientTick(Minecraft minecraft) {
        if (!isBreakingBlock() || this.mc.player == null || this.mc.level == null || this.mc.gameMode == null) {
            this.breakingBlock = false;
            return;
        }

        // Only continue mining while the correct keys are pressed
        if (Hotkeys.EASY_PLACE_ACTIVATION.getKeybind().isPressed()) {
            Direction side = Direction.UP;
            if (this.mc.gameMode.continueDestroyBlock(pos, side)) {
                this.mc.player.swing(InteractionHand.MAIN_HAND);
            }
        }

        // Check if block is broken
        BlockState state = this.mc.level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) {
            this.breakingBlock = false;

            // Sync selected slot
            //int curSlot = this.mc.player.getInventory().getSelectedSlot();
            //this.mc.getConnection().send(new ServerboundSetCarriedItemPacket(curSlot));

            this.mc.gameMode.stopDestroyBlock();
        }
        InventoryUtils2.tick();
    }

    // copied from litematica-printer2
    public static class InventoryUtils2 {
        private static int ptr = -1;
        public static int lastCount = 0;
        public static int itemChangeCount = 0;
        public static Item handlingItem = null;
        public static Item previousItem = null;
        public static int trackedSelectedSlot = -1;
        private static String cachedPickBlockableSlots = "";
        private static final HashSet<Integer> pickBlockableSlots = new HashSet();
        public static HashMap<Integer, Item> usedSlots = new LinkedHashMap();
        public static HashMap<Integer, Integer> slotCounts = new LinkedHashMap();

        private static char tickCounter = 0;
        public static void tick() {
            tickCounter++;
            if(tickCounter >=5) {
                clearCache();
                tickCounter = 0;
            }
        }

        public static void clearCache() {
            if (!usedSlots.isEmpty()) {
                Printer.printDebug("Clearing cache");
            }
            itemChangeCount = 0;
            trackedSelectedSlot = -1;
            previousItem = null;
            handlingItem = null;
            usedSlots.clear();
            slotCounts.clear();
        }

        public static Inventory getInventory(LocalPlayer player) {
            return player.getInventory();
        }

        public static boolean isCreative(LocalPlayer player) {
            return player.getAbilities().instabuild;
        }

        public static boolean isToolLikeItem(Item item) {
            return item instanceof FlintAndSteelItem || item instanceof ShearsItem;
        }


        private static int getPtr() {
            parsePickblockableSlots();
            if (pickBlockableSlots.isEmpty()) {
                return -1;
            } else {
                ++ptr;
                ptr %= pickBlockableSlots.size();
                return ptr;
            }
        }

        private static void parsePickblockableSlots() {
            String pickBlockableSlot = fi.dy.masa.litematica.config.Configs.Generic.PICK_BLOCKABLE_SLOTS.getStringValue();
            if (!pickBlockableSlot.equals(cachedPickBlockableSlots)) {
                cachedPickBlockableSlots = pickBlockableSlot;
                pickBlockableSlots.clear();

                for (String s : pickBlockableSlot.split(",")) {
                    try {
                        int i = Integer.parseInt(s);
                        if (i > 0 && i < 10) {
                            pickBlockableSlots.add(i - 1);
                        }
                    } catch (NumberFormatException var6) {
                    }
                }
            }

        }

        public static int getAvailableSlot(Item item) {
            if (usedSlots.containsValue(item)) {
                for (Integer i : usedSlots.keySet()) {
                    if (usedSlots.get(i) == item) {
                        return i;
                    }
                }

                return -1;
            } else {
                parsePickblockableSlots();
                if (usedSlots.size() == pickBlockableSlots.size()) {
                    return getPtr();
                } else {
                    for (int i = 0; i < 9; ++i) {
                        if (!usedSlots.containsKey(i) && pickBlockableSlots.contains(i)) {
                            return i;
                        }
                    }

                    return -1;
                }
            }
        }

        private static int searchSlot(Item item) {
            for (Integer i : usedSlots.keySet()) {
                if (usedSlots.get(i) == item && (Integer) slotCounts.getOrDefault(i, 0) > 0) {
                    return i;
                }
            }

            return -1;
        }

        public static ItemStack getMainHandStack(LocalPlayer player) {
            return player.getMainHandItem();
        }

        public static boolean areItemsExact(ItemStack a, ItemStack b) {
            return exceptToolItems(a, b);
        }

        private static boolean exceptToolItems(ItemStack a, ItemStack b) {
            if (!isToolLikeItem(a.getItem()) && !isToolLikeItem(b.getItem())) {
                return ItemStack.isSameItem(a, b);
            } else {
                return a.getItem() == b.getItem();
            }
        }

        public static boolean requiresSwap(LocalPlayer player, ItemStack stack) {
            int selectedSlot = getInventory(player).getSelectedSlot();
            if (usedSlots.get(selectedSlot) != null) {
                return stack.getItem() != usedSlots.get(selectedSlot) || (Integer) slotCounts.getOrDefault(selectedSlot, 0) <= 0;
            } else {
                return previousItem != null && lastCount != 0 ? !areItemsExact(previousItem.getDefaultInstance(), stack) : !areItemsExact(getMainHandStack(player), stack);
            }
        }

        public static boolean canSwap(LocalPlayer player, ItemStack stack) {
            if (isCreative(player)) {
                return true;
            } else {
                int slotNum = getSlotWithStack(player, stack);
                return slotNum != -1;
            }
        }

        public static int getSlotWithItem(Inventory inv, ItemStack stack) {
            for (int i = 0; i < inv.getNonEquipmentItems().size(); ++i) {
                if (ItemStack.isSameItem(inv.getItem(i), stack)) {
                    return i;
                }
            }

            return -1;
        }

        public static synchronized boolean swapToItem(Minecraft client, ItemStack stack) {
            Printer.printDebug("Trying to swap item into " + String.valueOf(stack.getItem()));
            LocalPlayer player = client.player;
            //int maxChange = LitematicaMixinMod.PRINTER_MAX_ITEM_CHANGES.getIntegerValue();
            int maxChange = 1;
            if (player != null && client.gameMode != null) {
                if (stack.getItem() != handlingItem && maxChange != 0 && itemChangeCount > maxChange) {
                    Printer.printDebug("Exceeded item change count");
                    return false;
                } else if (!requiresSwap(player, stack)) {
                    String var5 = String.valueOf(stack.getItem());
                    Printer.printDebug("Didn't require swap for item " + var5 + " previous handling item : " + String.valueOf(previousItem));
                    lastCount = isCreative(player) ? 65536 : getMainHandStack(player).getCount();
                    if (usedSlots.containsValue(stack.getItem()) && searchSlot(stack.getItem()) != trackedSelectedSlot) {
                        Printer.printDebug("Hotbar has duplicate item references, which should not happen!");
                    }

                    trackedSelectedSlot = getInventory(player).getSelectedSlot();
                    usedSlots.put(getInventory(player).getSelectedSlot(), getMainHandStack(player).getItem());
                    slotCounts.put(getInventory(player).getSelectedSlot(), lastCount);
                    previousItem = stack.getItem();
                    return true;
                } else {
                    if (usedSlots.containsValue(stack.getItem())) {
                        int slot = searchSlot(stack.getItem());
                        if (slot != -1) {
                            getInventory(player).setSelectedSlot(slot);
                            trackedSelectedSlot = getInventory(player).getSelectedSlot();
                            usedSlots.put(trackedSelectedSlot, stack.getItem());
                            slotCounts.put(trackedSelectedSlot, stack.getCount());
                            lastCount = stack.getCount();
                            previousItem = stack.getItem();
                            handlingItem = previousItem;
                            int var10000 = getInventory(player).getSelectedSlot();
                            Printer.printDebug("Selected slot " + var10000 + " based on cache for " + String.valueOf(stack.getItem()));
                            client.getConnection().send(new ServerboundSetCarriedItemPacket(getInventory(player).getSelectedSlot()));
                            return !getInventory(player).getSelectedItem().isEmpty();
                        }

                        Printer.printDebug("Used slot contains stack but cannot find " + String.valueOf(stack.getItem()));
                    }

                    Printer.printDebug("Trying survival Swap");
                    if (survivalSwap(client, player, stack)) {
                        usedSlots.put(getInventory(player).getSelectedSlot(), stack.getItem());
                        slotCounts.put(trackedSelectedSlot, getMainHandStack(player).getCount());
                        Printer.printDebug("Swapped to item " + String.valueOf(stack.getItem()));
                        handlingItem = stack.getItem();
                        previousItem = handlingItem;
                        ++itemChangeCount;
                        return true;
                    } else {
                        Printer.printDebug("Survival swap failed, trying creative swap");
                        return creativeSwap(client, player, stack);
                    }
                }
            } else {
                Printer.printDebug("Player or interaction manager was null");
                return false;
            }
        }

        public static int getSlotWithStack(LocalPlayer player, ItemStack stack) {
            Inventory inv = getInventory(player);
            return !EquipmentUtils.isRegularTool(stack) && !isToolLikeItem(stack.getItem()) ? getSlotWIthStackIgnoreNbt(getInventory(player), stack) : getSlotWithItem(inv, stack);
        }


        public static int getSlotWIthStackIgnoreNbt(Inventory inv, ItemStack stack) {
            int defaultSlot = inv.findSlotMatchingItem(stack);
            if (defaultSlot != -1) {
                return defaultSlot;
            } else {
                for (int i = 0; i < inv.getNonEquipmentItems().size(); ++i) {
                    boolean areItemsEqual = ItemStack.isSameItem(inv.getItem(i), stack);
                    if (areItemsEqual) {
                        return i;
                    }
                }

                return -1;
            }
        }

        private static boolean creativeSwap(Minecraft client, LocalPlayer player, ItemStack stack) {
            if (!isCreative(player)) {
                Printer.printDebug("Player is not in creative mode");
                return false;
            } else {
                int selectedSlot = getAvailableSlot(stack.getItem());
                if (selectedSlot == -1) {
                    Printer.printDebug("No available slot for " + String.valueOf(stack.getItem()));
                    return false;
                } else {
                    String var10000 = String.valueOf(stack.getItem());
                    Printer.printDebug("Clicked creative stack " + var10000 + " for slot " + selectedSlot);
                    getInventory(player).setSelectedSlot(selectedSlot);
                    client.gameMode.handleCreativeModeItemAdd(stack, 36 + selectedSlot);
                    client.getConnection().send(new ServerboundSetCarriedItemPacket(getInventory(player).getSelectedSlot()));
                    trackedSelectedSlot = selectedSlot;
                    getInventory(player).getNonEquipmentItems().set(selectedSlot, stack);
                    usedSlots.put(getInventory(player).getSelectedSlot(), stack.getItem());
                    slotCounts.put(trackedSelectedSlot, 65536);
                    lastCount = 65536;
                    handlingItem = stack.getItem();
                    previousItem = handlingItem;
                    ++itemChangeCount;
                    return true;
                }
            }
        }

        private static boolean survivalSwap(Minecraft client, LocalPlayer player, ItemStack stack) {
            if (!canSwap(player, stack)) {
                return false;
            } else if (areItemsExact(player.getOffhandItem(), stack) && !areItemsExact(getMainHandStack(player), stack)) {
                lastCount = isCreative(player) ? 65536 : client.player.getOffhandItem().getCount();
                client.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                return true;
            } else {
                int slot = getSlotWithStack(player, stack);
                if (slot == -1) {
                    return false;
                } else {
                    if (Inventory.isHotbarSlot(slot)) {
                        if (usedSlots.get(slot) != null) {
                            Printer.printDebug("Hotbar slot should have been handled before, so it must be error!");
                            String var10000 = String.valueOf(usedSlots.get(slot));
                            Printer.printDebug("Expected : " + var10000 + " but current client handles : " + String.valueOf(stack.getItem()));
                            return false;
                        }

                        getInventory(player).setSelectedSlot(slot);
                        trackedSelectedSlot = slot;
                        Printer.printDebug("Selected hotbar Slot " + slot);
                        lastCount = isCreative(player) ? 65536 : getInventory(player).getItem(slot).getCount();
                        client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
                    } else {
                        int selectedSlot = getAvailableSlot(stack.getItem());
                        if (selectedSlot == -1) {
                            Printer.printDebug("All hotbar slots are used");
                            return false;
                        }

                        lastCount = isCreative(player) ? 65536 : getInventory(player).getItem(slot).getCount();
                        Printer.printDebug("Slot at " + slot + "(" + String.valueOf(getInventory(player).getItem(slot).getItem()) + ") is swapped with " + selectedSlot + "(" + String.valueOf(getInventory(player).getNonEquipmentItems().get(selectedSlot)) + ")");
                        usedSlots.put(selectedSlot, stack.getItem());
                        client.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, slot, selectedSlot, ClickType.SWAP, player);
                        getInventory(player).setSelectedSlot(selectedSlot);
                        trackedSelectedSlot = selectedSlot;
                    }

                    try {
                        assert ItemStack.matches(getMainHandStack(player), stack);
                    } catch (Exception var5) {
                        String var10001 = stack.toString();
                        Printer.printDebug(var10001 + " does not match with " + player.getMainHandItem().toString() + "!");
                    }

                    return true;
                }
            }
        }
    }
}