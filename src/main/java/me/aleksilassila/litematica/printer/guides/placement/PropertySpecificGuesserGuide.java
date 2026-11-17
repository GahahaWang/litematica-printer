package me.aleksilassila.litematica.printer.guides.placement;

import cc.gahaha.fastmultiversion.Mc;
import me.aleksilassila.litematica.printer.multiversion.Multiversion;
import me.aleksilassila.litematica.printer.SchematicBlockState;

import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

public class PropertySpecificGuesserGuide extends GuesserGuide {
    protected static Property<?>[] ignoredProperties = new Property[]{
            RepeaterBlock.DELAY,
            ComparatorBlock.MODE,
            Multiversion.WIRE_POWER,
            Multiversion.WIRE_EAST,
            Multiversion.WIRE_NORTH,
            Multiversion.WIRE_SOUTH,
            Multiversion.WIRE_WEST,
            BlockStateProperties.POWERED,
            BlockStateProperties.OPEN,
            Mc.is("[26.2,)") ? SpeleothemBlock.THICKNESS : PointedDripstoneBlock.THICKNESS,
            ScaffoldingBlock.DISTANCE,
            ScaffoldingBlock.BOTTOM,
            CactusBlock.AGE,
            BambooStalkBlock.AGE,
            BambooStalkBlock.LEAVES,
            BambooStalkBlock.STAGE,
            SaplingBlock.STAGE,
            CrossCollisionBlock.EAST,
            CrossCollisionBlock.NORTH,
            CrossCollisionBlock.SOUTH,
            CrossCollisionBlock.WEST,
            SnowLayerBlock.LAYERS,
            SeaPickleBlock.PICKLES,
            CandleBlock.CANDLES,
            FlowerBedBlock.AMOUNT,
            EndPortalFrameBlock.HAS_EYE,
            BlockStateProperties.LIT,
            LeavesBlock.DISTANCE,
            LeavesBlock.PERSISTENT,
            BlockStateProperties.ATTACHED,
            BlockStateProperties.NOTE,
            BlockStateProperties.NOTEBLOCK_INSTRUMENT,
            BlockStateProperties.EXTENDED, // Piston
            BlockStateProperties.ENABLED,
            BlockStateProperties.TRIGGERED,
            WallBlock.UP,
            WallBlock.EAST,
            WallBlock.SOUTH,
            WallBlock.WEST,
            WallBlock.NORTH,
            WallBlock.WATERLOGGED,
            ComposterBlock.LEVEL,
            BedBlock.OCCUPIED,
            FenceGateBlock.IN_WALL,
            SculkSensorBlock.PHASE,
            SculkShriekerBlock.CAN_SUMMON,
            SculkShriekerBlock.SHRIEKING,
            LecternBlock.HAS_BOOK,
            ShelfBlock.SIDE_CHAIN_PART,
            JukeboxBlock.HAS_RECORD,
            DecoratedPotBlock.CRACKED,
            HugeMushroomBlock.EAST,
            HugeMushroomBlock.WEST,
            HugeMushroomBlock.NORTH,
            HugeMushroomBlock.SOUTH,
            HugeMushroomBlock.DOWN,
            HugeMushroomBlock.UP

    };

    public PropertySpecificGuesserGuide(SchematicBlockState state) {
        super(state);
    }

    @Override
    protected boolean statesEqual(BlockState resultState, BlockState targetState) {
        return statesEqualIgnoreProperties(resultState, targetState, ignoredProperties);
    }
}
