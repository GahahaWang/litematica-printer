package me.aleksilassila.litematica.printer.utils;

import java.util.Iterator;
import java.util.List;

import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer.IConfig;
import me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer.ITask;
import me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer.ITaskManager;
import me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer.ItaskPlan;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public final class BedrockMinerCompact implements IClientTickHandler {
    private static final String BEDROCK_MINER_MOD_ID = "bedrockminer";
    private static boolean initialized = false;
    private static boolean bedrockMinerAvailable = false;
    public static ObjectOpenHashSet<ITask> tasks = new ObjectOpenHashSet<>(100);

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        Printer.logger.warn("try init BedrockMinerCompact");
        bedrockMinerAvailable = FabricLoader.getInstance().isModLoaded(BEDROCK_MINER_MOD_ID);
        if (!bedrockMinerAvailable) {
            Printer.logger.warn("Bedrock-miner not found, printer won't support bedrock mining");
        }
    }

    public static boolean isExecutionEnvironmentValid() {
        try {
            if (!bedrockMinerAvailable) {
                return false;
            }
            if (!ITaskManager.litematica_printer$isWorking()) {
                return false;
            }
            if (!Breaker.isBreakerAllowed()) {
                return false;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return false;
            }
            if (mc.player.getAbilities().instabuild) return false;
            ITaskManager taskManager = getTaskManager();
            IConfig config = (IConfig) IConfig.litematica_printer$getInstance();
            if (config.litematica_printer$disable() || !taskManager.litematica_printer$isRunning()) {
                return false;
            }
            if (!taskManager.litematica_printer$isAllowExecutionEnvironment(false)) {
                return false;
            }
            if (!mc.player.gameMode().isSurvival()) {
                return false;
            }
            return true;
        } catch (Throwable e) {
            Printer.printDebug("BedrockMinerCompact isExecutionEnvironmentValid() error :\n", e);
            bedrockMinerAvailable = false;
            return false;
        }
    }

    public static ITaskManager getTaskManager() {
        if (!bedrockMinerAvailable) {
            return null;
        }
        try {
            return (ITaskManager) ITaskManager.litematica_printer$getInstance();
        } catch (Throwable e) {
            Printer.printDebug("BedrockMinerCompact getInstance() error :\n", e);
            bedrockMinerAvailable = false;
            return null;
        }
    }

    public static void addTask(ITask task) {
        if (!bedrockMinerAvailable) {
            return;
        }
        try {
            Printer.printDebug("Delegate To BedrockMiner");
            getTaskManager().litematica_printer$getPendingBlockTasks().add(task);
            tasks.add(task);
        } catch (Throwable e) {
            Printer.printDebug("BedrockMinerCompact addTask(Object task) error", e);
            bedrockMinerAvailable = false;
        }
    }

    public static ITask isGoodNewTask(ClientLevel world, Block block, BlockPos pos) {
        if (!bedrockMinerAvailable) {
            return null;
        }
        try {
            ITaskManager taskManager = getTaskManager();
            IConfig config = (IConfig) IConfig.litematica_printer$getInstance();

            if (!config.litematica_printer$isAllowBlock(block)) {
                return null;
            }
            if (config.litematica_printer$isFloorsBlacklist(pos)) {  // Floor restriction
                return null;
            }
            for (ITask task : taskManager.litematica_printer$getPendingBlockTasks()) {
                if (isSameTask(task, world, block, pos))
                    return null;
            }
            for(ITask task : tasks) {
                if (isSameTask(task, world, block, pos))
                    return null;
            }
            for(ITask task : taskManager.litematica_printer$getActiveBlockTasks()) {
                if (isSameTask(task, world, block, pos))
                    return null;
            }

            ITask newTask = (ITask)ITask.litematica_printer$newTask(world, block, pos);
            var taskState = newTask.litematica_printer$getCurrentState();
            boolean good = isState(taskState,"WAIT_GAME_UPDATE");
            if(good) {
                return newTask;
            } else {
                Printer.printDebug("ClientLevel {}, Block {}, BlockPos {} isn't good task", world, block, pos);
            }
            return null;
        } catch (Throwable e) {
            Printer.printDebug("BedrockMinerCompact isGoodNewTask(ClientLevel {}, Block {}, BlockPos {}) error :\n", world, block, pos, e);
            bedrockMinerAvailable = false;
            return null;
        }
    }

    public static boolean canAcceptBedrockMinerTask() {
        try {
            if (!bedrockMinerAvailable) {
                return false;
            }
            IConfig config = (IConfig) IConfig.litematica_printer$getInstance();
            List<ITask> activeTasks = getTaskManager().litematica_printer$getActiveBlockTasks();
            Printer.printDebug("limitmax {}",config.litematica_printer$getLimitMax());
            Printer.printDebug("tasks size {}",tasks.size());
            Printer.printDebug("activeTasks size {}",activeTasks.size());
            return tasks.size() < config.litematica_printer$getLimitMax() &&
                    activeTasks.size() < config.litematica_printer$getLimitMax();
        } catch (Throwable e) {
            Printer.printDebug("BedrockMinerCompact isBreakingBlock() error :\n", e);
            bedrockMinerAvailable = false;
            return false;
        }
    }

    private static boolean isState(Object taskState, String stateName) {
        if (taskState instanceof Enum<?> enumState) {
            return stateName.equals(enumState.name());
        }
        return false;
    }

    private static boolean isSameOrAdjacent(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        int dz = Math.abs(a.getZ() - b.getZ());
        int manhattan = dx + dy + dz;
        return manhattan == 0 || manhattan == 1;
    }

    public static boolean isBedrockMinerReservedPos(BlockPos blockPos) {
        for (ITask task : tasks) {
            BlockPos taskPos = task.litematica_printer$getPos();
            boolean x = Math.abs(taskPos.getX() - blockPos.getX()) <= 2;
            boolean y = Math.abs(taskPos.getY() - blockPos.getY()) <= 2;
            boolean z = Math.abs(taskPos.getZ() - blockPos.getZ()) <= 2;
            if (x|y|z) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBedrockMinerReservedPosFromPlan(BlockPos blockPos) {
        for (ITask task : tasks) {
            ItaskPlan planItem = (ItaskPlan)task.litematica_printer$getPlanItem();
            if (planItem == null) {
                continue;
            }
            var piston = planItem.litematica_printer$getPiston();
            var redstoneTorch = planItem.litematica_printer$getRedstoneTorch();
            var slimeBlock = planItem.litematica_printer$getSlimeBlock();

            if (isSameOrAdjacent(blockPos, piston.pos)
                    || isSameOrAdjacent(blockPos, redstoneTorch.pos)
                    || isSameOrAdjacent(blockPos, slimeBlock.pos)) {
                return true;
            }

            BlockPos pistonHeadPos = piston.pos.relative(piston.facing);
            if (blockPos.equals(pistonHeadPos)) {
                return true;
            }

            BlockPos redstoneTorchBasePos = redstoneTorch.pos.relative(redstoneTorch.facing.getOpposite());
            if (blockPos.equals(redstoneTorchBasePos)) {
                return true;
            }
        }
        return false;
    }

    private static void cleanTasks(Iterator<ITask> tasksIterator) {
        var mc = Minecraft.getInstance();
        Vec3 playerPos = mc.player.position();
        double maxReach = Configs.PRINTING_RANGE.getDoubleValue();
        double maxReachSquared = maxReach * maxReach;

        while (tasksIterator.hasNext()) {
            ITask task = tasksIterator.next();
            if(isState(task.litematica_printer$getCurrentState(), "COMPLETE")) {
                tasksIterator.remove();
                continue;
            }
            BlockPos taskPos = task.litematica_printer$getPos();
            Vec3 ownedTasksBlockCenter = Vec3.atCenterOf(taskPos);
            if (task.litematica_printer$getWorld() == mc.level &&
                    playerPos.distanceToSqr(ownedTasksBlockCenter) > maxReachSquared) {
                tasksIterator.remove();
            }
            Printer.printDebug("BedrockMinerCompact isBreakingBlock at pos{}", taskPos);
        }
    }

    private static boolean isSameTask(ITask task1, Level level, Block block, BlockPos pos) {
        return task1.litematica_printer$getPos().equals(pos) &&
                task1.litematica_printer$getWorld().equals(level) &&
                task1.litematica_printer$getBlock().equals(block);
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        ITaskManager taskManager = getTaskManager();
        taskManager.litematica_printer$getCacheBlockTasks().clear();
        List<ITask> activeTasks = taskManager.litematica_printer$getActiveBlockTasks();
        List<ITask> pendingTasks = taskManager.litematica_printer$getPendingBlockTasks();
        Iterator<ITask> tasksIterator = tasks.iterator();
        Iterator<ITask> activeTasksIterator = activeTasks.iterator();
        Iterator<ITask> pendingTasksIterator = pendingTasks.iterator();
        cleanTasks(tasksIterator);
        cleanTasks(activeTasksIterator);
        cleanTasks(pendingTasksIterator);
    }
}
