package me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer;

import com.github.bunnyi116.bedrockminer.task.Scheme;
import com.github.bunnyi116.bedrockminer.task.SchemeBlock;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value = Scheme.class, remap = false)
public interface IScheme {
    @Accessor("direction")
    Direction litematica_printer$getDirection();

    @Accessor("piston")
    SchemeBlock litematica_printer$getPiston();

    @Accessor("redstoneTorch")
    SchemeBlock litematica_printer$getRedstoneTorch();

    @Accessor("slimeBlock")
    SchemeBlock litematica_printer$getSlimeBlock();

}
