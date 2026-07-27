package me.aleksilassila.litematica.printer;

import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.actions.InteractAction;
import me.aleksilassila.litematica.printer.actions.PrepareAction;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import java.util.*;

import static me.aleksilassila.litematica.printer.Printer.printDebug;

public class ActionHandler {
    private final Minecraft client;
    private final LocalPlayer player;
    private final Queue<Action> actionQueue = new LinkedList<>();
    public PrepareAction lookAction = null;

    public ActionHandler(Minecraft client, LocalPlayer player) {
        this.client = client;
        this.player = player;
    }

    private int tick = 0;

    /** Paper-like server can process PlayerMove immidiately.
     *  while vanilla/fabric etc. must wait for a tick.
     *  because they only process "the first" PlayerMove in a tick.
     * @see LocalPlayer#tick() this.sendPosition()
     * <pre>
     * else {
     *     this.sendPosition();
     * }
     * </pre>
     */
    public void onGameTick() {
        int tickRate = Configs.PRINTING_INTERVAL.getIntegerValue();
        tick = tick % tickRate == tickRate - 1 ? 0 : tick + 1;

        if (tick % tickRate != 0) {
            return;
        }

        Action nextAction = actionQueue.poll();

        while (nextAction != null) {
            boolean isPrepareAction = nextAction instanceof PrepareAction;
            if (isPrepareAction) {
                lookAction = (PrepareAction) nextAction;
            }
            printDebug("Sending action {}", nextAction);
            nextAction.send(client, player);
            if (isPrepareAction && !isPaperLikeServer()) {
                return;
            }
            nextAction = actionQueue.poll();
        }
        lookAction = null;
    }

    private static final Set<String> PAPER_BRANDS = Set.of(
            "paper",
            "purpur",
            "pufferfish",
            "leaf",
            "gale",
            "folia"
    );

    private boolean isPaperLikeServer() {
        if (client.getConnection() == null) {
            return false;
        }

        String brand = client.getConnection().serverBrand();
        return brand != null && PAPER_BRANDS.contains(brand.toLowerCase(Locale.ROOT));
    }

    public boolean isQueueEmpty() {
        return actionQueue.isEmpty();
    }

    public boolean acceptsActions() {
        return actionQueue.size() <= 1;
    }
    public boolean acceptsActions(BlockPos pos) {
        return actionQueue.size() <= 1 && !isPositionPending(pos);
    }

    public boolean addActions(List<Action> actions) {
        if (LitematicaMixinMod.breaker.isBreakingBlock()) {
            return false;
        }

        if (!acceptsActions()) {
            return false;
        }

        for (Action action : actions) {
            BlockPos pos = getActionBlockPos(action);
            if (pos != null && isPositionPending(pos)) {
                return false;
            }
        }

//        for (Action action : actions) {
//            if (action instanceof PrepareAction) {
//                lookAction = (PrepareAction) action;
//                break;
//            }
//        }

        actionQueue.addAll(actions);
        return true;
    }

    private boolean isPositionPending(BlockPos pos) {
        for (Action queued : actionQueue) {
            if (pos.equals(getActionBlockPos(queued))) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos getActionBlockPos(Action action) {
        if (action instanceof InteractAction interactAction) {
            return interactAction.context.hitResult.getBlockPos();
        }
        if (action instanceof PrepareAction prepareAction) {
            return prepareAction.context.hitResult.getBlockPos();
        }
//        if (action instanceof BreakBlockAction breakBlockAction) {
//            return breakBlockAction.getPos();
//        }
        return null;
    }
}
