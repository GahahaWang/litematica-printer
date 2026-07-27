package me.aleksilassila.litematica.printer.config;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.*;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.PrinterReference;

public class Configs implements IConfigHandler {
    private static final String CONFIG_FILE_NAME = PrinterReference.MOD_ID + ".json";
    private static final String PRINTER_KEY = PrinterReference.MOD_KEY + ".config.printer";

    private static final String CATEGORY_PRINTER = "Printer";

    // Configs settings
    public static final ConfigInteger PRINTING_INTERVAL = new ConfigInteger("printingInterval", 12, 1, 40).apply(PRINTER_KEY);
    public static final ConfigDouble PRINTING_RANGE = new ConfigDouble("printingRange", 5, 2.5, 5).apply(PRINTER_KEY);
    public static final ConfigBoolean FAR_FIRST = new ConfigBoolean("farFirst", false).apply(PRINTER_KEY);
    public static final ConfigBoolean PRINT_MODE = new ConfigBoolean("printingMode", false).apply(PRINTER_KEY);
    public static final ConfigBoolean PRINT_DEBUG = new ConfigBoolean("printingDebug", false).apply(PRINTER_KEY);
    public static final ConfigBoolean REPLACE_FLUIDS_SOURCE_BLOCKS = new ConfigBoolean("replaceFluidSourceBlocks", true).apply(PRINTER_KEY);
    public static final ConfigBoolean CLEAR_FLUID = new ConfigBoolean("clearFluid", false).apply(PRINTER_KEY);
    public static final ConfigStringList BLOCKS_CLEAR_FLUID = new ConfigStringList("blocksClearFluid", ImmutableList.of("minecraft:cobblestone")).apply(PRINTER_KEY);
    public static final ConfigBoolean STRIP_LOGS = new ConfigBoolean("stripLogs", true).apply(PRINTER_KEY);
    public static final ConfigBoolean INTERACT_BLOCKS = new ConfigBoolean("interactBlocks", true).apply(PRINTER_KEY);
    public static final ConfigBoolean BREAK_BLOCKS = new ConfigBoolean("breakBlocks", true).apply(PRINTER_KEY);
    public static final ConfigOptionList BREAKER_OPTION = new ConfigOptionList("breakerOption", BreakerOption.HOLD).apply(PRINTER_KEY);
    public static final ConfigOptionList BREAK_PREFERENCE = new ConfigOptionList("breakPreference", BreakPreference.DEFAULT).apply(PRINTER_KEY);
    public static final ConfigBoolean TOOL_DURABILITY_PROTECTION = new ConfigBoolean("toolDurabilityProtection", true).apply(PRINTER_KEY);
    public static final ConfigInteger TOOL_DURABILITY_THRESHOLD = new ConfigInteger("toolDurabilityThreshold", 10, 1, 100).apply(PRINTER_KEY);
    public static final ConfigOptionList BREAK_LIST_MODE = new ConfigOptionList("breakListMode", BreakListMode.NONE).apply(PRINTER_KEY);
    public static final ConfigStringList BREAK_BLOCKS_BLACKLIST = new ConfigStringList("breakBlocksBlacklist", ImmutableList.of()).apply(PRINTER_KEY);
    public static final ConfigStringList BREAK_BLOCKS_WHITELIST = new ConfigStringList("breakBlocksWhitelist", ImmutableList.of()).apply(PRINTER_KEY);
    public static final ConfigBoolean AVOID_BREAKING_UNDER_PLAYER = new ConfigBoolean("avoidBreakingUnderPlayer", true).apply(PRINTER_KEY);
    public static final ConfigBoolean BREAKER_USE_BEDROCK_MINER = new ConfigBoolean("breakerUseBedrockMiner", false).apply(PRINTER_KEY);
    public static final ConfigBoolean PRINT_IN_AIR = new ConfigBoolean("printInAir", false).apply(PRINTER_KEY);
    public static final ConfigBoolean FALLING_BLOCK_PRINT_IN_AIR = new ConfigBoolean("fallingBlockPrintInAir", false).apply(PRINTER_KEY);
    public static final ConfigInteger PRINTER_CACHE_MS = new ConfigInteger("printerCacheMs", 200, 0, 5000).apply(PRINTER_KEY);

    // Smart Redstone configs
    public static final ConfigBoolean SMART_REDSTONE_ENABLED = new ConfigBoolean("smartRedstoneEnabled", true).apply(PRINTER_KEY);
    public static final ConfigBoolean OBSERVER_AVOID_ALL = new ConfigBoolean("observerAvoidAll", true).apply(PRINTER_KEY);
    public static final ConfigBoolean SUPPRESS_PUSH_LIMIT = new ConfigBoolean("suppressPushLimitPistons", true).apply(PRINTER_KEY);
    public static final ConfigBoolean AVOID_CHECK_ONLY_PISTONS = new ConfigBoolean("avoidCheckOnlyPistons", true).apply(PRINTER_KEY);
    public static final ConfigBoolean ROTATE = new ConfigBoolean("rotate", true).apply(PRINTER_KEY);

