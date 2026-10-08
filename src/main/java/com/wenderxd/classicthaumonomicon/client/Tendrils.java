package com.wenderxd.classicthaumonomicon.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * The lines Thaumcraft 4's map joins research with: not ruled connectors but tendrils that reach
 * out of an entry toward the one it follows from.
 *
 * <p>A tendril is a chain of short steps from the child toward the parent. Along the axis the two
 * are furthest apart on, the first step is doubled and every later one a little shorter, and each
 * point is placed at "step size times steps taken" from the child, so the chain bows outward and
 * comes to rest almost exactly on the parent. Across the other axis it runs straight. That bow is
 * the curve the old book's trees are drawn with.
 *
 * <p>An unfinished link also waves, most at the child and not at all at the parent, and fades in
 * along its length while darkening toward its end, so it reads in the direction the research has to
 * be worked through. A finished link is drawn plain and still.
 *
 * <p>Every tendril on the map goes into one ribbon of quads, submitted as a single GUI element.
 * Drawn as separate rectangles they would be thousands of tiny overlapping elements a frame, and
 * the GUI stacks each element that overlaps another on a layer of its own.
 */
final class Tendrils {
    /**
     * How far a waving tendril strays from its path. Thaumcraft 4 used five pixels on lines three
     * screen pixels thick; at one GUI pixel thick that reads as a much busier ripple, so this is the
     * calmer figure Thaumaturge's Legacy settled on for the same artwork.
     */
    private static final float WAVE_AMPLITUDE = 2.5F;
    private static final float HALF_THICKNESS = 0.5F;
    private static final float ALPHA = 0.6F;

    private float[] xs = new float[1024];
    private float[] ys = new float[1024];
    private int[] colours = new int[1024];
    private int vertices;
    private float minX = Float.MAX_VALUE;
    private float minY = Float.MAX_VALUE;
    private float maxX = -Float.MAX_VALUE;
    private float maxY = -Float.MAX_VALUE;

    // Scratch for one tendril's points.
    private float[] px = new float[64];
    private float[] py = new float[64];
    private int[] pc = new int[64];

    /**
     * Adds one tendril from ({@code x}, {@code y}) toward ({@code x2}, {@code y2}).
     *
     * @param rgb the colour at the near end, without alpha
     * @param wiggle whether the tendril waves and fades, as an unfinished link does
     * @param time the animation clock, in ticks with the partial tick added
     */
    void add(float x, float y, float x2, float y2, int rgb, boolean wiggle, float time) {
        float dx = x - x2;
        float dy = y - y2;
        int steps = (int) (Mth.sqrt(dx * dx + dy * dy) / 2.0F);
        if (steps < 1) {
            return;
        }
        boolean alongX = Math.abs(dx) > Math.abs(dy);
        float stepX = dx / steps * (alongX ? 2.0F : 1.0F);
        float stepY = dy / steps * (alongX ? 1.0F : 2.0F);
        float decay = 1.0F - 1.0F / (steps * 1.5F);
        ensurePoints(steps + 1);
        for (int step = 0; step <= steps; step++) {
            float phase = step / (float) steps;
            float pointX = x - stepX * step;
            float pointY = y - stepY * step;
            if (wiggle) {
                pointX += Mth.sin((time + step) / 7.0F) * WAVE_AMPLITUDE * (1.0F - phase);
                pointY += Mth.sin((time + step) / 5.0F) * WAVE_AMPLITUDE * (1.0F - phase);
            }
            px[step] = pointX;
            py[step] = pointY;
            pc[step] = wiggle ? argb(ALPHA * phase, scale(rgb, 1.0F - phase)) : argb(ALPHA, rgb);
            if (alongX) {
                stepX *= decay;
            } else {
                stepY *= decay;
            }
        }
        ribbon(steps + 1);
    }

    /** Hands everything added so far to the GUI as one element, and starts afresh. */
    void submit(GuiGraphicsExtractor graphics) {
        if (vertices == 0) {
            return;
        }
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissor = graphics.peekScissorStack();
        int left = Mth.floor(minX);
        int top = Mth.floor(minY);
        ScreenRectangle bounds = new ScreenRectangle(left, top, Mth.ceil(maxX) - left + 1, Mth.ceil(maxY) - top + 1).transformMaxBounds(pose);
        if (scissor != null) {
            bounds = scissor.intersection(bounds);
        }
        if (bounds != null) {
            graphics.submitGuiElementRenderState(new Ribbon(Arrays.copyOf(xs, vertices), Arrays.copyOf(ys, vertices), Arrays.copyOf(colours, vertices), pose, scissor, bounds));
        }
        vertices = 0;
        minX = minY = Float.MAX_VALUE;
        maxX = maxY = -Float.MAX_VALUE;
    }

    /**
     * Turns a run of points into a ribbon one pixel wide. Neighbouring quads share their edge at
     * each point, set square to the line's direction through it, so the ribbon has no gaps or
     * overlaps at its joints, and each edge takes its point's colour, so it shades smoothly along
     * its length as the old line strip did.
     */
    private void ribbon(int points) {
        ensureVertices(vertices + (points - 1) * 4);
        float prevNx = 0.0F;
        float prevNy = 0.0F;
        for (int i = 0; i < points; i++) {
            int before = Math.max(0, i - 1);
            int after = Math.min(points - 1, i + 1);
            float dirX = px[after] - px[before];
            float dirY = py[after] - py[before];
            float length = Mth.sqrt(dirX * dirX + dirY * dirY);
            float nx = length < 1.0E-4F ? prevNx : -dirY / length * HALF_THICKNESS;
            float ny = length < 1.0E-4F ? prevNy : dirX / length * HALF_THICKNESS;
            if (i > 0) {
                int a = i - 1;
                // Corners in the order a plain GUI rectangle lists them.
                vertex(px[a] - prevNx, py[a] - prevNy, pc[a]);
                vertex(px[a] + prevNx, py[a] + prevNy, pc[a]);
                vertex(px[i] + nx, py[i] + ny, pc[i]);
                vertex(px[i] - nx, py[i] - ny, pc[i]);
            }
            prevNx = nx;
            prevNy = ny;
        }
    }

    private void vertex(float x, float y, int colour) {
        xs[vertices] = x;
        ys[vertices] = y;
        colours[vertices] = colour;
        vertices++;
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
    }

    private void ensurePoints(int count) {
        if (px.length < count) {
            int size = Math.max(count, px.length * 2);
            px = Arrays.copyOf(px, size);
            py = Arrays.copyOf(py, size);
            pc = Arrays.copyOf(pc, size);
        }
    }

    private void ensureVertices(int count) {
        if (xs.length < count) {
            int size = Math.max(count, xs.length * 2);
            xs = Arrays.copyOf(xs, size);
            ys = Arrays.copyOf(ys, size);
            colours = Arrays.copyOf(colours, size);
        }
    }

    private static int argb(float alpha, int rgb) {
        return Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24 | rgb;
    }

    private static int scale(int rgb, float factor) {
        int r = (int) ((rgb >> 16 & 0xFF) * factor);
        int g = (int) ((rgb >> 8 & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return r << 16 | g << 8 | b;
    }

    /** Every tendril's quads, as one coloured, untextured GUI element. */
    private record Ribbon(float[] xs, float[] ys, int[] colours, Matrix3x2fc pose, @Nullable ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (int i = 0; i < xs.length; i++) {
                consumer.addVertexWith2DPose(pose, xs[i], ys[i]).setColor(colours[i]);
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return RenderPipelines.GUI;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }
    }
}
