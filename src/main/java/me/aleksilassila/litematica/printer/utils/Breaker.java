package me.aleksilassila.litematica.printer.utils;

import java.util.Optional;

import fi.dy.masa.litematica.config.Hotkeys;
import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import me.aleksilassila.litematica.printer.LitematicaMixinMod;
import me.aleksilassila.litematica.printer.config.BreakPreference;
import me.aleksilassila.litematica.printer.config.BreakerOption;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
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

        if (bestSlotId != -1) {
            PrinterInventoryUtils.survivalSwap(mc, mc.player, bestSlotId);
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

//        if (this.mc.gameMode.continueDestroyBlock(pos, Direction.UP)) {
//            //this.mc.player.swing(InteractionHand.MAIN_HAND);
//        }

        // Check if the block broke as a result of this tick's progress
        BlockState state = this.mc.level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) {
            cancelBreaking();
        }
    }
}