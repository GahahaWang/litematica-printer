package me.aleksilassila.litematica.printer.guides.interaction;

import fi.dy.masa.litematica.config.Hotkeys;
import fi.dy.masa.litematica.util.IgnoreBlockRegistry;
import me.aleksilassila.litematica.printer.LitematicaMixinMod;
import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.actions.BreakBlockAction;
import me.aleksilassila.litematica.printer.config.BreakerOption;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.guides.Guide;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static fi.dy.masa.litematica.config.Configs.Visuals.IGNORE_EXISTING_BLOCKS;

/**
 * A guide that breaks blocks that need to be removed before placing the correct block.
 */
public class BreakBlockGuide extends Guide {
    private static final long IGNORE_BLOCK_REGISTRY_REFRESH_INTERVAL_MS = 1000L;
    private IgnoreBlockRegistry ignoreBlockRegistry = new IgnoreBlockRegistry();
    private long ignoreBlockRegistryLastRefreshMs = System.currentTimeMillis();
    private static final HashSet<Block> STRICK_IGNORELIST = getDefaultBlockBlacklistServer();

    public BreakBlockGuide(SchematicBlockState state) {
        super(state);
    }

    protected IgnoreBlockRegistry getIgnoreBlockRegistry() {
        long now = System.currentTimeMillis();
        if (now - ignoreBlockRegistryLastRefreshMs >= IGNORE_BLOCK_REGISTRY_REFRESH_INTERVAL_MS) {
            ignoreBlockRegistry = new IgnoreBlockRegistry();
            ignoreBlockRegistryLastRefreshMs = now;
        }
        return ignoreBlockRegistry;
    }

    @Override
    public boolean canExecute(LocalPlayer player) {
        if (!isBreakerAllowed()) {
            return false;
        }

        //if (ClearFluidGuide.shouldKeepFillBlock(state)) {
        //    return false;
        //}

        if (statesEqual(targetState, currentState)) {
            return false;
        }

        if (currentState.isAir()) {
            return false;
        }

        // if state current block is in ignorable existing blocks do not break
        if (IGNORE_EXISTING_BLOCKS.getBooleanValue() && getIgnoreBlockRegistry().hasBlock(state.currentState.getBlock())) {
            return false;
        }

        // Skip certain blocks
        if (STRICK_IGNORELIST.contains(currentState.getBlock())) { return false; }

        // Determine if breaking is needed
        boolean needsBreaking = !currentState.getBlock().getName().equals(targetState.getBlock().getName());
        // Special case for slabs - need to break if the type is wrong
        if (!needsBreaking &&
            currentState.getBlock() instanceof SlabBlock &&
            targetState.getBlock() instanceof SlabBlock) {
            needsBreaking = isUnrepealableSlabState();
        }

        return needsBreaking;
    }

    protected boolean isBreakerAllowed() {
        if (!Configs.BREAK_BLOCKS.getBooleanValue()) {
            return false;
        }
        BreakerOption option = (BreakerOption) Configs.BREAKER_OPTION.getOptionListValue();
        return option == BreakerOption.AUTO || Hotkeys.EASY_PLACE_ACTIVATION.getKeybind().isPressed();
    }

    public boolean isUnrepealableSlabState() {
        SlabType currentType = currentState.getValue(SlabBlock.TYPE);
        SlabType targetType = targetState.getValue(SlabBlock.TYPE);
        if (targetType == SlabType.DOUBLE) return false;
        else if (currentType == SlabType.DOUBLE) {
            return targetType == SlabType.TOP || targetType == SlabType.BOTTOM;
        }
        else if (currentType == SlabType.TOP) {
            return targetType == SlabType.BOTTOM;
        }
        else if (currentType == SlabType.BOTTOM) {
            return targetType == SlabType.TOP;
        }
        return false;
    }

    @Override
    public @Nonnull List<Action> execute(LocalPlayer player) {
        List<Action> actions = new ArrayList<>();
        actions.add(new BreakBlockAction(state.blockPos, LitematicaMixinMod.breaker));
        return actions;
    }

    @Override
    protected @Nonnull List<ItemStack> getRequiredItems() {
        return new ArrayList<>();
    }

    @Override
    public boolean skipOtherGuides() {
        return false;
    }

    private static HashSet<Block> getDefaultBlockBlacklistServer() {
        // Default block blacklist (applied on remote servers, separate from the user blacklist)
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
