package me.aleksilassila.litematica.printer.multiversion;

import cc.gahaha.fastmultiversion.Mc;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.phys.Vec3;

/**
 * What differs between the Minecraft versions the printer is built for (26.1.2, 26.2 and 26.3), and between
 * the malilib and Litematica versions for them. Each version's build keeps only its own branch of each
 * {@code Mc.is}. Where both branches read the same, the code is the same but means something else in each
 * version, e.g. a method that returns another class.
 */
public final class Multiversion {
    private Multiversion() {
    }

    // Redstone dust: RedStoneWireBlock until 26.2, RedstoneWireBlock since 26.3.
    public static final Class<? extends Block> REDSTONE_WIRE = Mc.is("[26.3,)") ? RedstoneWireBlock.class : RedStoneWireBlock.class;
    public static final EnumProperty<RedstoneSide> WIRE_NORTH = Mc.is("[26.3,)") ? RedstoneWireBlock.NORTH : RedStoneWireBlock.NORTH;
    public static final EnumProperty<RedstoneSide> WIRE_EAST = Mc.is("[26.3,)") ? RedstoneWireBlock.EAST : RedStoneWireBlock.EAST;
    public static final EnumProperty<RedstoneSide> WIRE_SOUTH = Mc.is("[26.3,)") ? RedstoneWireBlock.SOUTH : RedStoneWireBlock.SOUTH;
    public static final EnumProperty<RedstoneSide> WIRE_WEST = Mc.is("[26.3,)") ? RedstoneWireBlock.WEST : RedStoneWireBlock.WEST;
    public static final IntegerProperty WIRE_POWER = Mc.is("[26.3,)") ? RedstoneWireBlock.POWER : RedStoneWireBlock.POWER;

    /** {@code BlockStateBase#blocksMotion}, which 26.3 dropped: solid, and neither a cobweb nor a bamboo sapling. */
    @SuppressWarnings("deprecation") // isSolid, which blocksMotion itself uses
    public static boolean blocksMotion(BlockState state) {
        if (Mc.is("[26.3,)")) {
            return !state.is(Blocks.COBWEB) && !state.is(Blocks.BAMBOO_SAPLING) && state.isSolid();
        }
        return state.blocksMotion();
    }

    /** {@code Entity#calculateViewVector}, which is static since 26.3. */
    public static Vec3 viewVector(Entity entity, float pitch, float yaw) {
        if (Mc.is("[26.3,)")) {
            return Entity.calculateViewVector(pitch, yaw);
        }
        return entity.calculateViewVector(pitch, yaw);
    }

}
