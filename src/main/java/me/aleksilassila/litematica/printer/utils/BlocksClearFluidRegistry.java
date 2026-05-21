package me.aleksilassila.litematica.printer.utils;

import fi.dy.masa.litematica.util.BlockUtils;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BlocksClearFluidRegistry {
    private final List<ItemStack> stacks;

    public List<ItemStack> getItemStacks() {
        return new ArrayList<>(this.stacks);
    }

    public boolean isEmpty() {
        return this.stacks.isEmpty();
    }

    public BlocksClearFluidRegistry() {
        this.stacks = new ArrayList<>();

        if (Configs.CLEAR_FLUID.getBooleanValue()) {
            for (String value : Configs.BLOCKS_CLEAR_FLUID.getStrings()) {
                String trimmed = value.trim();
                Optional<Block> block = BlockUtils.getBlockFromString(trimmed);
                block.ifPresent(candidate -> {
                    BlockState state = candidate.defaultBlockState();
                    if (!state.isAir() && state.getFluidState().isEmpty() && !state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                        ItemStack stack = new ItemStack(candidate);
                        if (!stack.isEmpty() && !stack.is(Items.AIR)) {
                            this.stacks.add(stack);
                        }
                    }
                });
            }
        }
    }
}
