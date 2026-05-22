package me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer;

import com.github.bunnyi116.bedrockminer.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "com.github.bunnyi116.bedrockminer.config.Config", remap = false)
public interface IConfigMixin {

    @Invoker("getInstance")
    static Config litematica_printer$getInstance() {
        throw new AssertionError();
    }

    @Accessor("disable")
    boolean litematica_printer$disable();

    @Invoker("isAllowBlock")
    boolean litematica_printer$isAllowBlock(Block block);

    @Invoker("isFloorsBlacklist")
    boolean litematica_printer$isFloorsBlacklist(BlockPos pos);
}
