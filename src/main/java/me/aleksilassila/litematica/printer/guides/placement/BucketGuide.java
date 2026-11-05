package me.aleksilassila.litematica.printer.guides.placement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.aleksilassila.litematica.printer.LitematicaMixinMod;
import me.aleksilassila.litematica.printer.SchematicBlockState;
import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.actions.PrepareAction;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.guides.Guide;
import me.aleksilassila.litematica.printer.implementation.PrinterPlacementContext;
import me.aleksilassila.litematica.printer.implementation.actions.UseBucketActionImpl;
import me.aleksilassila.litematica.printer.utils.BucketHelper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Empties a bucket of {@link #fluid} into the target, aiming at the first of {@link #getAimPoints} that gets it there.
 */
public abstract class BucketGuide extends Guide {
    protected final Fluid fluid;
    private BlockHitResult aimCache = null;

    public BucketGuide(SchematicBlockState state, Fluid fluid) {
        super(state);
        this.fluid = fluid;
    }

    /**
     * Points to try aiming the bucket at, in order of preference.
     */
    protected abstract List<Vec3> getAimPoints(Vec3 eye);

    @Override
    protected @Nonnull List<ItemStack> getRequiredItems() {
        ItemStack bucket = new ItemStack(fluid.getBucket());
        return bucket.isEmpty() ? Collections.emptyList() : Collections.singletonList(bucket);
    }

    @Override
    public boolean canExecute(LocalPlayer player) {
        if (!Configs.PLACE_FLUIDS.getBooleanValue() || getRequiredItems().isEmpty() || !playerHasRightItem(player)) {
            return false;
        }
        // water boils away there (e.g. in the Nether) and only empties the bucket
        if (fluid.defaultFluidState().is(FluidTags.WATER) && state.world.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, state.blockPos)) {
            return false;
        }
        // left for infinite water
        if (!LitematicaMixinMod.printer.waterFillPlanner.shouldFill(state)) {
            return false;
        }

        return getAim(player) != null;
    }

    private @Nullable BlockHitResult getAim(LocalPlayer player) {
        if (aimCache != null) {
            return aimCache;
        }

        Vec3 eye = player.getEyePosition();
        double rangeSquared = Mth.square(player.blockInteractionRange());

        for (Vec3 aimPoint : getAimPoints(eye)) {
            if (eye.distanceToSqr(aimPoint) > rangeSquared) {
                continue;
            }

            float[] rotation = BucketHelper.getRotation(eye, aimPoint);
            BlockHitResult hit = BucketHelper.getHitFilling(player, fluid, rotation[0], rotation[1], state.blockPos);
            if (hit != null) {
                aimCache = hit;
                return hit;
            }
        }

        return null;
    }

    @Override
    public @Nonnull List<Action> execute(LocalPlayer player) {
        List<Action> actions = new ArrayList<>();
        BlockHitResult aim = getAim(player);
        Optional<ItemStack> bucket = getRequiredItem(player);
        int slot = getRequiredItemStackSlot(player);

        if (aim == null || bucket.isEmpty() || slot == -1) {
            return actions;
        }

        float[] rotation = BucketHelper.getRotation(player.getEyePosition(), aim.getLocation());
        PrinterPlacementContext ctx = new PrinterPlacementContext(player, aim, bucket.get(), slot);
        actions.add(new PrepareAction(ctx, rotation[0], rotation[1]));
        actions.add(new UseBucketActionImpl(ctx, fluid, state.blockPos));

        return actions;
    }
}
