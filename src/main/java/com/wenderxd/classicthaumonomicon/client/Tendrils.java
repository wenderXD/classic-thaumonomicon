package com.wenderxd.classicthaumonomicon.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The curved lines between research entries, built the way Thaumcraft 4's map builds them.
 *
 * <p>A tendril is a chain of short steps from the child toward the parent. On the axis where the
 * two are furthest apart, the first step is doubled and every later one is a little shorter, and
 * each point is placed at "step size times steps taken" from the child. The chain bows outward and
 * ends almost exactly on the parent. On the other axis it runs straight.
 *
 * <p>An unfinished link also waves (most at the child, not at all at the parent), fades in along
 * its length and darkens toward its end. A finished link is plain and still.
 *
 * <p>Every tendril on the map is collected into one batch of quads and drawn in a single call.
 * As separate rectangles they would be thousands of tiny fills a frame.
 */
final class Tendrils {
    /**
     * How far a waving tendril moves off its path. Thaumcraft 4 used five pixels on lines three
     * screen pixels thick. At one GUI pixel thick that looks too busy, so this is the smaller value
     * Thaumaturge's Legacy uses for the same artwork.
     */
    private static final float WAVE_AMPLITUDE = 2.5F;
    private static final float HALF_THICKNESS = 0.5F;
    private static final float ALPHA = 0.6F;

    private float[] xs = new float[1024];
    private float[] ys = new float[1024];
    private int[] colours = new int[1024];
    private int vertices;

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

    /** Draws everything added so far as plain coloured GUI quads and starts over. */
    void submit(GuiGraphics graphics) {
        if (vertices == 0) {
            return;
        }
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < vertices; i++) {
            consumer.addVertex(pose, xs[i], ys[i], 0.0F).setColor(colours[i]);
        }
        graphics.flush();
        vertices = 0;
    }

    /**
     * Turns a run of points into a strip one pixel wide. Neighbouring quads share their edge at
     * each point, perpendicular to the line there, so the joints have no gaps or overlaps. Each edge
     * takes its point's colour, which shades the strip along its length like the old line strip.
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
}
