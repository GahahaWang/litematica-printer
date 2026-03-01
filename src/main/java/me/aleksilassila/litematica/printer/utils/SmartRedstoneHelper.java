package me.aleksilassila.litematica.printer.utils;

import fi.dy.masa.litematica.world.WorldSchematic;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/**
 * Helper class for smart redstone checks to avoid QC, BUD, and observer update order issues
 */
public class SmartRedstoneHelper {
    
    /**
     * Check if a redstone block placement would cause QC (Quasi-Connectivity)
     */
    public static boolean isQCable(Level clientWorld, WorldSchematic schematicWorld, BlockPos pos) {
        BlockPos downPos = pos.below();
        Direction[] horizontals = {Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH};
        
        for (Direction direction : horizontals) {
            BlockPos offsetPos = downPos.relative(direction);
            BlockState stateClient = clientWorld.getBlockState(offsetPos);
            BlockState stateSchematic = schematicWorld.getBlockState(offsetPos);
            
            if (!(stateSchematic.getBlock() instanceof PistonBaseBlock)) {
                continue;
            }
            
            if (stateSchematic.getValue(PistonBaseBlock.EXTENDED)) {
                continue;
            }
            
            if (stateClient.isAir()) {
                return true;
            } else if (!hasNoUpdatableState(clientWorld, schematicWorld, offsetPos)) {
                return true;
            } else if (stateClient.getBlock() instanceof PistonBaseBlock && 
                       stateSchematic.getValue(PistonBaseBlock.FACING).equals(Direction.UP)) {
                if (!schematicWorld.getBlockState(offsetPos.above()).getBlock()
                    .equals(clientWorld.getBlockState(offsetPos.above()).getBlock())) {
                    return true;
                }
            }
        }
        
        BlockState stateSchematic = schematicWorld.getBlockState(downPos.below());
        return stateSchematic.getBlock() instanceof PistonBaseBlock && 
               !stateSchematic.getValue(PistonBaseBlock.EXTENDED) && 
               !schematicWorld.getBlockState(downPos).getBlock()
                   .equals(clientWorld.getBlockState(downPos).getBlock());
    }
    
