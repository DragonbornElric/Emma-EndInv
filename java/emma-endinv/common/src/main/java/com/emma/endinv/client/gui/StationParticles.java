package com.emma.endinv.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The particles a station block gives off in the world, drawn in the GUI with the vanilla particle
 * textures. Positions are in GUI pixels relative to an origin the owner passes when drawing, with
 * 16 px to a block; motion, lifetime, size and colour follow the 26.2 particle classes:
 * <ul>
 *   <li>smoke: {@code SmokeParticle} (generic_7 down to generic_0, dark grey, drifts and rises)</li>
 *   <li>flame: {@code FlameParticle} (nearly still, shrinks as it ages)</li>
 *   <li>enchanting glyph: {@code FlyTowardsPositionParticle} (an sga_ letter flying in to the book,
 *       dropping onto it at the end)</li>
 * </ul>
 * Ticked 20 times a second from the screen's {@code containerTick}.
 */
final class StationParticles {

    private static final int MAX = 64;
    private static final Identifier FLAME = Identifier.withDefaultNamespace("textures/particle/flame.png");
    private static final Identifier[] SMOKE = new Identifier[8];
    private static final Identifier[] GLYPHS = new Identifier[26];

    static {
        for (int i = 0; i < SMOKE.length; i++) SMOKE[i] = Identifier.withDefaultNamespace("textures/particle/generic_" + i + ".png");
        for (int i = 0; i < GLYPHS.length; i++) GLYPHS[i] = Identifier.withDefaultNamespace("textures/particle/sga_" + (char) ('a' + i) + ".png");
    }

    private enum Kind { SMOKE, FLAME, GLYPH }

    private static final class P {
        Kind kind;
        float x, y, xo, yo, xd, yd;
        float sx, sy, dx, dy;
        int age, lifetime;
        float size;
        int color;
        Identifier texture;
    }

    private final List<P> particles = new ArrayList<>();
    private final RandomSource random = RandomSource.create();

    RandomSource random() {
        return random;
    }

    void clear() {
        particles.clear();
    }

    /** Vanilla smoke at ({@code x}, {@code y}). */
    void smoke(float x, float y) {
        P p = add(Kind.SMOKE, x, y);
        if (p == null) return;
        // Particle's random start velocity, scaled by SmokeParticle's 0.1 direction factor (16 px a block).
        p.xd = (random.nextFloat() * 2f - 1f) * 0.6f;
        p.yd = (random.nextFloat() * 2f - 1f) * 0.6f;
        p.lifetime = Math.max(1, (int) (8 / (random.nextFloat() * 0.8f + 0.2f)));
        p.size = 0.75f * (random.nextFloat() * 0.5f + 0.5f) * 3.2f;
        int c = (int) (random.nextFloat() * 0.3f * 255f);
        p.color = 0xFF000000 | c << 16 | c << 8 | c;
    }

    /** Vanilla flame at ({@code x}, {@code y}). */
    void flame(float x, float y) {
        P p = add(Kind.FLAME, x + (random.nextFloat() - random.nextFloat()) * 0.8f, y + (random.nextFloat() - random.nextFloat()) * 0.8f);
        if (p == null) return;
        p.xd = (random.nextFloat() * 2f - 1f) * 0.06f;
        p.yd = (random.nextFloat() * 2f - 1f) * 0.06f;
        p.lifetime = (int) (8 / (random.nextFloat() * 0.8f + 0.2f)) + 4;
        p.size = (random.nextFloat() * 0.5f + 0.5f) * 3.2f;
        p.color = 0xFFFFFFFF;
    }

    /** An enchanting glyph flying from ({@code fromX}, {@code fromY}) to ({@code toX}, {@code toY}). */
    void glyph(float fromX, float fromY, float toX, float toY) {
        // Vanilla aims at a point above the table and drops the glyph 1.2 blocks onto it at the end.
        float drop = 8f;
        P p = add(Kind.GLYPH, fromX, fromY);
        if (p == null) return;
        p.sx = toX;
        p.sy = toY - drop;
        p.dx = fromX - toX;
        p.dy = fromY - p.sy;
        p.lifetime = (int) (random.nextFloat() * 10f) + 30;
        p.size = 2f + random.nextFloat();
        float br = random.nextFloat() * 0.6f + 0.4f;
        int rg = (int) (0.9f * br * 255f), b = (int) (br * 255f);
        p.color = 0xFF000000 | rg << 16 | rg << 8 | b;
        p.texture = GLYPHS[random.nextInt(GLYPHS.length)];
    }

    private P add(Kind kind, float x, float y) {
        if (particles.size() >= MAX) return null;
        P p = new P();
        p.kind = kind;
        p.x = p.xo = x;
        p.y = p.yo = y;
        particles.add(p);
        return p;
    }

    void tick() {
        for (Iterator<P> it = particles.iterator(); it.hasNext(); ) {
            P p = it.next();
            p.xo = p.x;
            p.yo = p.y;
            if (p.age++ >= p.lifetime) {
                it.remove();
                continue;
            }
            switch (p.kind) {
                case SMOKE -> {
                    // gravity -0.1: yd += 0.004 blocks a tick upwards; friction 0.96.
                    p.yd -= 0.064f;
                    p.x += p.xd;
                    p.y += p.yd;
                    p.xd *= 0.96f;
                    p.yd *= 0.96f;
                }
                case FLAME -> {
                    p.x += p.xd;
                    p.y += p.yd;
                    p.xd *= 0.96f;
                    p.yd *= 0.96f;
                }
                case GLYPH -> {
                    float pos = 1f - (float) p.age / p.lifetime;
                    float pp = 1f - pos;
                    pp *= pp;
                    pp *= pp;
                    p.x = p.sx + p.dx * pos;
                    p.y = p.sy + p.dy * pos + pp * 8f;
                }
            }
        }
    }

    void extract(GuiGraphicsExtractor graphics, int originX, int originY, float partialTick) {
        if (particles.isEmpty()) return;
        graphics.nextStratum();
        for (P p : particles) {
            float t = Mth.clamp((p.age + partialTick) / p.lifetime, 0f, 1f);
            Identifier texture;
            float size = p.size;
            switch (p.kind) {
                case SMOKE -> {
                    // SmokeParticle's sprite list runs generic_7 to generic_0 over its life.
                    texture = SMOKE[7 - Math.min(7, (int) (t * 8f))];
                    size *= Mth.clamp(t * 32f, 0f, 1f);
                }
                case FLAME -> {
                    texture = FLAME;
                    size *= 1f - t * t * 0.5f;
                }
                default -> texture = p.texture;
            }
            int s = Math.max(1, Math.round(size * 2f));
            float x = Mth.lerp(partialTick, p.xo, p.x);
            float y = Mth.lerp(partialTick, p.yo, p.y);
            int px = originX + Math.round(x - s / 2f);
            int py = originY + Math.round(y - s / 2f);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, px, py, 0f, 0f, s, s, 8, 8, 8, 8, p.color);
        }
    }
}
