package me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer;

import com.github.bunnyi116.bedrockminer.task.Scheme;
import com.github.bunnyi116.bedrockminer.task.Task;
import com.github.bunnyi116.bedrockminer.task.TaskState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(value = Task.class, remap = false)
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

    @Accessor("activeScheme")
    @Nullable Scheme litematica_printer$getActiveScheme();

    @Invoker("isComplete")
    boolean litematica_printer$isComplete();

}
