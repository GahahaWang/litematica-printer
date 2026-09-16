package me.aleksilassila.litematica.printer.utils;

import java.util.Optional;

import fi.dy.masa.litematica.config.Hotkeys;
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
public class Breaker{
    private final Minecraft mc;
    private BlockPos pos;
    private BlockState state;

    public Breaker(Minecraft mc) {
        this.mc = mc;
    }

    public void startBreakingBlock(BlockPos pos) {
        if (this.mc.player.getAbilities().instabuild || this.mc.level.getBlockState(pos).getDestroySpeed(this.mc.level, pos) == 0) {
            this.mc.gameMode.startDestroyBlock(pos, Direction.UP);
            return;
        }
        if (!LitematicaMixinMod.printer.actionHandler.isQueueEmpty()) {
            return;
        }

        this.pos = pos;
        this.state = this.mc.level.getBlockState(this.pos);
        // Find best tool in inventory
        int bestSlotId = getBestItemSlotIdToMineBlock();

        if (bestSlotId != -1) {
            PrinterInventoryUtils.survivalSwap(this.mc, this.mc.player, bestSlotId);
        }

        // Check if can break instantly with this tool
        if (this.state.getDestroyProgress(this.mc.player, this.mc.player.level(), this.pos) >= 1.0F) {
            this.mc.gameMode.startDestroyBlock(pos, Direction.UP);
        }
    }

    public boolean validateBreakingTarget() {
        boolean ret = true;
        if (this.pos == null) {
            ret = false;
        }

        if (ret) {
            if (!isBreakerAllowed()) {
                ret = false;
            }
        }

        if (ret) {
            double eyeDistanceSquared = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(this.pos));
            double maxReachSquared = Mth.square(Configs.PRINTING_RANGE.getDoubleValue());
            if (eyeDistanceSquared > maxReachSquared) {
                ret = false;
            }
        }

        if (ret) {
            BlockState newState = this.mc.level.getBlockState(this.pos);
            if (newState.isAir() || newState.canBeReplaced() || !newState.equals(this.state)) ret = false;
        }
        if (!ret) {
            cancelBreaking();
        }

        return ret;
    }

    public void cancelBreaking() {
        if (this.pos != null) {
            this.pos = null;
            this.state = null;
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

    public int getBestItemSlotIdToMineBlock() {
        int bestSlot = -1;
        float bestSpeed = 0;
        return getFastestToolSlot(this.mc, bestSlot, bestSpeed, this.state);
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
        if (stack.isEmpty()) {
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
        if (slotId < 0 || slotId >= 36) {
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

    public void onClientTick() {
        if (!validateBreakingTarget()) {
            return;
        }
        this.mc.gameMode.continueDestroyBlock(pos, Direction.UP);//this.mc.player.swing(InteractionHand.MAIN_HAND);
//        validateBreakingTarget(); // printer will validate it very soon
    }
}