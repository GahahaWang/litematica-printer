package me.aleksilassila.litematica.printer.actions;

import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.utils.Breaker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

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

        // For creative mode, instantly break the block
        if (player.getAbilities().instabuild) {
            client.gameMode.startDestroyBlock(pos, Direction.UP);
            Printer.printDebug("BreakBlockAction: Instantly broke block at {}", pos);
        } else {
            // For survival mode, start the breaking process
            boolean started = breaker.startBreakingBlock(pos);
            if (started) {
                Printer.printDebug("BreakBlockAction: Started breaking block at {}", pos);
            } else {
                Printer.printDebug("BreakBlockAction: Instantly broke block at {} (easy break)", pos);
            }
        }
    }

    @Override
    public String toString() {
        return "BreakBlockAction{pos=" + pos + '}';
    }
}