    private static boolean hasNoUpdatableState(Level clientWorld, WorldSchematic schematicWorld, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos offsetPos = pos.relative(direction);
            BlockState schematicState = schematicWorld.getBlockState(offsetPos);
            BlockState clientState = clientWorld.getBlockState(offsetPos);
            
            if (!schematicState.equals(clientState)) {
                if (schematicState.isAir() && clientState.isAir()) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check if nearby redstone dust has incorrect redirection
     */
    public static boolean hasNearbyRedirectDust(Level clientWorld, WorldSchematic schematicWorld, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!isCorrectDustState(clientWorld, schematicWorld, pos.relative(direction))) {
                return true;
            }
            
            if (direction.getAxis() != Direction.Axis.Y && 
                !isCorrectDustState(clientWorld, schematicWorld, pos.relative(direction, 2))) {
                return true;
            }
            
            if (!isCorrectDustState(clientWorld, schematicWorld, pos.relative(direction).above())) {
                return true;
            }
            
            if (direction.getAxis() != Direction.Axis.Y && 
                !isCorrectDustState(clientWorld, schematicWorld, pos.relative(direction, 2).above())) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean isCorrectDustState(Level clientWorld, WorldSchematic schematicWorld, BlockPos pos) {
        BlockState clientState = clientWorld.getBlockState(pos);
        BlockState schematicState = schematicWorld.getBlockState(pos);
        
        if (!(schematicState.getBlock() instanceof RedStoneWireBlock)) {
            return true;
        }
        
        if (!(clientState.getBlock() instanceof RedStoneWireBlock)) {
            return false;
        }
        
        return schematicState.getValue(RedStoneWireBlock.EAST) == clientState.getValue(RedStoneWireBlock.EAST) &&
               schematicState.getValue(RedStoneWireBlock.WEST) == clientState.getValue(RedStoneWireBlock.WEST) &&
               schematicState.getValue(RedStoneWireBlock.SOUTH) == clientState.getValue(RedStoneWireBlock.SOUTH) &&
               schematicState.getValue(RedStoneWireBlock.NORTH) == clientState.getValue(RedStoneWireBlock.NORTH) &&
               Objects.equals(schematicState.getValue(RedStoneWireBlock.POWER) == 0, 
                            clientState.getValue(RedStoneWireBlock.POWER) == 0);
    }
    
    /**
     * Check if piston will extend unexpectedly
     */
    public static boolean cantAvoidExtend(Level clientWorld, WorldSchematic schematicWorld, BlockPos pos) {
        BlockState schematicState = schematicWorld.getBlockState(pos);
        if (!schematicState.getValue(PistonBaseBlock.EXTENDED)) {
            return willExtendInWorld(clientWorld, pos, schematicState.getValue(PistonBaseBlock.FACING));
        }
        return false;
    }
    
    /**
     * Check if piston state matches between client and schematic (QC check)
     */
    public static boolean shouldExtendQC(Level clientWorld, WorldSchematic schematicWorld, BlockPos pos) {
        BlockState schematicState = schematicWorld.getBlockState(pos);
        return willExtendInWorld(clientWorld, pos, schematicState.getValue(PistonBaseBlock.FACING)) == 
               schematicState.getValue(PistonBaseBlock.EXTENDED);
    }
    
    /**
     * Check if piston should be suppressed from extending (BUD)
     */
    public static boolean shouldSuppressExtend(WorldSchematic schematicWorld, BlockPos pos) {
        BlockState state = schematicWorld.getBlockState(pos);
        return willExtendInWorld(schematicWorld, pos, state.getValue(PistonBaseBlock.FACING)) && 
               !state.getValue(PistonBaseBlock.EXTENDED);
    }
    
    /**
     * Check if piston is directly powered by redstone block
     */
    public static boolean directlyPowered(WorldSchematic schematicWorld, BlockPos pos, Direction pistonFace) {
        for (Direction direction : Direction.values()) {
            if (direction == pistonFace) {
                continue;
            }
            if (schematicWorld.getBlockState(pos.relative(direction)).getBlock() instanceof PoweredBlock) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Determine if piston will extend in the given world
     */
    public static boolean willExtendInWorld(Level world, BlockPos pos, Direction pistonFace) {
        // Check horizontal and vertical power sources
        for (Direction direction : Direction.values()) {
            if (direction == pistonFace) {
                continue;
            }
            
            BlockPos adjacentPos = pos.relative(direction);
            int signal = world.getSignal(adjacentPos, direction);
            
            if (signal <= 0) {
                continue;
            }
            
            // Check for observer errors
            boolean hasObserver = false;
            for (Direction dir : Direction.values()) {
                BlockState observerState = world.getBlockState(adjacentPos.relative(dir));
                if (observerState.getBlock() instanceof ObserverBlock) {
                    if (observerState.getValue(ObserverBlock.POWERED)) {
                        hasObserver = true;
                        break;
                    }
                }
            }
            
            BlockState adjState = world.getBlockState(adjacentPos);
            if (adjState.getBlock() instanceof ObserverBlock) {
                if (adjState.getValue(ObserverBlock.POWERED)) {
                    hasObserver = true;
                }
            }
            
            if (hasObserver) {
                continue;
            }
            return true;
        }
        
        // Check direct power from below
        if (world.getSignal(pos, Direction.DOWN) > 0) {
            return true;
        }
        
        // Check QC (Quasi-Connectivity) from above
        BlockPos upPos = pos.above();
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) {
                continue;
            }
            
            BlockPos qcPos = upPos.relative(direction);
            int signal = world.getSignal(qcPos, direction);
            
            if (signal <= 0) {
                continue;
            }
            
            BlockState qcState = world.getBlockState(qcPos);
            if (qcState.getBlock() instanceof ObserverBlock && 
                qcState.getValue(ObserverBlock.FACING) == direction && 
                qcState.getValue(ObserverBlock.POWERED)) {
                continue;
            }
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if observer update order should prevent placement
     */
    public static boolean observerUpdateOrder(Level clientWorld, WorldSchematic schematicWorld, BlockPos pos) {
        if (!Configs.OBSERVER_AVOID_ALL.getBooleanValue()) {
            return false;
        }
        
        BlockState schematicState = schematicWorld.getBlockState(pos);
        if (!(schematicState.getBlock() instanceof ObserverBlock)) {
            return false;
        }
        
        if (schematicState.getValue(ObserverBlock.POWERED)) {
            return false;
        }
        
        Direction facing = schematicState.getValue(ObserverBlock.FACING);
        BlockPos targetPos = pos.relative(facing);
        
        BlockState targetSchematic = schematicWorld.getBlockState(targetPos);
        BlockState targetClient = clientWorld.getBlockState(targetPos);
        
        if (targetSchematic.getBlock() instanceof BarrierBlock) {
            return false;
        }
        
        if (targetSchematic.getBlock() instanceof ObserverBlock && 
            targetSchematic.getValue(ObserverBlock.FACING) == facing.getOpposite()) {
            return false;
        }
        
        if (targetSchematic.isAir() && targetClient.isAir()) {
            return false;
        }
        
        // Check if states don't match
        return !targetSchematic.equals(targetClient);
    }
    
    /**
     * Check if block has wrong state nearby for BUD pistons
     */
    public static boolean hasWrongStateNearby(Level clientWorld, WorldSchematic schematicWorld, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos offsetPos = pos.relative(direction);
            BlockState schematicState = schematicWorld.getBlockState(offsetPos);
            BlockState clientState = clientWorld.getBlockState(offsetPos);
            
            if (!schematicState.equals(clientState)) {
                if (!schematicState.isAir() || !clientState.isAir()) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Check if TNT will be powered when placed
     */
    public static boolean isTntPowered(Level clientWorld, BlockPos pos) {
        return clientWorld.getBestNeighborSignal(pos) > 0;
    }
}
