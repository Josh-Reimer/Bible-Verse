package com.verse.of.the.day;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class FabRadialAnimator {
    private static final int ANIMATION_DURATION = 300;
    private static final float RADIUS = 330f;
    private static final float DEGREE_STEP = 30f;

    private final FloatingActionButton centerFab;
    private final FloatingActionButton[] secondaryFabs;
    private final float centerX;
    private final float centerY;

    public FabRadialAnimator(FloatingActionButton centerFab, FloatingActionButton... fabs) {
        this.centerFab = centerFab;
        this.secondaryFabs = fabs;
        this.centerX = 0;
        this.centerY = 0;
    }

    public void showFabs() {
        for (int i = 0; i < secondaryFabs.length; i++) {
            FloatingActionButton fab = secondaryFabs[i];
            fab.setAlpha(0);
            fab.setVisibility(View.VISIBLE);

            float startAngle = 90f - ((secondaryFabs.length - 1) * DEGREE_STEP) / 2f;
            float angle = startAngle + (i * DEGREE_STEP);
            float radians = (float) Math.toRadians(angle);
            float endX = (float) (RADIUS * Math.cos(radians));
            float endY = (float) (-RADIUS * Math.sin(radians));

            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(fab, "translationX", centerX, endX),
                    ObjectAnimator.ofFloat(fab, "translationY", centerY, endY),
                    ObjectAnimator.ofFloat(fab, "alpha", 0, 1),
                    ObjectAnimator.ofFloat(fab, "scaleX", 0.5f, 1f),
                    ObjectAnimator.ofFloat(fab, "scaleY", 0.5f, 1f)
            );
            set.setDuration(ANIMATION_DURATION);
            set.start();
        }
    }

    public void hideFabs() {
        for (FloatingActionButton fab : secondaryFabs) {
            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(fab, "translationX", fab.getTranslationX(), centerX),
                    ObjectAnimator.ofFloat(fab, "translationY", fab.getTranslationY(), centerY),
                    ObjectAnimator.ofFloat(fab, "alpha", 1, 0),
                    ObjectAnimator.ofFloat(fab, "scaleX", 1f, 0.5f),
                    ObjectAnimator.ofFloat(fab, "scaleY", 1f, 0.5f)
            );
            set.setDuration(ANIMATION_DURATION);
            set.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    fab.setVisibility(View.INVISIBLE);
                }
            });
            set.start();
        }
    }
}
