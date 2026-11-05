package me.aleksilassila.litematica.printer.gui;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
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

    @Override
    public void onRenderWorldLast(RenderTarget fb, Matrix4fc modelViewMatrix, CameraRenderState cameraState, Frustum culling,
                                  RenderBuffers buffers, GpuBufferSlice terrainFog, Vector4f fogColor, ProfilerFiller profiler) {
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
        try (RenderContext ctx = new RenderContext(() -> PrinterReference.MOD_ID + ":planned_buckets/outlines",
                MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL, 0)) {
            BufferBuilder buffer = ctx.getBuilder();
            for (int i = 0; i < positions.size(); i++) {
                RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(pos.set(positions.getLong(i)), color, EXPAND, LINE_WIDTH, buffer);
            }
            draw(ctx, buffer, true);

            buffer = ctx.start(() -> PrinterReference.MOD_ID + ":planned_buckets/sides",
                    MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL, 0);
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
