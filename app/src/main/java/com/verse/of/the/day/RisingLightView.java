package com.verse.of.the.day;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;

import java.util.Random;

/**
 * Motes of light rising slowly behind the main screen on Easter Sunday, each one gently
 * brightening and fading as it goes. Purely decorative: it is not clickable, so every touch
 * falls through to the views beneath it.
 *
 * <p>Animation is driven from {@link #onDraw}, which re-posts itself. When the window is
 * hidden nothing draws, so the loop stops, and it starts again on the next frame drawn.
 * With system animations turned off (the accessibility "remove animations" setting) the
 * motes are drawn once and stay still.
 */
class RisingLightView extends View {

    private static final int MOTE_COUNT = 45;
    // Cap the step so a long pause (window hidden) doesn't teleport every mote.
    private static final float MAX_STEP_SECONDS = 0.05f;

    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color;
    private final float density;
    private final boolean animate;
    private float[] x, y, radius, speed, drift, phase;
    private long lastFrame;

    RisingLightView(Context context, int color) {
        super(context);
        this.color = color;
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
        x = new float[MOTE_COUNT];
        y = new float[MOTE_COUNT];
        radius = new float[MOTE_COUNT];
        speed = new float[MOTE_COUNT];
        drift = new float[MOTE_COUNT];
        phase = new float[MOTE_COUNT];
        for (int i = 0; i < MOTE_COUNT; i++) {
            x[i] = random.nextFloat() * w;
            y[i] = random.nextFloat() * h;
            radius[i] = (1.5f + random.nextFloat() * 2.5f) * density;
            // Slower than falling snow: the light lifts rather than drops.
            speed[i] = (10f + random.nextFloat() * 14f) * density;
            drift[i] = (4f + random.nextFloat() * 8f) * density;
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
        int baseAlpha = Color.alpha(color);
        for (int i = 0; i < MOTE_COUNT; i++) {
            y[i] -= speed[i] * dt;
            if (y[i] + radius[i] < 0) {
                y[i] = h + radius[i];
                x[i] = random.nextFloat() * w;
            }
            float sway = (float) Math.sin(t * 0.5f + phase[i]) * drift[i];
            float glow = 0.55f + 0.45f * (float) Math.sin(t * 1.3f + phase[i] * 2f);
            paint.setColor(color);
            paint.setAlpha((int) (baseAlpha * glow));
            canvas.drawCircle(x[i] + sway, y[i], radius[i], paint);
        }
        if (animate) postInvalidateOnAnimation();
    }
}
