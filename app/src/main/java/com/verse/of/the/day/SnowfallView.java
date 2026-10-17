package com.verse.of.the.day;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;

import java.util.Random;

/**
 * Gentle falling snow drawn behind the main screen on Christmas Day. Purely decorative:
 * it is not clickable, so every touch falls through to the views beneath it.
 *
 * <p>Animation is driven from {@link #onDraw}, which re-posts itself — when the window is
 * hidden nothing draws, so the loop stops, and it picks up again on the next frame drawn.
 * With system animations turned off (the accessibility "remove animations" setting) the
 * flakes are drawn once and stay still.
 */
class SnowfallView extends View {

    private static final int FLAKE_COUNT = 70;
    // Cap the step so a long pause (window hidden) doesn't teleport every flake.
    private static final float MAX_STEP_SECONDS = 0.05f;

    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final boolean animate;
    private float[] x, y, radius, speed, drift, phase;
    private long lastFrame;

    SnowfallView(Context context, int color) {
        super(context);
        paint.setColor(color);
        density = getResources().getDisplayMetrics().density;
        animate = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        x = new float[FLAKE_COUNT];
        y = new float[FLAKE_COUNT];
        radius = new float[FLAKE_COUNT];
        speed = new float[FLAKE_COUNT];
        drift = new float[FLAKE_COUNT];
        phase = new float[FLAKE_COUNT];
        for (int i = 0; i < FLAKE_COUNT; i++) {
            x[i] = random.nextFloat() * w;
            y[i] = random.nextFloat() * h;
            radius[i] = (1.5f + random.nextFloat() * 2.5f) * density;
            // Bigger flakes fall faster, which reads as nearer.
            speed[i] = (18f + radius[i] / density * 10f) * density;
            drift[i] = (8f + random.nextFloat() * 14f) * density;
            phase[i] = random.nextFloat() * 6.28f;
        }
        lastFrame = SystemClock.uptimeMillis();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (x == null) return;
        long now = SystemClock.uptimeMillis();
        float dt = animate ? Math.min((now - lastFrame) / 1000f, MAX_STEP_SECONDS) : 0f;
        lastFrame = now;
        int w = getWidth(), h = getHeight();
        float t = now / 1000f;
        for (int i = 0; i < FLAKE_COUNT; i++) {
            y[i] += speed[i] * dt;
            if (y[i] - radius[i] > h) {
                y[i] = -radius[i];
                x[i] = random.nextFloat() * w;
            }
            float sway = (float) Math.sin(t * 0.8f + phase[i]) * drift[i];
            canvas.drawCircle(x[i] + sway, y[i], radius[i], paint);
        }
        if (animate) postInvalidateOnAnimation();
    }
}
