package me.aleksilassila.litematica.printer.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Optional;

import fi.dy.masa.litematica.config.Hotkeys;
import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import fi.dy.masa.malilib.util.EquipmentUtils;
import me.aleksilassila.litematica.printer.LitematicaMixinMod;
import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.config.BreakPreference;
import me.aleksilassila.litematica.printer.config.BreakerOption;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

@SuppressWarnings("DataFlowIssue")
public class Breaker implements IClientTickHandler {
    private final Minecraft mc;
    private BlockPos pos;

    public Breaker(Minecraft mc) {
        this.mc = mc;
    }

    public boolean startBreakingBlock(BlockPos pos) {
        if (!LitematicaMixinMod.printer.actionHandler.isQueueEmpty()) {
            //Printer.printDebug("Breaker: refusing to start breaking {}, ActionHandler still has pending actions", pos);
            return false;
        }

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
//            ItemStack stack = this.mc.player.getInventory().getItem(bestSlotId);
//            InventoryUtils2.swapToItem(mc, stack);
            InventoryUtils3.survivalSwap(mc, mc.player, bestSlotId);
        }

        // Check if can break instantly with this tool
        BlockState blockState = this.mc.level.getBlockState(pos);
        if (blockState.getDestroyProgress(this.mc.player, this.mc.player.level(), pos) >= 1.0F) {
            this.mc.gameMode.startDestroyBlock(pos, Direction.UP);
            return false;
        }

