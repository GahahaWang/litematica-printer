package me.aleksilassila.litematica.printer.utils;

import java.util.Iterator;
import java.util.List;
import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer.IConfigMixin;
import me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer.ITaskMixin;
import me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer.ITaskManagerMixin;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.gameMode;
import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;

public final class BedrockMinerCompact{
    private static final String BEDROCK_MINER_MOD_ID = "bedrockminer";
    private static boolean initialized = false;
    private static boolean bedrockMinerAvailable = false;
    public static boolean overlayMessage = false;
    public static Object task;

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

    public static boolean isBedrockMinerAvailable() {
        return bedrockMinerAvailable;
    }

    public static ITaskManagerMixin getTaskManager() {
        if (!bedrockMinerAvailable) {
            return null;
        }
        try {
            return (ITaskManagerMixin) ITaskManagerMixin.litematica_printer$getInstance();
        } catch (Throwable e) {
            Printer.printDebug("BedrockMinerCompact getInstance() error :\n", e);
            bedrockMinerAvailable = false;
            return null;
        }
    }

    public static void addTask(Block block, BlockPos pos, ClientLevel world) {
        if (!bedrockMinerAvailable) {
            return;
        }
        try {
            Printer.printDebug("Delegate To BedrockMiner");
            getTaskManager().litematica_printer$getPendingBlockTasks().add((ITaskMixin) task);
        } catch (Throwable e) {
            Printer.printDebug("BedrockMinerCompact addTask(Block {}, BlockPos {}, ClientLevel {}) error :\n", block, pos, world, e);
            bedrockMinerAvailable = false;
        }
    }

    public static boolean isWorking() {
        if (!bedrockMinerAvailable) {
            return false;
        }
        try {
            return ITaskManagerMixin.litematica_printer$isWorking();
        } catch (Throwable e) {
            Printer.printDebug("BedrockMinerCompact isWorking() error :\n", e);
            bedrockMinerAvailable = false;
            return false;
        }
    }

    public static boolean isGoodNewTask(ClientLevel world, Block block, BlockPos pos) {
        if (!bedrockMinerAvailable) {
            return false;
        }
        try {
            ITaskManagerMixin taskManager = getTaskManager();
            IConfigMixin config = (IConfigMixin)IConfigMixin.litematica_printer$getInstance();
            if (config.litematica_printer$disable() || !taskManager.litematica_printer$isRunning()) {
                return false;
            }
            if (!taskManager.litematica_printer$isAllowExecutionEnvironment(false)) {
                return false;
            }
            if (!gameMode.isSurvival()) {
                return false;
            }
            if (!config.litematica_printer$isAllowBlock(block)) {
                return false;
            }
            if (config.litematica_printer$isFloorsBlacklist(pos)) {  // Floor restriction
                return false;
            }
            for (ITaskMixin targetBlock : taskManager.litematica_printer$getPendingBlockTasks()) {
                if (targetBlock.litematica_printer$getPos().equals(pos)) {
                    return false;
                }
            }

            Object newTask = ITaskMixin.litematica_printer$newTask(world, block, pos);
            Object taskState = ((ITaskMixin) newTask).litematica_printer$getCurrentState();

            boolean good = isState(taskState,"WAIT_GAME_UPDATE");
            if(good) {
                task = newTask;
            } else {
                Printer.printDebug("ClientLevel {}, Block {}, BlockPos {} isn't good task", world, block, pos);
            }
            return good;
        } catch (Throwable e) {
            Printer.printDebug("BedrockMinerCompact isGoodNewTask(ClientLevel {}, Block {}, BlockPos {}) error :\n", world, block, pos, e);
            bedrockMinerAvailable = false;
            return false;
        }
    }

    public static boolean isBreakingBlock() {
        try {
            if (!bedrockMinerAvailable) {
                return false;
            }
            if (!Breaker.isBreakerAllowed()) {
                return false;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return false;
            }
            if (task == null) {
                return false;
            }
            if (player.getAbilities().instabuild) return false;
            ITaskMixin ITaskMixin = (ITaskMixin) task;
            if(isState(ITaskMixin.litematica_printer$getCurrentState(), "COMPLETE")) {
                task = null;
                return false;
            }
            Vec3 playerPos = mc.player.position();
            double maxReach = Configs.PRINTING_RANGE.getDoubleValue();
            double maxReachSquared = maxReach * maxReach;
            {
                BlockPos taskPos = getTaskPos(task);
                Vec3 ownedTasksBlockCenter = Vec3.atCenterOf(taskPos);
                if (isTaskInWorld(task, mc.level) && playerPos.distanceToSqr(ownedTasksBlockCenter) < maxReachSquared) {
                    Printer.printDebug("BedrockMinerCompact isBreakingBlock at pos{}", taskPos);
                    return true;
                }
            }
            ITaskManagerMixin taskManager = getTaskManager();
            List<?> activeTasks = taskManager.litematica_printer$getActiveBlockTasks();
            List<?> cacheTasks = taskManager.litematica_printer$getCacheBlockTasks();
            if (cacheTasks != null) {
                cacheTasks.clear();
            }
            Iterator<?> activeTasksIterator = activeTasks.iterator();
            while (activeTasksIterator.hasNext()) {
                Object task = activeTasksIterator.next();
                if (task == null) {
                    activeTasksIterator.remove();
                }
                if (!isTaskInWorld(task, mc.level)) {
                    activeTasksIterator.remove();
                }
                Vec3 blockCenter = Vec3.atCenterOf(getTaskPos(task));
                if (playerPos.distanceToSqr(blockCenter) > maxReachSquared) {
                    activeTasksIterator.remove();
                } else {
                    return true;
                }
            }
            return false;
        } catch (Throwable e) {
            Printer.printDebug("BedrockMinerCompact isBreakingBlock() error :\n", e);
            bedrockMinerAvailable = false;
            return false;
        }
    }

    private static boolean hasTaskInList(List<?> tasks, ClientLevel world, BlockPos pos) {
        for (Object task : tasks) {
            if (task == null) {
                continue;
            }
            if (!isTaskInWorld(task, world)) {
                continue;
            }
            BlockPos taskPos = getTaskPos(task);
            if (pos.equals(taskPos)) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos getTaskPos(Object task) {
        return ((ITaskMixin) task).litematica_printer$getPos();
    }

    private static boolean isTaskInWorld(Object task, ClientLevel world) {
        return ((ITaskMixin) task).litematica_printer$getWorld() == world;
    }

    private static boolean isState(Object taskState, String stateName) {
        if (taskState instanceof Enum<?> enumState) {
            return stateName.equals(enumState.name());
        }
        return false;
    }
}
