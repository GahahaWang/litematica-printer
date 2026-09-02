package me.aleksilassila.litematica.printer.mixin;

import fi.dy.masa.litematica.util.InventoryUtils;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(InventoryUtils.class)
public interface InventoryUtilsAccessor {

    @Final
    @Accessor("PICK_BLOCKABLE_SLOTS")
    static List<Integer> getPickBlockableSlots() {
        throw new UnsupportedOperationException();
    }

    @Invoker("getEmptyPickBlockableHotbarSlot")
    static int getEmptyPickBlockableHotbarSlot(Inventory inventory) {
        throw new UnsupportedOperationException();
    }

    @Invoker("canPickToSlot")
    static boolean canPickToSlot(Inventory inventory, int slotNum) {
        throw new UnsupportedOperationException();
    }
}
