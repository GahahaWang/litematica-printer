package me.aleksilassila.litematica.printer.gui;

import cc.gahaha.fastmultiversion.Mc;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.function.Supplier;
import me.aleksilassila.litematica.printer.LitematicaMixinMod;
import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.PrinterReference;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * Draws the blocks {@link me.aleksilassila.litematica.printer.utils.WaterFillPlanner} plans to fill with a bucket,
 * through walls, like Litematica's schematic verifier.
 */
public class PlannedBucketRenderer implements IRenderer {
    private static final float LINE_WIDTH = 2.0f;
    private static final float SIDES_ALPHA = 0.25f;
    private static final double EXPAND = 0.002;
    private static final Supplier<String> OUTLINES = () -> PrinterReference.MOD_ID + ":planned_buckets/outlines";
    private static final Supplier<String> SIDES = () -> PrinterReference.MOD_ID + ":planned_buckets/sides";

    // malilib's hook for Minecraft 26.1.2 and 26.2...
    @Mc.Is("[26.1.2,26.3)")
    @Override
    public void onRenderWorldLast(RenderTarget fb, Matrix4fc modelViewMatrix, CameraRenderState cameraState, Frustum culling,
                                  RenderBuffers buffers, com.mojang.blaze3d.buffers.GpuBufferSlice terrainFog, Vector4f fogColor,
                                  ProfilerFiller profiler) {
        render(profiler);
    }

    // ...and since 26.3: no model view matrix, and Minecraft's render API is in com.mojang.renderpearl.
    @Mc.Is("[26.3,)")
    @Override
    public void onRenderWorldLast(RenderTarget fb, CameraRenderState cameraState, Frustum culling, RenderBuffers buffers,
                                  com.mojang.renderpearl.api.buffers.GpuBufferSlice terrainFog, Vector4f fogColor,
                                  ProfilerFiller profiler) {
        render(profiler);
    }

    private void render(ProfilerFiller profiler) {
        if (!Configs.FLUID_MINIMIZE_BUCKETS.getBooleanValue() || !Configs.FLUID_MINIMIZE_RENDER.getBooleanValue()
                || LitematicaMixinMod.printer == null) {
            return;
        }
        LongList positions = LitematicaMixinMod.printer.waterFillPlanner.getPlannedBuckets();
        if (positions.isEmpty()) {
            return;
        }

        Color4f color = Configs.FLUID_MINIMIZE_RENDER_COLOR.getColor();
        Color4f sidesColor = new Color4f(color.r, color.g, color.b, color.a * SIDES_ALPHA);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        profiler.push("planned_buckets");
        try (RenderContext ctx = outlines()) {
            BufferBuilder buffer = ctx.getBuilder();
            for (int i = 0; i < positions.size(); i++) {
                RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(pos.set(positions.getLong(i)), color, EXPAND, LINE_WIDTH, buffer);
            }
            draw(ctx, buffer, true);

            buffer = startSides(ctx);
            for (int i = 0; i < positions.size(); i++) {
                pos.set(positions.getLong(i));
                RenderUtils.renderAreaSidesBatched(pos, pos, sidesColor, EXPAND, buffer);
            }
            draw(ctx, buffer, false);
        } catch (Exception e) {
            Printer.logger.error("PlannedBucketRenderer: draw failed", e);
        }
        profiler.pop();
    }

    // malilib's RenderContext takes a buffer size since 0.29 (Minecraft 26.2). The 26.2 and 26.3 branches read
    // the same, but are compiled against different pipeline classes: 26.3 moved Minecraft's RenderPipeline to
    // com.mojang.renderpearl.

    /** A context drawing the outlines through walls. */
    private static RenderContext outlines() {
        if (Mc.is("[26.3,)")) {
            return new RenderContext(OUTLINES, MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL, 0);
        }
        if (Mc.is("[26.2,)")) {
            return new RenderContext(OUTLINES, MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL, 0);
        }
        return new RenderContext(OUTLINES, MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL);
    }

    /** Starts drawing the translucent sides through walls in {@code ctx}. */
    private static BufferBuilder startSides(RenderContext ctx) {
        if (Mc.is("[26.3,)")) {
            return ctx.start(SIDES, MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL, 0);
        }
        if (Mc.is("[26.2,)")) {
            return ctx.start(SIDES, MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL, 0);
        }
        return ctx.start(SIDES, MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL);
    }

    private static void draw(RenderContext ctx, BufferBuilder buffer, boolean setColor) {
        MeshData meshData = buffer.build();
        if (meshData != null) {
            ctx.draw(meshData, false, setColor);
            meshData.close();
        }
    }

    @Override
    public Supplier<String> getProfilerSectionSupplier() {
        return () -> PrinterReference.MOD_ID + "_planned_buckets";
    }
}
