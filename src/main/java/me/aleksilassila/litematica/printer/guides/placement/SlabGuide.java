package me.aleksilassila.litematica.printer.guides.placement;

import com.mojang.datafixers.util.Pair;
import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.implementation.PrinterPlacementContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class SlabGuide extends GeneralPlacementGuide {
    public SlabGuide(SchematicBlockState state) {
        super(state);
        currentSlabType = getProperty(state.currentState, SlabBlock.TYPE).orElse(null);
        targetSlabType = getProperty(state.targetState, SlabBlock.TYPE).orElse(SlabType.DOUBLE);
        requiredSlabTypes = MISSING_HALF.getOrDefault(new Pair<>(currentSlabType, targetSlabType), List.of());
    }

    @Nullable private final SlabType currentSlabType;
    private final SlabType targetSlabType;
    private final List<SlabType> requiredSlabTypes;
    // Pair<current SlabType, target SlabType>
    private static final Map<Pair<@Nullable SlabType, SlabType>, List<SlabType>> MISSING_HALF =
            Map.of(
                    new Pair<>(SlabType.TOP, SlabType.DOUBLE), List.of(SlabType.BOTTOM),
                    new Pair<>(SlabType.BOTTOM, SlabType.DOUBLE), List.of(SlabType.TOP),
                    new Pair<>(null, SlabType.TOP), List.of(SlabType.TOP),
                    new Pair<>(null, SlabType.BOTTOM), List.of(SlabType.BOTTOM),
                    new Pair<>(null, SlabType.DOUBLE), List.of(SlabType.TOP, SlabType.BOTTOM)
            );

    @Override
    protected List<Direction> getPossibleSides() {
        List<Direction> resultList = new ArrayList<>();

        if (this.targetSlabType == SlabType.DOUBLE) {
            return super.getPossibleSides();
        }

        Direction[] directionsToCheck = {
                Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
        };

        for (Direction direction : directionsToCheck) {
            SlabType neighborSlabType = getProperty(state.offset(direction).currentState, SlabBlock.TYPE).orElse(SlabType.DOUBLE);
            if (neighborSlabType == SlabType.DOUBLE || this.requiredSlabTypes.contains(neighborSlabType)) {
                resultList.add(direction);
            }
        }

        if (this.targetSlabType == SlabType.TOP || this.targetSlabType == SlabType.BOTTOM) {
            Direction verticalDirection = this.targetSlabType == SlabType.TOP ? Direction.UP : Direction.DOWN;
            SlabType neighborSlabType = getProperty(state.offset(verticalDirection).currentState, SlabBlock.TYPE).orElse(SlabType.DOUBLE);

            if (neighborSlabType == SlabType.DOUBLE || neighborSlabType != this.targetSlabType) {
                resultList.add(verticalDirection);
            }
        }

        return resultList;
    }

    @Override
    public PrinterPlacementContext getPlacementContext(LocalPlayer player) {
        if (!Configs.PRINT_IN_AIR.getBooleanValue()) {
            return super.getPlacementContext(player);
        }
        try {
            Optional<ItemStack> requiredItem = getRequiredItem(player);
            int requiredSlot = getRequiredItemStackSlot(player);
            if (requiredItem.isEmpty() || requiredSlot == -1) {
                return null;
            }
            Direction hitSide = Direction.DOWN;
            double yOffset = 0.0;
            if (this.targetSlabType == SlabType.TOP) {
                yOffset = 0.25;
            } else if (this.targetSlabType == SlabType.BOTTOM) {
                hitSide = Direction.UP;
                yOffset = -0.25;
            } else if (this.currentSlabType == SlabType.TOP) {
                yOffset = -0.25;
            } else if (this.currentSlabType == SlabType.BOTTOM) {
                hitSide = Direction.UP;
                yOffset = 0.25;
            }
            Vec3 hitVec = Vec3.atCenterOf(state.blockPos).add(0, yOffset, 0);
            BlockHitResult blockHitResult = new BlockHitResult(hitVec, hitSide, state.blockPos, false);
            return new PrinterPlacementContext(player, blockHitResult, requiredItem.get(), requiredSlot,
                    null, false);
        } catch (Exception e) {
            Printer.logger.error("getPlacementContext(): Exception caught: {}", e.getMessage());
            return null;
        }
    }

    @Override
    protected Vec3 getHitModifier(Direction validSide) {
        if (validSide.get2DDataValue() == -1) {
            return new Vec3(0, 0, 0);
        }

        SlabType neighborSlabType = getProperty(state.offset(validSide).currentState, SlabBlock.TYPE)
                .orElse(SlabType.DOUBLE);
        boolean useTopHalf = neighborSlabType == SlabType.DOUBLE
                ? requiredSlabTypes.getFirst() == SlabType.TOP
                : neighborSlabType == SlabType.TOP;
        return new Vec3(0, useTopHalf ? 0.25 : -0.25, 0);
    }
}
