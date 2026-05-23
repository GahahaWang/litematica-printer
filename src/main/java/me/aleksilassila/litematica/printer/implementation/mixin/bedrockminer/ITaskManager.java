package me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer;

import java.util.ArrayList;
import java.util.List;

import com.github.bunnyi116.bedrockminer.task.TaskManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "com.github.bunnyi116.bedrockminer.task.TaskManager", remap = false)
public interface ITaskManager {
	@Invoker("getInstance")
	static TaskManager litematica_printer$getInstance() {
		throw new AssertionError();
	}

	@Invoker("isAllowExecutionEnvironment")
    boolean litematica_printer$isAllowExecutionEnvironment(boolean setOverlayMessage);

	@Invoker("isRunning")
	boolean litematica_printer$isRunning();

	@Invoker("isWorking")
	static boolean litematica_printer$isWorking() {
		throw new AssertionError();
	}

	@Invoker("getPendingBlockTasks")
	List<ITask> litematica_printer$getPendingBlockTasks();

	@Invoker("getActiveBlockTasks")
	ArrayList<ITask> litematica_printer$getActiveBlockTasks();

	@Invoker("getCacheBlockTasks")
	ArrayList<ITask> litematica_printer$getCacheBlockTasks();

}
