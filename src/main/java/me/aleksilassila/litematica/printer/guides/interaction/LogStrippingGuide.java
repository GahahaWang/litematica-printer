package me.aleksilassila.litematica.printer.guides.interaction;

import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.mixin.AxeItemAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LogStrippingGuide extends InteractionGuide {
    static final Item[] AXE_ITEMS = new Item[]{
            Items.NETHERITE_AXE,
            Items.DIAMOND_AXE,
            Items.GOLDEN_AXE,
            Items.IRON_AXE,
            Items.COPPER_AXE,
            Items.STONE_AXE,
            Items.WOODEN_AXE
    };

    public static final Map<Block, Block> STRIPPED_BLOCKS = AxeItemAccessor.getStrippedBlocks();
    // Reverse Map
    public static final Map<Block, Block> STRIPPED_TO_LOG =
            STRIPPED_BLOCKS.entrySet()
                    .stream()
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey),
                            Collections::unmodifiableMap
                    ));

    public LogStrippingGuide(SchematicBlockState state) {
        super(state);
    }

    /**
     * Whether stripping turns {@code current} into {@code target}, with or without a usable axe right now.
     */
    public static boolean stripsInto(BlockState current, BlockState target) {
        return Configs.STRIP_LOGS.getBooleanValue() && STRIPPED_BLOCKS.get(current.getBlock()) == target.getBlock();
    }

    public boolean hasUsableAxe(LocalPlayer player) {
        return playerHasRightItem(player);
    }

    @Override
    public boolean canExecute(LocalPlayer player) {
        return stripsInto(currentState, targetState) && super.canExecute(player);
    }

    @Override
    protected @Nonnull List<ItemStack> getRequiredItems() {
        return Arrays.stream(AXE_ITEMS).map(ItemStack::new).toList();
    }
}
