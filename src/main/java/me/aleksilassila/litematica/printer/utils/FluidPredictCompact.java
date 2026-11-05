package me.aleksilassila.litematica.printer.utils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import me.aleksilassila.litematica.printer.Printer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import org.jspecify.annotations.Nullable;

/**
 * Runs Fluid Predict's {@code FluidSimulation} when the mod is installed. It is reached through reflection so that the
 * printer neither needs it to build nor to run.
 */
public final class FluidPredictCompact {
    private static final String FLUID_PREDICT_MOD_ID = "fluidpredict";
    private static final String SIM_PACKAGE = "cc.gahaha.fluidpredict.sim.";
    private static boolean available = false;
    private static MethodHandle newRules;
    private static MethodHandle newLimits;
    private static MethodHandle newSimulation;
    private static MethodHandle placeFluid;
    private static MethodHandle waterlog;
    private static MethodHandle run;
    private static MethodHandle isFinished;
    private static MethodHandle status;
    private static MethodHandle world;

    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded(FLUID_PREDICT_MOD_ID)) {
            Printer.logger.warn("Fluid Predict not found, water plans won't be checked by simulation");
            return;
        }

        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> rulesClass = Class.forName(SIM_PACKAGE + "FluidRules");
            Class<?> limitsClass = Class.forName(SIM_PACKAGE + "FluidSimulation$Limits");
            Class<?> simulationClass = Class.forName(SIM_PACKAGE + "FluidSimulation");

            newRules = lookup.findConstructor(rulesClass, MethodType.methodType(void.class, Level.class, ServerLevel.class));
            newLimits = lookup.findConstructor(limitsClass, MethodType.methodType(void.class, int.class, int.class, int.class, long.class, long.class));
            newSimulation = lookup.findConstructor(simulationClass, MethodType.methodType(void.class, Level.class, BlockPos.class, rulesClass, limitsClass));
            placeFluid = lookup.findVirtual(simulationClass, "placeFluid", MethodType.methodType(void.class, BlockPos.class, FlowingFluid.class));
            waterlog = lookup.findVirtual(simulationClass, "waterlog", MethodType.methodType(void.class, BlockPos.class, FlowingFluid.class));
            run = lookup.findVirtual(simulationClass, "run", MethodType.methodType(void.class, long.class));
            isFinished = lookup.findVirtual(simulationClass, "isFinished", MethodType.methodType(boolean.class));
            status = lookup.findVirtual(simulationClass, "status", MethodType.methodType(Class.forName(SIM_PACKAGE + "FluidSimulation$Status")));
            world = lookup.findVirtual(simulationClass, "world", MethodType.methodType(Class.forName(SIM_PACKAGE + "SimLevel")));
            available = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            Printer.logger.warn("Fluid Predict found but its simulation can't be used, water plans won't be checked by simulation", e);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    private static void disable(Throwable e) {
        Printer.logger.warn("Fluid Predict simulation failed, water plans won't be checked by simulation anymore", e);
        available = false;
    }

    /**
     * Starts a simulation of {@code level} that may change blocks up to {@code radius} blocks around {@code origin},
     * or returns null if Fluid Predict can't be used.
     */
    public static @Nullable Simulation create(Level level, BlockPos origin, int radius, int maxChangedBlocks) {
        if (!available) {
            return null;
        }

        try {
            // without a server level the game rule defaults are used: water turns into sources
            Object rules = newRules.invoke(level, (ServerLevel) null);
            Object limits = newLimits.invoke(radius, maxChangedBlocks, 400_000, 24_000L, 2_000_000_000L);
            return new Simulation(newSimulation.invoke(level, origin, rules, limits));
        } catch (Throwable e) {
            disable(e);
            return null;
        }
    }

    public static final class Simulation {
        private final Object simulation;
        private boolean failed = false;

        private Simulation(Object simulation) {
            this.simulation = simulation;
        }

        /** Empties a bucket of {@code fluid} into {@code pos}. */
        public void placeFluid(BlockPos pos, FlowingFluid fluid) {
            try {
                placeFluid.invoke(simulation, pos, fluid);
            } catch (Throwable e) {
                fail(e);
            }
        }

        /** Empties a bucket of {@code fluid} into the waterloggable block at {@code pos}. */
        public void waterlog(BlockPos pos, FlowingFluid fluid) {
            try {
                waterlog.invoke(simulation, pos, fluid);
            } catch (Throwable e) {
                fail(e);
            }
        }

        /**
         * Advances the simulation for about {@code budgetNanos}, returns whether it is done.
         */
        public boolean run(long budgetNanos) {
            if (failed) {
                return true;
            }
            try {
                run.invoke(simulation, budgetNanos);
                return (boolean) isFinished.invoke(simulation);
            } catch (Throwable e) {
                fail(e);
                return true;
            }
        }

        /** Whether the fluid settled, so that {@link #world()} shows the final state. */
        public boolean isSettled() {
            if (failed) {
                return false;
            }
            try {
                return ((Enum<?>) status.invoke(simulation)).name().equals("SETTLED");
            } catch (Throwable e) {
                fail(e);
                return false;
            }
        }

        /** The simulated level, unchanged positions read through to the real one. */
        public @Nullable BlockGetter world() {
            if (failed) {
                return null;
            }
            try {
                return (BlockGetter) world.invoke(simulation);
            } catch (Throwable e) {
                fail(e);
                return null;
            }
        }

        private void fail(Throwable e) {
            failed = true;
            disable(e);
        }
    }
}
