package me.aleksilassila.litematica.printer.utils;

import java.lang.reflect.Method;

import me.aleksilassila.litematica.printer.Printer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

public final class BedrockMinerCompact {
    private static boolean initialized = false;
    private static boolean bedrockMinerAvailable = false;
    private static Class<?> TaskManagerClass;
    private static Method getInstanceMethod;
    private static Method addTaskMethod;
    private static Method removeBlockTaskMethod;
    private static Method isWorkingMethod;
    private static Method isProcessingMethod;
    private static Class<?> configClass;
    private static Method getConfigInstanceMethod;
    private static Method isAllowBlockMethod;
    private static Method isFloorsBlacklistMethod;

    public static void init() {
        if (initialized) { return; }
        initialized = true;
        Printer.logger.warn("try init BedrockMinerCompact");
        try {
            TaskManagerClass = Class.forName("com.github.bunnyi116.bedrockminer.task.TaskManager");
            getInstanceMethod = TaskManagerClass.getDeclaredMethod("getInstance");
            addTaskMethod = TaskManagerClass.getDeclaredMethod("addTask", Block.class, BlockPos.class, ClientLevel.class);
            removeBlockTaskMethod = TaskManagerClass.getMethod("removeBlockTask", ClientLevel.class, BlockPos.class);
            isWorkingMethod = TaskManagerClass.getDeclaredMethod("isWorking");
            isProcessingMethod = TaskManagerClass.getMethod("isProcessing");

            configClass = Class.forName("com.github.bunnyi116.bedrockminer.config.Config");
            getConfigInstanceMethod = configClass.getMethod("getInstance");
            isAllowBlockMethod = configClass.getMethod("isAllowBlock", Block.class);
            isFloorsBlacklistMethod = configClass.getMethod("isFloorsBlacklist", BlockPos.class);

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
            addTaskMethod.invoke(null, block, pos, world);
            return true;
        } catch (Exception e) {
            Printer.printDebug("BedrockMinerCompact addTask(Block {}, BlockPos {}, ClientLevel {}) error :\n",block, pos, world, e);
            bedrockMinerAvailable = false;
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
    
    public static boolean isProcessing(){
        try {
            Object instance = getTaskManager();
            if (instance == null) {
                return false;
            }
            return (boolean) isProcessingMethod.invoke(instance);
        } catch (Exception e) {
            Printer.printDebug("BedrockMinerCompact isProcessing() error :\n", e);
            bedrockMinerAvailable = false;
            return false;
        }
    }

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

    private record TaskRecord(ClientLevel world, BlockPos pos, Block block) {}
}
