package me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer;

import com.github.bunnyi116.bedrockminer.task.Task;
import com.github.bunnyi116.bedrockminer.task.TaskPlan;
import com.github.bunnyi116.bedrockminer.task.TaskState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "com.github.bunnyi116.bedrockminer.task.Task", remap = false)
public interface ITask {

    @Invoker("<init>")
    static Task litematica_printer$newTask(ClientLevel world, Block block, BlockPos pos) {
        throw new AssertionError();
    }

    @Accessor("currentState")
    TaskState litematica_printer$getCurrentState();

    @Accessor("world")
    ClientLevel litematica_printer$getWorld();

    @Accessor("pos")
    BlockPos litematica_printer$getPos();

    @Accessor("block")
    Block litematica_printer$getBlock();

    @Accessor("planItem")
    TaskPlan litematica_printer$getPlanItem();

    @Invoker("isComplete")
    boolean litematica_printer$isComplete();

}
