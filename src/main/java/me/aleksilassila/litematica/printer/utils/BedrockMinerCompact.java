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
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public final class BedrockMinerCompact implements IClientTickHandler {
    private static final String BEDROCK_MINER_MOD_ID = "bedrockminer";
    private static boolean initialized = false;
    private static boolean bedrockMinerAvailable = false;
    public static final ObjectOpenHashSet<ITask> tasks = new ObjectOpenHashSet<>(100);
    public static final ObjectOpenHashSet<ITask> ANTI_GHOST_BLOCK = new ObjectOpenHashSet<>(100);

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

    public static ITask isValidNewTask(ClientLevel world, Block block, BlockPos pos) {
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

    public static boolean willTaskEffectOthersTask(ITask task) {
        ItaskPlan planItem = (ItaskPlan) task.litematica_printer$getPlanItem();
        if (planItem == null) {
            return false;
        }
        for (ITask task2 : tasks) {
            ItaskPlan planItem2 = (ItaskPlan) task2.litematica_printer$getPlanItem();
            if (planItem2 == null) {
                continue;
            }
            if (doesPlanImpact(planItem, planItem2) || doesPlanImpact(planItem2, planItem)) {
                return true;
            }
        }
        return false;
    }

    private static boolean doesPlanImpact(ItaskPlan source, ItaskPlan target) {
        var sourcePiston = source.litematica_printer$getPiston();
        var sourceTorch = source.litematica_printer$getRedstoneTorch();
        var sourceSlime = source.litematica_printer$getSlimeBlock();

        BlockPos sourcePistonPos = sourcePiston.pos;
        BlockPos sourcePistonHeadPos = sourcePistonPos.relative(sourcePiston.facing);
        BlockPos sourceTorchPos = sourceTorch.pos;

        var targetPiston = target.litematica_printer$getPiston();
        var targetTorch = target.litematica_printer$getRedstoneTorch();
        var targetSlime = target.litematica_printer$getSlimeBlock();

        BlockPos targetPistonPos = targetPiston.pos;
        BlockPos targetPistonHeadPos = targetPistonPos.relative(targetPiston.facing);
        BlockPos targetTorchPos = targetTorch.pos;
        BlockPos targetTorchBasePos = targetTorchPos.relative(targetTorch.facing.getOpposite());
        BlockPos targetSlimePos = targetSlime.pos;

        if (sourceTorchPos.offset(1,0,0) == targetPistonPos||
                sourceTorchPos.offset(-1,0,0) == targetPistonPos||
                sourceTorchPos.offset(0,1,0) == targetPistonPos||
                sourceTorchPos.offset(0,-1,0) == targetPistonPos||
                sourceTorchPos.offset(0,0,1) == targetPistonPos||
                sourceTorchPos.offset(0,0,-1) == targetPistonPos||
                sourceTorchPos.offset(1,-1,0) == targetPistonPos||
                sourceTorchPos.offset(-1,-1,0) == targetPistonPos||
                sourceTorchPos.offset(0,-1,1) == targetPistonPos||
                sourceTorchPos.offset(0,-1,-1) == targetPistonPos||
                sourceTorchPos.offset(1,1,0) == targetPistonPos||
                sourceTorchPos.offset(-1,1,0) == targetPistonPos||
                sourceTorchPos.offset(0,1,1) == targetPistonPos||
                sourceTorchPos.offset(0,1,-1) == targetPistonPos||
                sourceTorchPos.offset(0,2,0) == targetPistonPos||
                sourceTorchPos.offset(0,-2,0) == targetPistonPos
        ) return true;

        if (sourcePistonHeadPos == targetPistonPos||
                sourcePistonHeadPos == targetPistonHeadPos||
                sourcePistonHeadPos == targetTorchPos||
                sourcePistonHeadPos == targetTorchBasePos||
                sourcePistonHeadPos == targetSlimePos
        ) return true;

        return false;
    }

    private static void cleanTasks(Iterator<ITask> tasksIterator) {
        var mc = Minecraft.getInstance();
        Vec3 playerPos = mc.player.position();
        double maxReach = Configs.PRINTING_RANGE.getDoubleValue();
        double maxReachSquared = maxReach * maxReach;

        while (tasksIterator.hasNext()) {
            ITask task = tasksIterator.next();
            if (task == null) {
                tasksIterator.remove();
                continue;
            }
            if(task.litematica_printer$isComplete()) {
                tasksIterator.remove();
                ANTI_GHOST_BLOCK.add(task);
                continue;
            }
            BlockPos taskPos = task.litematica_printer$getPos();
            Vec3 ownedTasksBlockCenter = Vec3.atCenterOf(taskPos);
            if (task.litematica_printer$getWorld() == mc.level &&
                    playerPos.distanceToSqr(ownedTasksBlockCenter) > maxReachSquared) {
                tasksIterator.remove();
                continue;
            }
            //Printer.printDebug("BedrockMinerCompact isBreakingBlock at pos{}", taskPos);
        }
    }

    private static boolean isSameTask(ITask task1, Level level, Block block, BlockPos pos) {
        return task1.litematica_printer$getPos().equals(pos) &&
                task1.litematica_printer$getWorld().equals(level) &&
                task1.litematica_printer$getBlock().equals(block);
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null || !bedrockMinerAvailable) {
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
        for (ITask task: ANTI_GHOST_BLOCK) {
            ServerboundPlayerActionPacket packetTarget = new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    task.litematica_printer$getPos(),
                    Direction.UP       // with ABORT_DESTROY_BLOCK, this value is unused
            );
            mc.getConnection().send(packetTarget);
            if (task.litematica_printer$getPlanItem() == null) continue;
            ServerboundPlayerActionPacket packetPiston = new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    task.litematica_printer$getPlanItem().piston.pos,
                    Direction.UP       // with ABORT_DESTROY_BLOCK, this value is unused
            );
            mc.getConnection().send(packetPiston);
            ServerboundPlayerActionPacket packetPistonHead = new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    task.litematica_printer$getPlanItem().piston.pos.relative(task.litematica_printer$getPlanItem().piston.facing),
                    Direction.UP       // with ABORT_DESTROY_BLOCK, this value is unused
            );
            mc.getConnection().send(packetPistonHead);
        }
        ANTI_GHOST_BLOCK.clear();
    }
}
