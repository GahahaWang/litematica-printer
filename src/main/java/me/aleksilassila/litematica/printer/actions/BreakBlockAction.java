package me.aleksilassila.litematica.printer.actions;

import me.aleksilassila.litematica.printer.utils.Breaker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

public class BreakBlockAction extends Action {
    private final BlockPos pos;
    private final Breaker breaker;

    public BreakBlockAction(BlockPos pos, Breaker breaker) {
        this.pos = pos;
        this.breaker = breaker;
    }

    @Override
    public void send(Minecraft client, LocalPlayer player) {
        if (client.gameMode == null) {
            return;
        }
        breaker.startBreakingBlock(pos);
    }

    @Override
    public String toString() {
        return "BreakBlockAction{pos=" + pos + '}';
    }
}