        return true;
    }

    /**
     * Whether there is currently a valid, in-range, still-unbroken target to keep
     * breaking. Pure query - does not cancel or otherwise mutate breaking state;
     * callers are responsible for calling {@link #cancelBreaking()} when this
     * returns false.
     */
    public boolean isBreakingBlock() {
        if (this.pos == null || this.mc.level == null || this.mc.player == null) {
            return false;
        }

        if (!isBreakerAllowed()) {
            return false;
        }

        double eyeDistanceSquared = this.mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(this.pos));
        double maxReachSquared = Mth.square(Configs.PRINTING_RANGE.getDoubleValue());
        if (eyeDistanceSquared > maxReachSquared) {
            cancelBreaking();
            return false;
        }

        BlockState state = this.mc.level.getBlockState(pos);
        return !state.isAir() && !state.canBeReplaced();
    }

    /**
     * Cancel the current breaking operation.
     * Called when print mode is disabled, the target is out of range, already
     * broken, or the tick handler otherwise stops breaking it.
     */
    public void cancelBreaking() {
        if (this.mc.gameMode != null && pos != null) {
            this.pos = null;
            //this.mc.gameMode.stopDestroyBlock();
        }
    }

    public static boolean isBreakerAllowed() {
        if (!Configs.PRINT_MODE.getBooleanValue()) {
            return false;
        }
        BreakerOption option = (BreakerOption) Configs.BREAKER_OPTION.getOptionListValue();
        return option == BreakerOption.AUTO || Hotkeys.EASY_PLACE_ACTIVATION.getKeybind().isPressed();
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
        BreakPreference preference = (BreakPreference) Configs.BREAK_PREFERENCE.getOptionListValue();
        boolean preferenceEnabled = preference != BreakPreference.DEFAULT;
        boolean onlyPreferred = preferenceEnabled && hasPreferredTool(mc, state, preference);

        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            float speed = getBlockBreakingSpeed(state, mc, i);

            if (onlyPreferred && !matchesPreference(stack, mc, preference)) {
                continue;
            }

            if (isDurabilityTooLow(stack)) {
                continue;
            }

            if (speed > bestSpeed && speed > 1.0F) {
                bestSlot = i;
                bestSpeed = speed;
            }
        }

        if (bestSlot != -1) {
            return bestSlot;
        }

        // No tool gives an actual speed bonus - prefer a non-durability item over
        // wearing down a real tool or switching to an empty hand.
        int nonDurabilitySlot = findNonDurabilitySlot(inventory, 0, Inventory.getSelectionSize());
        if (nonDurabilitySlot != -1) {
            return nonDurabilitySlot;
        }

        nonDurabilitySlot = findNonDurabilitySlot(inventory, Inventory.getSelectionSize(), Inventory.INVENTORY_SIZE);
        if (nonDurabilitySlot != -1) {
            return nonDurabilitySlot;
        }

        // Nothing suitable found - keep mining with whatever is currently held.
        return -1;
    }

    private static int findNonDurabilitySlot(Inventory inventory, int fromInclusive, int toExclusive) {
        for (int i = fromInclusive; i < toExclusive; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isDamageableItem()) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isDurabilityTooLow(ItemStack stack) {
        if (!Configs.TOOL_DURABILITY_PROTECTION.getBooleanValue() || !stack.isDamageableItem()) {
            return false;
        }

        int remainingDurability = stack.getMaxDamage() - stack.getDamageValue();
        return remainingDurability <= Configs.TOOL_DURABILITY_THRESHOLD.getIntegerValue();
    }


    private static boolean hasPreferredTool(Minecraft mc, BlockState state, BreakPreference preference) {
        if (preference == BreakPreference.DEFAULT || mc.player == null || mc.level == null) {
            return false;
        }

        Inventory inventory = mc.player.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (getBlockBreakingSpeed(state, mc, i) > 1.0F
                    && matchesPreference(inventory.getItem(i), mc, preference)) {
                return true;
            }
        }

        return false;
    }

    private static boolean matchesPreference(ItemStack stack, Minecraft mc, BreakPreference preference) {
        if (preference == BreakPreference.DEFAULT) {
            return true;
        }
        if (stack.isEmpty() || mc.level == null) {
            return false;
        }

        return switch (preference) {
            case FORTUNE -> getEnchantmentLevel(mc, stack, Enchantments.FORTUNE) > 0;
            case SILK_TOUCH -> getEnchantmentLevel(mc, stack, Enchantments.SILK_TOUCH) > 0;
            default -> true;
        };
    }

    private static int getEnchantmentLevel(Minecraft mc, ItemStack stack, ResourceKey<Enchantment> enchantmentKey) {
        Optional<Holder.Reference<Enchantment>> optional = mc.level.registryAccess().get(enchantmentKey);
        return optional.map(enchantmentReference -> EnchantmentHelper.getItemEnchantmentLevel(enchantmentReference, stack))
                .orElse(0);
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
        if (this.mc.player == null || this.mc.level == null || this.mc.gameMode == null) {
            cancelBreaking();
            return;
        }
        if (!isBreakingBlock()) {
            cancelBreaking();
            return;
        }

        if (this.mc.gameMode.continueDestroyBlock(pos, Direction.UP)) {
            //this.mc.player.swing(InteractionHand.MAIN_HAND);
        }

        // Check if the block broke as a result of this tick's progress
        BlockState state = this.mc.level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) {
            cancelBreaking();
        }
    }

    public static class InventoryUtils3 {
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
//
//    // copied from litematica-printer2
//    public static class InventoryUtils2 {
//        private static int ptr = -1;
//        public static int lastCount = 0;
//        public static int itemChangeCount = 0;
//        public static Item handlingItem = null;
//        public static Item previousItem = null;
//        public static int trackedSelectedSlot = -1;
//        private static String cachedPickBlockableSlots = "";
//        private static final HashSet<Integer> pickBlockableSlots = new HashSet();
//        public static HashMap<Integer, Item> usedSlots = new LinkedHashMap();
//        public static HashMap<Integer, Integer> slotCounts = new LinkedHashMap();
//
//        private static char tickCounter = 0;
//        public static void tick() {
//            tickCounter++;
//            if(tickCounter >=5) {
//                clearCache();
//                tickCounter = 0;
//            }
//        }
//
//        public static void clearCache() {
//            if (!usedSlots.isEmpty()) {
//                Printer.printDebug("Clearing cache");
//            }
//            itemChangeCount = 0;
//            trackedSelectedSlot = -1;
//            previousItem = null;
//            handlingItem = null;
//            usedSlots.clear();
//            slotCounts.clear();
//        }
//
//        public static Inventory getInventory(LocalPlayer player) {
//            return player.getInventory();
//        }
//
//        public static boolean isCreative(LocalPlayer player) {
//            return player.getAbilities().instabuild;
//        }
//
//        public static boolean isToolLikeItem(Item item) {
//            return item instanceof FlintAndSteelItem || item instanceof ShearsItem;
//        }
//
//
//        private static int getPtr() {
//            parsePickblockableSlots();
//            if (pickBlockableSlots.isEmpty()) {
//                return -1;
//            } else {
//                ++ptr;
//                ptr %= pickBlockableSlots.size();
//                return ptr;
//            }
//        }
//
//        private static void parsePickblockableSlots() {
//            String pickBlockableSlot = fi.dy.masa.litematica.config.Configs.Generic.PICK_BLOCKABLE_SLOTS.getStringValue();
//            if (!pickBlockableSlot.equals(cachedPickBlockableSlots)) {
//                cachedPickBlockableSlots = pickBlockableSlot;
//                pickBlockableSlots.clear();
//
//                for (String s : pickBlockableSlot.split(",")) {
//                    try {
//                        int i = Integer.parseInt(s);
//                        if (i > 0 && i < 10) {
//                            pickBlockableSlots.add(i - 1);
//                        }
//                    } catch (NumberFormatException var6) {
//                    }
//                }
//            }
//
//        }
//
//        public static int getAvailableSlot(Item item) {
//            if (usedSlots.containsValue(item)) {
//                for (Integer i : usedSlots.keySet()) {
//                    if (usedSlots.get(i) == item) {
//                        return i;
//                    }
//                }
//
//                return -1;
//            } else {
//                parsePickblockableSlots();
//                if (usedSlots.size() == pickBlockableSlots.size()) {
//                    return getPtr();
//                } else {
//                    for (int i = 0; i < 9; ++i) {
//                        if (!usedSlots.containsKey(i) && pickBlockableSlots.contains(i)) {
//                            return i;
//                        }
//                    }
//
//                    return -1;
//                }
//            }
//        }
//
//        private static int searchSlot(Item item) {
//            for (Integer i : usedSlots.keySet()) {
//                if (usedSlots.get(i) == item && (Integer) slotCounts.getOrDefault(i, 0) > 0) {
//                    return i;
//                }
//            }
//
//            return -1;
//        }
//
//        public static ItemStack getMainHandStack(LocalPlayer player) {
//            return player.getMainHandItem();
//        }
//
//        public static boolean areItemsExact(ItemStack a, ItemStack b) {
//            return exceptToolItems(a, b);
//        }
//
//        private static boolean exceptToolItems(ItemStack a, ItemStack b) {
//            if (!isToolLikeItem(a.getItem()) && !isToolLikeItem(b.getItem())) {
//                return ItemStack.isSameItem(a, b);
//            } else {
//                return a.getItem() == b.getItem();
//            }
//        }
//
//        public static boolean requiresSwap(Minecraft mc, LocalPlayer player, ItemStack stack) {
//            int selectedSlot = getInventory(player).getSelectedSlot();
//            if (usedSlots.get(selectedSlot) != null) {
//                return stack.getItem() != usedSlots.get(selectedSlot) || (Integer) slotCounts.getOrDefault(selectedSlot, 0) <= 0;
//            } else {
//                ItemStack current = previousItem != null && lastCount != 0 ? previousItem.getDefaultInstance() : getMainHandStack(player);
//                if (!areItemsExact(current, stack)) {
//                    return true;
//                }
//                if (isDurabilityTooLow(getMainHandStack(player))) {
//                    return true;
//                }
//                return !matchesSamePreference(mc, current, stack);
//            }
//        }
//
//        private static boolean matchesSamePreference(Minecraft mc, ItemStack current, ItemStack target) {
//            BreakPreference preference = (BreakPreference) Configs.BREAK_PREFERENCE.getOptionListValue();
//            if (preference == BreakPreference.DEFAULT) {
//                return true;
//            }
//            return matchesPreference(current, mc, preference) == matchesPreference(target, mc, preference);
//        }
//
//        public static boolean canSwap(LocalPlayer player, ItemStack stack) {
//            if (isCreative(player)) {
//                return true;
//            } else {
//                int slotNum = getSlotWithStack(player, stack);
//                return slotNum != -1;
//            }
//        }
//
//        public static int getSlotWithItem(Inventory inv, ItemStack stack) {
//            for (int i = 0; i < inv.getNonEquipmentItems().size(); ++i) {
//                if (ItemStack.isSameItem(inv.getItem(i), stack)) {
//                    return i;
//                }
//            }
//
//            return -1;
//        }
//
//        public static synchronized boolean swapToItem(Minecraft client, ItemStack stack) {
//            Printer.printDebug("Trying to swap item into " + String.valueOf(stack.getItem()));
//            LocalPlayer player = client.player;
//            //int maxChange = LitematicaMixinMod.PRINTER_MAX_ITEM_CHANGES.getIntegerValue();
//            int maxChange = 1;
//            if (player != null && client.gameMode != null) {
//                if (stack.getItem() != handlingItem && maxChange != 0 && itemChangeCount > maxChange) {
//                    Printer.printDebug("Exceeded item change count");
//                    return false;
//                } else if (!requiresSwap(client, player, stack)) {
//                    String var5 = String.valueOf(stack.getItem());
//                    Printer.printDebug("Didn't require swap for item " + var5 + " previous handling item : " + String.valueOf(previousItem));
//                    lastCount = isCreative(player) ? 65536 : getMainHandStack(player).getCount();
//                    if (usedSlots.containsValue(stack.getItem()) && searchSlot(stack.getItem()) != trackedSelectedSlot) {
//                        Printer.printDebug("Hotbar has duplicate item references, which should not happen!");
//                    }
//
//                    trackedSelectedSlot = getInventory(player).getSelectedSlot();
//                    usedSlots.put(getInventory(player).getSelectedSlot(), getMainHandStack(player).getItem());
//                    slotCounts.put(getInventory(player).getSelectedSlot(), lastCount);
//                    previousItem = stack.getItem();
//                    return true;
//                } else {
//                    if (usedSlots.containsValue(stack.getItem())) {
//                        int slot = searchSlot(stack.getItem());
//                        if (slot != -1) {
//                            getInventory(player).setSelectedSlot(slot);
//                            trackedSelectedSlot = getInventory(player).getSelectedSlot();
//                            usedSlots.put(trackedSelectedSlot, stack.getItem());
//                            slotCounts.put(trackedSelectedSlot, stack.getCount());
//                            lastCount = stack.getCount();
//                            previousItem = stack.getItem();
//                            handlingItem = previousItem;
//                            int var10000 = getInventory(player).getSelectedSlot();
//                            Printer.printDebug("Selected slot " + var10000 + " based on cache for " + String.valueOf(stack.getItem()));
//                            client.getConnection().send(new ServerboundSetCarriedItemPacket(getInventory(player).getSelectedSlot()));
//                            return !getInventory(player).getSelectedItem().isEmpty();
//                        }
//
//                        Printer.printDebug("Used slot contains stack but cannot find " + String.valueOf(stack.getItem()));
//                    }
//
//                    Printer.printDebug("Trying survival Swap");
//                    if (survivalSwap(client, player, stack)) {
//                        usedSlots.put(getInventory(player).getSelectedSlot(), stack.getItem());
//                        slotCounts.put(trackedSelectedSlot, getMainHandStack(player).getCount());
//                        Printer.printDebug("Swapped to item " + String.valueOf(stack.getItem()));
//                        handlingItem = stack.getItem();
//                        previousItem = handlingItem;
//                        ++itemChangeCount;
//                        return true;
//                    } else {
//                        Printer.printDebug("Survival swap failed, trying creative swap");
//                        return creativeSwap(client, player, stack);
//                    }
//                }
//            } else {
//                Printer.printDebug("Player or interaction manager was null");
//                return false;
//            }
//        }
//
//        public static int getSlotWithStack(LocalPlayer player, ItemStack stack) {
//            Inventory inv = getInventory(player);
//            return !EquipmentUtils.isRegularTool(stack) && !isToolLikeItem(stack.getItem()) ? getSlotWIthStackIgnoreNbt(getInventory(player), stack) : getSlotWithItem(inv, stack);
//        }
//
//
//        public static int getSlotWIthStackIgnoreNbt(Inventory inv, ItemStack stack) {
//            int defaultSlot = inv.findSlotMatchingItem(stack);
//            if (defaultSlot != -1) {
//                return defaultSlot;
//            } else {
//                for (int i = 0; i < inv.getNonEquipmentItems().size(); ++i) {
//                    boolean areItemsEqual = ItemStack.isSameItem(inv.getItem(i), stack);
//                    if (areItemsEqual) {
//                        return i;
//                    }
//                }
//
//                return -1;
//            }
//        }
//
//        private static boolean creativeSwap(Minecraft client, LocalPlayer player, ItemStack stack) {
//            if (!isCreative(player)) {
//                Printer.printDebug("Player is not in creative mode");
//                return false;
//            } else {
//                int selectedSlot = getAvailableSlot(stack.getItem());
//                if (selectedSlot == -1) {
//                    Printer.printDebug("No available slot for " + String.valueOf(stack.getItem()));
//                    return false;
//                } else {
//                    String var10000 = String.valueOf(stack.getItem());
//                    Printer.printDebug("Clicked creative stack " + var10000 + " for slot " + selectedSlot);
//                    getInventory(player).setSelectedSlot(selectedSlot);
//                    client.gameMode.handleCreativeModeItemAdd(stack, 36 + selectedSlot);
//                    client.getConnection().send(new ServerboundSetCarriedItemPacket(getInventory(player).getSelectedSlot()));
//                    trackedSelectedSlot = selectedSlot;
//                    getInventory(player).getNonEquipmentItems().set(selectedSlot, stack);
//                    usedSlots.put(getInventory(player).getSelectedSlot(), stack.getItem());
//                    slotCounts.put(trackedSelectedSlot, 65536);
//                    lastCount = 65536;
//                    handlingItem = stack.getItem();
//                    previousItem = handlingItem;
//                    ++itemChangeCount;
//                    return true;
//                }
//            }
//        }
//
//        private static boolean survivalSwap(Minecraft client, LocalPlayer player, ItemStack stack) {
//            if (!canSwap(player, stack)) {
//                return false;
//            } else {
//                int slot = getSlotWithStack(player, stack);
//                if (slot == -1) {
//                    return false;
//                } else {
//                    if (Inventory.isHotbarSlot(slot)) {
//                        if (usedSlots.get(slot) != null) {
//                            Printer.printDebug("Hotbar slot should have been handled before, so it must be error!");
//                            String var10000 = String.valueOf(usedSlots.get(slot));
//                            Printer.printDebug("Expected : " + var10000 + " but current client handles : " + String.valueOf(stack.getItem()));
//                            return false;
//                        }
//
//                        getInventory(player).setSelectedSlot(slot);
//                        trackedSelectedSlot = slot;
//                        Printer.printDebug("Selected hotbar Slot " + slot);
//                        lastCount = isCreative(player) ? 65536 : getInventory(player).getItem(slot).getCount();
//                        client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
//                    } else {
//                        int selectedSlot = getAvailableSlot(stack.getItem());
//                        if (selectedSlot == -1) {
//                            Printer.printDebug("All hotbar slots are used");
//                            return false;
//                        }
//
//                        lastCount = isCreative(player) ? 65536 : getInventory(player).getItem(slot).getCount();
//                        Printer.printDebug("Slot at " + slot + "(" + String.valueOf(getInventory(player).getItem(slot).getItem()) + ") is swapped with " + selectedSlot + "(" + String.valueOf(getInventory(player).getNonEquipmentItems().get(selectedSlot)) + ")");
//                        usedSlots.put(selectedSlot, stack.getItem());
//                        client.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, selectedSlot, ContainerInput.SWAP, player);
//                        getInventory(player).setSelectedSlot(selectedSlot);
//                        trackedSelectedSlot = selectedSlot;
//                    }
//
//                    try {
//                        assert ItemStack.matches(getMainHandStack(player), stack);
//                    } catch (Exception var5) {
//                        String var10001 = stack.toString();
//                        Printer.printDebug(var10001 + " does not match with " + player.getMainHandItem().toString() + "!");
//                    }
//
//                    return true;
//                }
//            }
//        }
//    }
}