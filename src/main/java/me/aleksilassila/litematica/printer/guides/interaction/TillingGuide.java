package me.aleksilassila.litematica.printer.guides.interaction;

import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.guides.placement.FarmlandGuide;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;

public class TillingGuide extends InteractionGuide {
    public static final Item[] HOE_ITEMS = new Item[]{
            Items.NETHERITE_HOE,
            Items.DIAMOND_HOE,
            Items.GOLDEN_HOE,
            Items.IRON_HOE,
            Items.COPPER_HOE,
            Items.STONE_HOE,
            Items.WOODEN_HOE
    };

    public TillingGuide(SchematicBlockState state) {
        super(state);
    }

    public static boolean isTillable(BlockState state) {
        return Arrays.stream(FarmlandGuide.TILLABLE_BLOCKS).anyMatch(state::is);
    }

    @Override
    public boolean canExecute(LocalPlayer player) {
        if (!super.canExecute(player) || !isTillable(currentState))
            return false;

        // HoeItem only tills with air above, except rooted dirt
        return currentState.is(Blocks.ROOTED_DIRT) || state.world.getBlockState(state.blockPos.above()).isAir();
    }

    @Override
    protected @Nonnull List<ItemStack> getRequiredItems() {
        return Arrays.stream(HOE_ITEMS).map(ItemStack::new).toList();
    }
}
