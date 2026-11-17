package me.aleksilassila.litematica.printer.guides.interaction;

import cc.gahaha.fastmultiversion.Mc;
import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.mixin.AxeItemAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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

    public static final Map<Block, Block> STRIPPED_BLOCKS = Mc.is("[26.3,)") ? strippedByName() : AxeItemAccessor.getStrippedBlocks();
    // Reverse Map
    public static final Map<Block, Block> STRIPPED_TO_LOG =
            STRIPPED_BLOCKS.entrySet()
                    .stream()
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey),
                            Collections::unmodifiableMap
                    ));

    /**
     * Since 26.3, axes strip with a block transformer component instead of AxeItem's map. Every block they strip
     * has its stripped form named stripped_&lt;name&gt; (oak_log and stripped_oak_log, bamboo_block and
     * stripped_bamboo_block, ...), so the map follows from the block names.
     */
    @Mc.Is("[26.3,)")
    private static Map<Block, Block> strippedByName() {
        Map<Block, Block> stripped = new HashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (id.getPath().startsWith("stripped_")) {
                BuiltInRegistries.BLOCK.getOptional(id.withPath(id.getPath().substring("stripped_".length())))
                        .ifPresent(log -> stripped.put(log, block));
            }
        }
        return Collections.unmodifiableMap(stripped);
    }

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