    // HUD configs
    public static final ConfigBoolean PRINTER_ON_HUD_ENABLED = new ConfigBoolean("printerOnHudEnabled", true).apply(PRINTER_KEY);
    public static final ConfigInteger PRINTER_ON_HUD_X = new ConfigInteger("printerOnHudX", 4, 0, 2000).apply(PRINTER_KEY);
    public static final ConfigInteger PRINTER_ON_HUD_Y = new ConfigInteger("printerOnHudY", 4, 0, 2000).apply(PRINTER_KEY);
    public static final ConfigColor PRINTER_ON_HUD_COLOR = new ConfigColor("printerOnHudColor", "#FF27C300").apply(PRINTER_KEY);
    public static final ConfigDouble PRINTER_ON_HUD_SCALE = new ConfigDouble("printerOnHudScale", 2.0, 0.25, 4.0).apply(PRINTER_KEY);

    // Hotkeys
    public static final ConfigHotkey PRINT = new ConfigHotkey("print", "V", KeybindSettings.PRESS_ALLOWEXTRA_EMPTY).apply(PRINTER_KEY);
    public static final ConfigHotkey TOGGLE_PRINTING_MODE = new ConfigHotkey("togglePrintingMode", "CAPS_LOCK", KeybindSettings.PRESS_ALLOWEXTRA_EMPTY).apply(PRINTER_KEY);

    public static final ImmutableList<ConfigHotkey> HOTKEY_LIST = ImmutableList.of(
            PRINT,
            TOGGLE_PRINTING_MODE
    );

    public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
            PRINT_MODE,
            PRINT_DEBUG,
            PRINTING_INTERVAL,
            PRINTING_RANGE,
            FAR_FIRST,
            REPLACE_FLUIDS_SOURCE_BLOCKS,
            CLEAR_FLUID,
            BLOCKS_CLEAR_FLUID,
            STRIP_LOGS,
            INTERACT_BLOCKS,
            BREAK_BLOCKS,
            BREAKER_OPTION,
            BREAK_PREFERENCE,
            TOOL_DURABILITY_PROTECTION,
            TOOL_DURABILITY_THRESHOLD,
            BREAK_LIST_MODE,
            BREAK_BLOCKS_BLACKLIST,
            BREAK_BLOCKS_WHITELIST,
            AVOID_BREAKING_UNDER_PLAYER,
            BREAKER_USE_BEDROCK_MINER,
            PRINT_IN_AIR,
            FALLING_BLOCK_PRINT_IN_AIR,
            PRINTER_CACHE_MS,
            ROTATE,
            PRINTER_ON_HUD_ENABLED,
            PRINTER_ON_HUD_X,
            PRINTER_ON_HUD_Y,
            PRINTER_ON_HUD_COLOR,
            PRINTER_ON_HUD_SCALE,
            SMART_REDSTONE_ENABLED,
            OBSERVER_AVOID_ALL,
            SUPPRESS_PUSH_LIMIT,
            AVOID_CHECK_ONLY_PISTONS
    );

    public static ImmutableList<IConfigBase> getPrinterTabConfigs() {
        return ImmutableList.<IConfigBase>builder()
                .addAll(OPTIONS)
                .addAll(HOTKEY_LIST)
                .build();
    }

    public static void loadFromFile() {
        Path configFile = FileUtils.getConfigDirectory().resolve(CONFIG_FILE_NAME);

        if (!Files.exists(configFile) || !Files.isReadable(configFile)) {
            return;
        }

        JsonElement element = JsonUtils.parseJsonFile(configFile);

        if (element == null || !element.isJsonObject()) {
            Printer.logger.error("loadFromFile(): Failed to load config file '{}'.", configFile.toAbsolutePath());
            return;
        }

        JsonObject root = element.getAsJsonObject();

        ConfigUtils.readConfigBase(root, CATEGORY_PRINTER, getPrinterTabConfigs());
    }

    public static void saveToFile() {
        Path dir = FileUtils.getConfigDirectory();

        if (!Files.exists(dir)) {
            FileUtils.createDirectoriesIfMissing(dir);
        }

        if (Files.isDirectory(dir)) {
            JsonObject root = new JsonObject();

            ConfigUtils.writeConfigBase(root, CATEGORY_PRINTER, getPrinterTabConfigs());

            JsonUtils.writeJsonToFile(root, dir.resolve(CONFIG_FILE_NAME));
        } else {
            Printer.logger.error("saveToFile(): Config folder '{}' does not exist!", dir.toAbsolutePath());
        }
    }

    @Override
    public void load() {
        loadFromFile();
    }

    @Override
    public void save() {
        saveToFile();
    }
}
