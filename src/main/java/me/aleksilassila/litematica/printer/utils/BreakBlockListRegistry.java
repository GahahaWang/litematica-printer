package me.aleksilassila.litematica.printer.utils;

import fi.dy.masa.litematica.util.BlockUtils;
import me.aleksilassila.litematica.printer.config.BreakListMode;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Optional;

/**
 * Decides whether the breaker is allowed to break a given block, combining the built-in list of
 * structurally dangerous/unbreakable blocks with the user configurable blacklist/whitelist.
 */
public class BreakBlockListRegistry {
    // Always ignored regardless of the user's blacklist/whitelist mode.
    private static final HashSet<Block> DEFAULT_IGNORE_LIST = getDefaultIgnoreList();

    private final BreakListMode mode;
    private final HashSet<Block> userList;

    public BreakBlockListRegistry() {
        this.mode = (BreakListMode) Configs.BREAK_LIST_MODE.getOptionListValue();
        this.userList = parseBlocks(this.mode == BreakListMode.WHITELIST
                ? Configs.BREAK_BLOCKS_WHITELIST.getStrings()
                : Configs.BREAK_BLOCKS_BLACKLIST.getStrings());
    }

    public boolean isBreakable(Block block) {
        if (DEFAULT_IGNORE_LIST.contains(block)) {
            return false;
        }

        return switch (this.mode) {
            case BLACKLIST -> !this.userList.contains(block);
            case WHITELIST -> this.userList.contains(block);
            case NONE -> true;
        };
    }

    private static HashSet<Block> parseBlocks(Iterable<String> values) {
        HashSet<Block> blocks = new HashSet<>();

        for (String value : values) {
            Optional<Block> block = BlockUtils.getBlockFromString(value.trim());
            block.ifPresent(blocks::add);
        }

        return blocks;
    }

    private static HashSet<Block> getDefaultIgnoreList() {
        var set = new HashSet<Block>();
        set.add(Blocks.BARRIER);                    // barrier
        set.add(Blocks.COMMAND_BLOCK);              // command block
        set.add(Blocks.CHAIN_COMMAND_BLOCK);        // chain command block
        set.add(Blocks.REPEATING_COMMAND_BLOCK);    // repeating command block
        set.add(Blocks.STRUCTURE_VOID);             // structure void
        set.add(Blocks.STRUCTURE_BLOCK);            // structure block
        set.add(Blocks.JIGSAW);                     // jigsaw block
        set.add(Blocks.MOVING_PISTON);
        set.add(Blocks.PISTON_HEAD);
        set.add(Blocks.BUBBLE_COLUMN);
        set.add(Blocks.LAVA);
        set.add(Blocks.WATER);
        set.add(Blocks.BEDROCK);
        return set;
    }
}
