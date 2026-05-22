package me.aleksilassila.litematica.printer.guides.interaction;

import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.utils.BedrockMinerCompact;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.NonNull;

import java.util.List;

import static fi.dy.masa.litematica.config.Configs.Visuals.IGNORE_EXISTING_BLOCKS;

public class BedrockMinerGuide extends BreakBlockGuide {
    public BedrockMinerGuide(SchematicBlockState state) {
        super(state);
    }

    @Override
    public boolean canExecute(LocalPlayer player) {
        if (!isBreakerAllowed()) {
            return false;
        }
        if (statesEqual(targetState, currentState)) {
            return false;
        }
        if (currentState.isAir()) {
            return false;
        }
        if (IGNORE_EXISTING_BLOCKS.getBooleanValue() && getIgnoreBlockRegistry().hasBlock(state.currentState.getBlock())) {
            return false;
        }

        return  Configs.BREAKER_USE_BEDROCK_MINER.getBooleanValue() &&
                BedrockMinerCompact.isBedrockMinerAvailable() &&
                BedrockMinerCompact.isWorking() &&
                player.level() instanceof ClientLevel clientLevel &&
                BedrockMinerCompact.isGoodNewTask(clientLevel, currentState.getBlock(), state.blockPos);
    }

    @Override
    public @NonNull List<Action> execute(LocalPlayer player) {
        BedrockMinerCompact.addTask(currentState.getBlock(), state.blockPos, (ClientLevel)player.level());
        return List.of();
    }

    @Override
    public boolean skipOtherGuides() {
        return false;
    }
}
