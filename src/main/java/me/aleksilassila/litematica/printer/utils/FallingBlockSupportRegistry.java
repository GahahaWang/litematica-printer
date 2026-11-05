package me.aleksilassila.litematica.printer.utils;

import fi.dy.masa.litematica.util.BlockUtils;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FallingBlockSupportRegistry {
    private final List<ItemStack> stacks;

    public List<ItemStack> getItemStacks() {
        return new ArrayList<>(this.stacks);
    }

    public FallingBlockSupportRegistry() {
        this.stacks = new ArrayList<>();

        if (Configs.FALLING_BLOCK_SUPPORT.getBooleanValue()) {
            for (String value : Configs.FALLING_BLOCK_SUPPORT_BLOCKS.getStrings()) {
                Optional<Block> block = BlockUtils.getBlockFromString(value.trim());
                block.ifPresent(candidate -> {
                    // A support that falls or can be replaced would not hold the falling block up
                    if (!(candidate instanceof FallingBlock) && !candidate.defaultBlockState().canBeReplaced()) {
                        ItemStack stack = new ItemStack(candidate);
                        if (!stack.isEmpty()) {
                            this.stacks.add(stack);
                        }
                    }
                });
            }
        }
    }
}
