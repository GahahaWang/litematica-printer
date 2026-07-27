package me.aleksilassila.litematica.printer.guides.interaction;

import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer.ITask;
import me.aleksilassila.litematica.printer.utils.BedrockMinerCompact;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;

import static fi.dy.masa.litematica.config.Configs.Visuals.IGNORE_EXISTING_BLOCKS;

public class BedrockMinerGuide extends BreakBlockGuide {
    public BedrockMinerGuide(SchematicBlockState state) {
        super(state);
    }
    private ITask task = null;

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
        if (Configs.AVOID_BREAKING_UNDER_PLAYER.getBooleanValue() && state.blockPos.equals(player.blockPosition().below())) {
            return false;
        }
        if (IGNORE_EXISTING_BLOCKS.getBooleanValue() && getIgnoreBlockRegistry().hasBlock(state.currentState.getBlock())) {
            return false;
        }
        boolean bl = Configs.BREAKER_USE_BEDROCK_MINER.getBooleanValue() &&
                BedrockMinerCompact.isExecutionEnvironmentValid() &&
                BedrockMinerCompact.canAcceptBedrockMinerTask();
        if (!bl) return false;
        this.task = BedrockMinerCompact.isValidNewTask((ClientLevel)player.level(), currentState.getBlock(), state.blockPos);
        return this.task != null && !BedrockMinerCompact.willTaskEffectOthersTask(this.task);
    }

    @Override
    public @NonNull List<Action> execute(LocalPlayer player) {
        BedrockMinerCompact.addTask(task);
        return Collections.emptyList();
    }
}
