package me.aleksilassila.litematica.printer.guides.placement;

import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.guides.Guide;
import me.aleksilassila.litematica.printer.implementation.PrinterPlacementContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Optional;

public class BlockReplacementGuide extends PlacementGuide {
    private static final HashMap<IntegerProperty, Item> increasingProperties = new HashMap<>();

    static {
        increasingProperties.put(SnowLayerBlock.LAYERS, null);
        increasingProperties.put(SeaPickleBlock.PICKLES, null);
        increasingProperties.put(CandleBlock.CANDLES, null);
    }

    private Integer currentLevel = null;
    private Integer targetLevel = null;
    private IntegerProperty increasingProperty = null;

    public BlockReplacementGuide(SchematicBlockState state) {
        super(state);

        for (IntegerProperty property : increasingProperties.keySet()) {
            if (targetState.hasProperty(property) && currentState.hasProperty(property)) {
                currentLevel = currentState.getValue(property);
                targetLevel = targetState.getValue(property);
                increasingProperty = property;
                break;
            }
        }
    }

    @Override
    protected boolean getUseShift(SchematicBlockState state) {
        return false;
    }

    @Override
    public @Nullable PrinterPlacementContext getPlacementContext(LocalPlayer player) {
        Optional<ItemStack> requiredItem = getRequiredItem(player);
        int slot = getRequiredItemStackSlot(player);
        if (requiredItem.isEmpty() || slot == -1) return null;

        SlabType currentSlabType = Guide.getProperty(currentState, SlabBlock.TYPE).orElse(null);
        SlabType targetSlabType  = Guide.getProperty(targetState,  SlabBlock.TYPE).orElse(null);
        Direction hitSide = Direction.UP;
        if (targetSlabType == SlabType.DOUBLE && currentSlabType == SlabType.TOP) {
            hitSide = Direction.DOWN;
        }

        BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(state.blockPos), hitSide, state.blockPos, false);
        return new PrinterPlacementContext(player, hitResult, requiredItem.get(), slot);
    }


    @Override
    public boolean canExecute(LocalPlayer player) {

        if (Guide.getProperty(targetState, SlabBlock.TYPE).orElse(null) == SlabType.DOUBLE
                && Guide.getProperty(currentState, SlabBlock.TYPE).orElse(SlabType.DOUBLE) != SlabType.DOUBLE) {
            if (!playerHasRightItem(player)) return false;
            if (statesEqual(targetState, currentState)) return false;
            PrinterPlacementContext ctx = getPlacementContext(player);
            if (ctx == null || !ctx.canPlace()) return false;
            BlockState resultState = getRequiredItemAsBlock(player)
                    .orElse(targetState.getBlock())
                    .getStateForPlacement(ctx);
            return resultState != null && resultState.canSurvive(state.world, state.blockPos);
        }

        if (currentLevel == null || targetLevel == null || increasingProperty == null) return false;
        if (!statesEqualIgnoreProperties(currentState, targetState, CandleBlock.LIT, increasingProperty)) return false;
        if (currentLevel >= targetLevel) return false;

        return super.canExecute(player);
    }
}
