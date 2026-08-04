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
import me.aleksilassila.litematica.printer.guides.placement.ClearFluidGuide;
import me.aleksilassila.litematica.printer.utils.BreakBlockListRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static fi.dy.masa.litematica.config.Configs.Visuals.IGNORE_EXISTING_BLOCKS;

/**
 * A guide that breaks blocks that need to be removed before placing the correct block.
 */
public class BreakBlockGuide extends Guide {
    private static final long IGNORE_BLOCK_REGISTRY_REFRESH_INTERVAL_MS = 1000L;
    private IgnoreBlockRegistry ignoreBlockRegistry = new IgnoreBlockRegistry();
    private long ignoreBlockRegistryLastRefreshMs = System.currentTimeMillis();
    private BreakBlockListRegistry breakBlockListRegistry = new BreakBlockListRegistry();
    private long breakBlockListRegistryLastRefreshMs = System.currentTimeMillis();

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

    protected BreakBlockListRegistry getBreakBlockListRegistry() {
        long now = System.currentTimeMillis();
        if (now - breakBlockListRegistryLastRefreshMs >= IGNORE_BLOCK_REGISTRY_REFRESH_INTERVAL_MS) {
            breakBlockListRegistry = new BreakBlockListRegistry();
            breakBlockListRegistryLastRefreshMs = now;
        }
        return breakBlockListRegistry;
    }

    @Override
    public boolean canExecute(LocalPlayer player) {
        if (!isBreakerAllowed()) {
            return false;
        }

        if (!LitematicaMixinMod.printer.actionHandler.isQueueEmpty()) {
            return false;
        }

        if (Configs.AVOID_BREAKING_UNDER_PLAYER.getBooleanValue() && state.blockPos.equals(player.blockPosition().below())) {
            return false;
        }

        if (Configs.CLEAR_FLUID.getBooleanValue() && targetState.canBeReplaced() && ClearFluidGuide.wouldReformSource(state)) {
            return false;
        }

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

        // Skip default-ignored blocks, plus the user's blacklist/whitelist
        if (!getBreakBlockListRegistry().isBreakable(currentState.getBlock())) { return false; }

        // Determine if breaking is needed
        boolean needsBreaking = !currentState.getBlock().equals(targetState.getBlock());

        NeedBreak:
        if (needsBreaking) {
            // FlowerPotFillGuide
            if (currentState.is(Blocks.FLOWER_POT) && targetState.getBlock() instanceof FlowerPotBlock){
                needsBreaking = false;
                break NeedBreak;
            }
            // LogStrippingGuide
            if (new LogStrippingGuide(state).canExecute(player)) {
                needsBreaking = false;
                break NeedBreak;
            }
            // Wrong Slab
            if(currentState.getBlock() instanceof SlabBlock && targetState.getBlock() instanceof SlabBlock) {
                needsBreaking = isUnrepealableSlabState();
                break NeedBreak;
            }
        }

        return needsBreaking;
    }

    public static boolean isBreakerAllowed() {
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
//        List<Action> actions = new ArrayList<>();
//        actions.add(new BreakBlockAction(state.blockPos, LitematicaMixinMod.breaker));
//        return actions;
        new BreakBlockAction(state.blockPos, LitematicaMixinMod.breaker).send(Minecraft.getInstance(), player);
        return Collections.emptyList();
    }

    @Override
    protected @Nonnull List<ItemStack> getRequiredItems() {
        return new ArrayList<>();
    }

    @Override
    public boolean isBreakGuide() {
        return true;
    }
}
