package me.aleksilassila.litematica.printer.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BedrockMinerCompact implements IClientTickHandler {
    private static boolean initialized = false;
    private static boolean bedrockMinerAvailable = false;
    public static boolean overlayMessage = false;
    private static Class<?> TaskManagerClass;
    private static Method getInstanceMethod;
    private static Method addTaskMethod;
    private static Method isWorkingMethod;
    private static Method isProcessingMethod;
    private static Method getPendingBlockTasksMethod;
    private static Method getActiveBlockTasksMethod;
    private static Method getCacheBlockTasksMethod;
    private static Class<?> ConfigClass;
    private static Method getConfigInstanceMethod;
    private static Method isAllowBlockMethod;
    private static Method isFloorsBlacklistMethod;
    private static Class<?> TaskClass;
    private static Method findMethod;
    private static Class<? extends Enum> TaskStateClass;
    private static Enum<?> TaskState_WAIT_GAME_UPDATE;
    public static TaskRecord ownedTasks;

    public static void init() {
        if (initialized) { return; }
        initialized = true;
        Printer.logger.warn("try init BedrockMinerCompact");
        try {
            TaskManagerClass = Class.forName("com.github.bunnyi116.bedrockminer.task.TaskManager");
            getInstanceMethod = TaskManagerClass.getDeclaredMethod("getInstance");
            addTaskMethod = TaskManagerClass.getDeclaredMethod("addTask", Block.class, BlockPos.class, ClientLevel.class);
            isWorkingMethod = TaskManagerClass.getDeclaredMethod("isWorking");
            isProcessingMethod = TaskManagerClass.getMethod("isProcessing");
            getPendingBlockTasksMethod = TaskManagerClass.getMethod("getPendingBlockTasks");
            getActiveBlockTasksMethod = TaskManagerClass.getMethod("getActiveBlockTasks");
            getCacheBlockTasksMethod = TaskManagerClass.getMethod("getCacheBlockTasks");

            ConfigClass = Class.forName("com.github.bunnyi116.bedrockminer.config.Config");
            getConfigInstanceMethod = ConfigClass.getMethod("getInstance");
            isAllowBlockMethod = ConfigClass.getMethod("isAllowBlock", Block.class);
            isFloorsBlacklistMethod = ConfigClass.getMethod("isFloorsBlacklist", BlockPos.class);

            TaskClass = Class.forName("com.github.bunnyi116.bedrockminer.task.Task");
            findMethod = TaskClass.getDeclaredMethod("find");
            findMethod.setAccessible(true);

            TaskStateClass = (Class<? extends Enum>) Class.forName("com.github.bunnyi116.bedrockminer.task.TaskState");
            TaskState_WAIT_GAME_UPDATE = Enum.valueOf(TaskStateClass, "WAIT_GAME_UPDATE");

            bedrockMinerAvailable = true;
        } catch (Exception e) {
            Printer.logger.warn("Bedrock-miner reflect failed, printer won't support bedrock mining");
        }
    }

    public static boolean isBedrockMinerAvailable() {
        return bedrockMinerAvailable;
    }

    public static Object getTaskManager() {
        try {
            return getInstanceMethod.invoke(null);
        } catch (Exception e) {
            Printer.printDebug("BedrockMinerCompact getInstance() error :\n", e);
            bedrockMinerAvailable = false;
            return null;
        }
    }

    public static boolean addTask(Block block, BlockPos pos, ClientLevel world){
        try {
            Object taskManager = getTaskManager();
            if (hasTaskForPos(taskManager, world, pos)) {
                return false;
            }
            //if (ownedTasks != null) {
            //    return false;
            //}
            addTaskMethod.invoke(null, block, pos, world);
            ownedTasks = new TaskRecord(world, pos.immutable());
            return true;
        } catch (Exception e) {
            Printer.printDebug("BedrockMinerCompact addTask(Block {}, BlockPos {}, ClientLevel {}) error :\n",block, pos, world, e);
            bedrockMinerAvailable = false;
            //ownedTasks = null;
            return false;
        }
    }

    public static boolean isWorking(){
        try {
            return (boolean) isWorkingMethod.invoke(null);
        } catch (Exception e) {
            Printer.printDebug("BedrockMinerCompact isWorking() error :\n", e);
            bedrockMinerAvailable = false;
            return false;
        }
    }
    /*
    public static boolean isProcessing(){
        try {
            Object instance = getTaskManager();
            boolean isProcessing =  (boolean) isProcessingMethod.invoke(instance);
            if (!isProcessing) cleanupOwnedTasks(instance);
            return isProcessing;
        } catch (Exception e) {
            Printer.printDebug("BedrockMinerCompact isProcessing() error :\n", e);
            bedrockMinerAvailable = false;
            return false;
        }
    }*/

    public static boolean canHandleBlock(Block block, BlockPos pos) {
        try {
            Object config = getConfigInstanceMethod.invoke(null);
            boolean allowBlock = (boolean)isAllowBlockMethod.invoke(config, block);
            boolean floorsBlacklist = (boolean)isFloorsBlacklistMethod.invoke(config, pos);
            return allowBlock && !floorsBlacklist;
        } catch (Exception e) {
            Printer.printDebug("BedrockMinerCompact canHandleBlock(Block {}, BlockPos {}) error : ", block, pos, e);
            bedrockMinerAvailable = false;
            return false;
        }
    }

    public static boolean isGoodNewTask(ClientLevel world, Block block, BlockPos pos) {
        try {
            Object task = TaskClass.getDeclaredConstructor(ClientLevel.class, Block.class, BlockPos.class)
                    .newInstance(world, block, pos);
            overlayMessage = false;
            findMethod.invoke(task);
            overlayMessage = true;
            Field taskState = task.getClass().getDeclaredField("currentState");
            taskState.setAccessible(true);
            return (taskState.get(task) == TaskState_WAIT_GAME_UPDATE);
        } catch (Exception e) {
            Printer.printDebug("BedrockMinerCompact isGoodNewTask(ClientLevel {}, Block {}, BlockPos {}) error :\n", world, block, pos, e);
            bedrockMinerAvailable = false;
            return false;
        }
    }

    public static boolean isBreakingBlock(){
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
            Object taskManager = getTaskManager();
            if (taskManager == null) {
                return false;
            }
            List<?> activeTasks = getTaskList(taskManager, getActiveBlockTasksMethod);
            if (activeTasks.isEmpty()) {
                return false;
            }
            Vec3 playerPos = mc.player.position();
            double maxReach = Configs.PRINTING_RANGE.getDoubleValue();
            double maxReachSquared = maxReach * maxReach;

            for (Object task : activeTasks) {
                if (task == null) {
                    continue;
                }
                if (!isTaskInWorld(task, mc.level)) {
                    continue;
                }
                BlockPos taskPos = getTaskPos(task);
                BlockState state = mc.level.getBlockState(taskPos);
                if (state.isAir() || state.canBeReplaced()) {
                    continue;
                }
                Vec3 blockCenter = Vec3.atCenterOf(taskPos);
                if (playerPos.distanceToSqr(blockCenter) > maxReachSquared) {
                    continue;
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            Printer.printDebug("BedrockMinerCompact isBreakingBlock() error :\n", e);
            return false;
        }
    }

    /*
    private static void cleanupOwnedTasks(Object taskManager) throws Exception {
        if (ownedTasks == null) {
            return;
        }
        Iterator<TaskRecord> iterator = ownedTasks.iterator();
        while (iterator.hasNext()) {
            TaskRecord record = iterator.next();
            if (!hasTaskForPos(taskManager, record.world(), record.pos())) {
                iterator.remove();
            }
        }
    }*/

    private static boolean hasTaskForPos(Object taskManager, ClientLevel world, BlockPos pos) throws Exception {
        return hasTaskInList(getTaskList(taskManager, getPendingBlockTasksMethod), world, pos)
            || hasTaskInList(getTaskList(taskManager, getActiveBlockTasksMethod), world, pos);
    }

    private static List<?> getTaskList(Object taskManager, Method method) throws Exception {
        Object result = method.invoke(taskManager);
        if (result instanceof List) {
            return (List<?>) result;
        }
        return Collections.emptyList();
    }

    private static boolean hasTaskInList(List<?> tasks, ClientLevel world, BlockPos pos) throws Exception {
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

    private static BlockPos getTaskPos(Object task) throws Exception {
        Object value = task.getClass().getField("pos").get(task);
        return (BlockPos) value;
    }

    private static boolean isTaskInWorld(Object task, ClientLevel world) throws Exception {
        Object value = task.getClass().getField("world").get(task);
        return value == world;
    }

    @Override
    public void onClientTick(Minecraft mc) {

    }

    private record TaskRecord(ClientLevel world, BlockPos pos) {}
}
