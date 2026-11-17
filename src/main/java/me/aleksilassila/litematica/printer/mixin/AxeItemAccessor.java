package me.aleksilassila.litematica.printer.mixin;

import cc.gahaha.fastmultiversion.Mc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import net.minecraft.world.level.block.Block;

/**
 * This class apparently fixes an issue with Quilt.
 *
 * <p>AxeItem is gone in 26.3, where stripping is a block transformer component of the axes
 * ({@link me.aleksilassila.litematica.printer.guides.interaction.LogStrippingGuide} finds the stripped blocks
 * by name there). So the target is a name, which compiles in every version, and {@code @Pseudo} lets Mixin
 * skip this accessor quietly where the class does not exist.
 */
@Pseudo
@Mixin(targets = "net.minecraft.world.item.AxeItem")
public interface AxeItemAccessor {
    @Mc.Is("[26.1.2,26.3)")
    @Accessor("STRIPPABLES")
    static Map<Block, Block> getStrippedBlocks() {
        throw new AssertionError("Untransformed @Accessor");
    }
}
