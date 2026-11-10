package me.aleksilassila.litematica.printer.guides;

import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.utils.Breaker;
import me.aleksilassila.litematica.printer.utils.PrinterInventoryUtils;
import me.aleksilassila.litematica.printer.implementation.BlockHelperImpl;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CoralPlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

abstract public class Guide extends BlockHelperImpl {
    protected final SchematicBlockState state;
    protected final BlockState currentState;
    protected final BlockState targetState;

    public Guide(SchematicBlockState state) {
        this.state = state;
        this.currentState = state.currentState;
        this.targetState = state.targetState;
    }

    protected boolean playerHasRightItem(LocalPlayer player) {
        return getRequiredItemStackSlot(player) != -1;
    }

    /**
     * Finds a slot holding {@code itemStack}, preferring the main hand, then the offhand
     * (when usable), so no swap is needed if either hand already has it.
     */
    protected int getSlotWithItem(LocalPlayer player, ItemStack itemStack) {
        Inventory inventory = player.getInventory();

        int selected = inventory.getSelectedSlot();
        if (isMatchingItem(inventory.getNonEquipmentItems().get(selected), itemStack)) {
            return selected;
        }

        if (!itemStack.isEmpty() && PrinterInventoryUtils.canUseOffhand(player)
                && isMatchingItem(player.getOffhandItem(), itemStack)) {
            return Inventory.SLOT_OFFHAND;
        }

        for (int i = 0; i < inventory.getNonEquipmentItems().size(); ++i) {
            if (isMatchingItem(inventory.getNonEquipmentItems().get(i), itemStack)) {
                return i;
            }
        }

        return -1;
    }

    private static boolean isMatchingItem(ItemStack stack, ItemStack required) {
        if (required.isEmpty()) {
            return stack.is(required.getItem());
        }
        // a tool too worn to use counts as missing
        return !stack.isEmpty() && ItemStack.isSameItem(stack, required) && !Breaker.isDurabilityTooLow(stack);
    }

    protected int getRequiredItemStackSlot(LocalPlayer player) {
        if (player.getAbilities().instabuild) {
            return player.getInventory().getSelectedSlot();
        }

        Optional<ItemStack> requiredItem = getRequiredItem(player);
        return requiredItem.map(itemStack -> getSlotWithItem(player, itemStack)).orElse(-1);
    }

    public boolean canExecute(LocalPlayer player) {
        if (!playerHasRightItem(player)) {
            return false;
        }

        BlockState targetState = state.targetState;
        BlockState currentState = state.currentState;

        return !statesEqual(targetState, currentState);
    }

    abstract public @Nonnull List<Action> execute(LocalPlayer player);

    abstract protected @Nonnull List<ItemStack> getRequiredItems();

    /**
     * Returns the first required item that the player has access to,
     * or empty if the items are inaccessible.
     */
    protected Optional<ItemStack> getRequiredItem(LocalPlayer player) {
        List<ItemStack> requiredItems = getRequiredItems();

        for (ItemStack requiredItem : requiredItems) {
            if (player.getAbilities().instabuild) {
                return Optional.of(requiredItem);
            }

            int slot = getSlotWithItem(player, requiredItem);
            if (slot > -1) {
                return Optional.of(requiredItem);
            }
        }

        return Optional.empty();
    }

    protected boolean statesEqualIgnoreProperties(BlockState state1, BlockState state2,
                                                  Property<?>... propertiesToIgnore) {
        if (state1.getBlock() != state2.getBlock()) {
            return false;
        }

        loop:
        for (Property<?> property : state1.getProperties()) {
            if (property == BlockStateProperties.WATERLOGGED && !(state1.getBlock() instanceof CoralPlantBlock)) {
                continue;
            }

            for (Property<?> ignoredProperty : propertiesToIgnore) {
                if (property == ignoredProperty) {
                    continue loop;
                }
            }

            try {
                if (!state1.getValue(property).equals(state2.getValue(property))) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }

        return true;
    }

    protected static <T extends Comparable<T>> Optional<T> getProperty(BlockState blockState, Property<T> property) {
        if (blockState.hasProperty(property)) {
            return Optional.of(blockState.getValue(property));
        }
        return Optional.empty();
    }

    /**
     * Returns true if
     */
    protected boolean statesEqual(BlockState state1, BlockState state2) {
        return statesEqualIgnoreProperties(state1, state2);
    }

    public boolean skipOtherGuides() {
        return false;
    }

    public boolean isBreakGuide() {
        return false;
    }
}
