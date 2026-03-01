package me.aleksilassila.litematica.printer.guides.placement;

import fi.dy.masa.litematica.world.WorldSchematic;
import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.guides.Guide;
import me.aleksilassila.litematica.printer.utils.SmartRedstoneHelper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Guide for smart redstone placement to avoid QC, BUD, and observer issues
 */
public class SmartRedstoneGuide extends Guide {
    private Boolean shouldSkipCache = null;
    
    public SmartRedstoneGuide(SchematicBlockState state) {
        super(state);
    }

    @Override
    public @Nonnull List<Action> execute(LocalPlayer player) {
        // This guide only checks and skips, doesn't execute actions
        return new ArrayList<>();
    }

    @Override
    protected @Nonnull List<ItemStack> getRequiredItems() {
        // Smart redstone guide doesn't require any specific items
        // It only checks conditions and decides whether to skip placement
        return new ArrayList<>();
    }

    @Override
    public boolean canExecute(LocalPlayer player) {
        if (!Configs.SMART_REDSTONE_ENABLED.getBooleanValue()) {
            return false;
        }

        if (player == null) {
            return false;
        }

        Block targetBlock = state.targetState.getBlock();
        Level clientWorld = player.level();
        WorldSchematic schematicWorld = state.schematic;
        BlockPos pos = state.blockPos;
        
        boolean shouldSkip = checkSmartRedstoneConditions(targetBlock, clientWorld, schematicWorld, pos);
        shouldSkipCache = shouldSkip;
        
        // Return true to indicate this guide handles the situation (by skipping)
        return shouldSkip;
    }
    
    private boolean checkSmartRedstoneConditions(Block targetBlock, Level clientWorld, WorldSchematic schematicWorld, BlockPos pos) {
        // Check Redstone Block for QC
        if (targetBlock instanceof PoweredBlock) {
            return SmartRedstoneHelper.isQCable(clientWorld, schematicWorld, pos);
        }
        
        // Check TNT for power
        else if (targetBlock instanceof TntBlock) {
            return SmartRedstoneHelper.isTntPowered(clientWorld, pos);
        }
        
        // Check Piston
        else if (targetBlock instanceof PistonBaseBlock) {
            // Check QC state
            if (!SmartRedstoneHelper.shouldExtendQC(clientWorld, schematicWorld, pos)) {
                return true;
            }
            
            // Check nearby redstone dust redirection
            if (SmartRedstoneHelper.hasNearbyRedirectDust(clientWorld, schematicWorld, pos)) {
                return true;
            }
            
            // Check if piston will extend unexpectedly
            if (SmartRedstoneHelper.cantAvoidExtend(clientWorld, schematicWorld, pos)) {
                return true;
            }
            
            // Check BUD state
            if (SmartRedstoneHelper.shouldSuppressExtend(schematicWorld, pos) && 
                SmartRedstoneHelper.hasWrongStateNearby(clientWorld, schematicWorld, pos)) {
                return true;
            }
            
            // Check push limit
            if (Configs.SUPPRESS_PUSH_LIMIT.getBooleanValue()) {
                BlockState schematicState = schematicWorld.getBlockState(pos);
                boolean willExtend = SmartRedstoneHelper.willExtendInWorld(schematicWorld, pos, schematicState.getValue(PistonBaseBlock.FACING));
                boolean isExtended = schematicState.getValue(PistonBaseBlock.EXTENDED);
                
                if (willExtend != isExtended && 
                    SmartRedstoneHelper.directlyPowered(schematicWorld, pos, schematicState.getValue(PistonBaseBlock.FACING))) {
                    return true;
                }
            }
        }
        
        // Check Observer
        else if (targetBlock instanceof ObserverBlock) {
            return SmartRedstoneHelper.observerUpdateOrder(clientWorld, schematicWorld, pos);
        }
        
        return false;
    }

    @Override
    public boolean skipOtherGuides() {
        if (!Configs.SMART_REDSTONE_ENABLED.getBooleanValue()) {
            return false;
        }
        
        // If already evaluated by canExecute, use cached result
        if (shouldSkipCache != null) {
            return shouldSkipCache;
        }
        
        // Otherwise, check if this is a redstone-related block that we care about
        Block targetBlock = state.targetState.getBlock();
        return targetBlock instanceof PoweredBlock || 
               targetBlock instanceof TntBlock ||
               targetBlock instanceof PistonBaseBlock || 
               targetBlock instanceof ObserverBlock;
    }
}
