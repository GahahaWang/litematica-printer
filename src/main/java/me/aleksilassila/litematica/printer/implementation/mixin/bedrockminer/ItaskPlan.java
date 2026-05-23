package me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer;

import com.github.bunnyi116.bedrockminer.task.TaskPlan;
import com.github.bunnyi116.bedrockminer.task.TaskPlanItem;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.github.bunnyi116.bedrockminer.task.TaskPlan")
public interface ItaskPlan {
    @Accessor("direction")
    Direction litematica_printer$getDirection();

    @Accessor("piston")
    TaskPlanItem litematica_printer$getPiston();

    @Accessor("redstoneTorch")
    TaskPlanItem litematica_printer$getRedstoneTorch();

    @Accessor("slimeBlock")
    TaskPlanItem litematica_printer$getSlimeBlock();

}
