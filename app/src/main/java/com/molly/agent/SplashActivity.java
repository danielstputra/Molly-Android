package com.molly.agent;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.molly.agent.api.MollyApiClient;
import com.molly.agent.intro.IntroActivity;

import java.util.Random;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final int NAVIGATE_DELAY_MS = 2800;

    private View vSplashGlow;
    private View vLogoRing;
    private ImageView ivSplashLogo;
    private LinearLayout llSplashContent;
    private TextView tvSplashTitle;
    private TextView tvSplashTagline;
    private LinearLayout llLoadingDots;
    private TextView tvSplashVersion;

    private View[] particles;
    private View vLoadDot1, vLoadDot2, vLoadDot3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isAnimating = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Security check
        if (!com.molly.agent.security.AppSecurityGuard.enforceSecurity(this)) {
            return;
        }

        bindViews();
        startSplashAnimation();

        handler.postDelayed(this::navigateNext, NAVIGATE_DELAY_MS);
    }

    private void bindViews() {
        vSplashGlow = findViewById(R.id.vSplashGlow);
        vLogoRing = findViewById(R.id.vLogoRing);
        ivSplashLogo = findViewById(R.id.ivSplashLogo);
        llSplashContent = findViewById(R.id.llSplashContent);
        tvSplashTitle = findViewById(R.id.tvSplashTitle);
        tvSplashTagline = findViewById(R.id.tvSplashTagline);
        llLoadingDots = findViewById(R.id.llLoadingDots);
        tvSplashVersion = findViewById(R.id.tvSplashVersion);
        if (tvSplashVersion != null) {
            tvSplashVersion.setText("v" + com.molly.agent.update.AppUpdateManager.getCurrentVersionName(this));
        }
        vLoadDot1 = findViewById(R.id.vLoadDot1);
        vLoadDot2 = findViewById(R.id.vLoadDot2);
        vLoadDot3 = findViewById(R.id.vLoadDot3);

        particles = new View[]{
                findViewById(R.id.vParticle1),
                findViewById(R.id.vParticle2),
                findViewById(R.id.vParticle3),
                findViewById(R.id.vParticle4),
                findViewById(R.id.vParticle5),
                findViewById(R.id.vParticle6)
        };
    }

    private void startSplashAnimation() {
        // Phase 1 (0ms): Glow fades in + expands
        animateGlow();

        // Phase 2 (200ms): Content fades in + slides up
        handler.postDelayed(this::animateContent, 200);

        // Phase 3 (500ms): Logo pulse
        handler.postDelayed(this::animateLogoPulse, 500);

        // Phase 4 (600ms): Ring expand
        handler.postDelayed(this::animateRing, 600);

        // Phase 5 (700ms): Title + tagline fade in with slide
        handler.postDelayed(this::animateTextElements, 700);

        // Phase 6 (900ms): Particles scatter
        handler.postDelayed(this::animateParticles, 900);

        // Phase 7 (1100ms): Loading dots + version
        handler.postDelayed(this::animateBottomElements, 1100);

        // Phase 8 (1300ms+): Loading dot pulse loop
        handler.postDelayed(this::startLoadingDotLoop, 1300);
    }

    // ─── Glow ───────────────────────────────────────────────────────────────
    private void animateGlow() {
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(vSplashGlow, "alpha", 0f, 0.85f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(vSplashGlow, "scaleX", 0.4f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(vSplashGlow, "scaleY", 0.4f, 1.0f);

        AnimatorSet glowSet = new AnimatorSet();
        glowSet.playTogether(fadeIn, scaleX, scaleY);
        glowSet.setDuration(900);
        glowSet.setInterpolator(new DecelerateInterpolator(2f));
        glowSet.start();

        // Gentle breathing pulse after reveal
        handler.postDelayed(() -> {
            if (!isAnimating) return;
            ObjectAnimator breatheX = ObjectAnimator.ofFloat(vSplashGlow, "scaleX", 1.0f, 1.08f, 1.0f);
            breatheX.setRepeatCount(ValueAnimator.INFINITE);
            breatheX.setDuration(2000);
            breatheX.setInterpolator(new AccelerateDecelerateInterpolator());

            ObjectAnimator breatheY = ObjectAnimator.ofFloat(vSplashGlow, "scaleY", 1.0f, 1.08f, 1.0f);
            breatheY.setRepeatCount(ValueAnimator.INFINITE);
            breatheY.setDuration(2000);
            breatheY.setInterpolator(new AccelerateDecelerateInterpolator());

            breatheX.start();
            breatheY.start();
        }, 1000);
    }

    // ─── Content (whole block) ───────────────────────────────────────────────
    private void animateContent() {
        llSplashContent.setTranslationY(40f);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(llSplashContent, "alpha", 0f, 1f);
        ObjectAnimator slideUp = ObjectAnimator.ofFloat(llSplashContent, "translationY", 40f, 0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(fadeIn, slideUp);
        set.setDuration(600);
        set.setInterpolator(new DecelerateInterpolator(2f));
        set.start();
    }

    // ─── Logo bounce pulse ───────────────────────────────────────────────────
    private void animateLogoPulse() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(ivSplashLogo, "scaleX", 0f, 1.15f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(ivSplashLogo, "scaleY", 0f, 1.15f, 1.0f);
        AnimatorSet bounce = new AnimatorSet();
        bounce.playTogether(scaleX, scaleY);
        bounce.setDuration(700);
        bounce.setInterpolator(new OvershootInterpolator(1.5f));
        bounce.start();
    }

    // ─── Ring expand ────────────────────────────────────────────────────────
    private void animateRing() {
        vLogoRing.setScaleX(0.3f);
        vLogoRing.setScaleY(0.3f);

        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(vLogoRing, "alpha", 0f, 0.7f, 0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(vLogoRing, "scaleX", 0.4f, 1.5f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(vLogoRing, "scaleY", 0.4f, 1.5f);
        AnimatorSet ring = new AnimatorSet();
        ring.playTogether(fadeIn, scaleX, scaleY);
        ring.setDuration(900);
        ring.setInterpolator(new DecelerateInterpolator(1.5f));
        ring.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Repeat subtle ring pulses
                if (isAnimating) repeatRingPulse();
            }
        });
        ring.start();
    }

    private void repeatRingPulse() {
        vLogoRing.setScaleX(0.8f);
        vLogoRing.setScaleY(0.8f);
        vLogoRing.setAlpha(0f);

        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(vLogoRing, "alpha", 0f, 0.4f, 0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(vLogoRing, "scaleX", 0.8f, 1.3f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(vLogoRing, "scaleY", 0.8f, 1.3f);
        AnimatorSet ring = new AnimatorSet();
        ring.playTogether(fadeIn, scaleX, scaleY);
        ring.setDuration(1200);
        ring.setInterpolator(new DecelerateInterpolator());
        ring.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isAnimating) {
                    handler.postDelayed(SplashActivity.this::repeatRingPulse, 400);
                }
            }
        });
        ring.start();
    }

    // ─── Title + Tagline ────────────────────────────────────────────────────
    private void animateTextElements() {
        // Title slides in from left
        tvSplashTitle.setTranslationX(-30f);
        ObjectAnimator titleFade = ObjectAnimator.ofFloat(tvSplashTitle, "alpha", 0f, 1f);
        ObjectAnimator titleSlide = ObjectAnimator.ofFloat(tvSplashTitle, "translationX", -30f, 0f);
        AnimatorSet titleSet = new AnimatorSet();
        titleSet.playTogether(titleFade, titleSlide);
        titleSet.setDuration(500);
        titleSet.setInterpolator(new DecelerateInterpolator(1.5f));
        titleSet.start();

        // Tagline with slight delay
        handler.postDelayed(() -> {
            tvSplashTagline.setAlpha(0f);
            ObjectAnimator tagFade = ObjectAnimator.ofFloat(tvSplashTagline, "alpha", 0f, 1f);
            tagFade.setDuration(500);
            tagFade.start();
        }, 150);
    }

    // ─── Particles ──────────────────────────────────────────────────────────
    private void animateParticles() {
        Random rng = new Random(42);
        for (int i = 0; i < particles.length; i++) {
            final View p = particles[i];
            float dx = (rng.nextFloat() - 0.5f) * 60f;
            float dy = (rng.nextFloat() - 0.5f) * 60f;
            long delay = i * 80L;

            handler.postDelayed(() -> {
                p.setTranslationX(0f);
                p.setTranslationY(0f);
                p.setAlpha(0f);
                p.setScaleX(0f);
                p.setScaleY(0f);

                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(p, "alpha", 0f, 0.9f, 0.5f);
                ObjectAnimator moveX = ObjectAnimator.ofFloat(p, "translationX", 0f, dx);
                ObjectAnimator moveY = ObjectAnimator.ofFloat(p, "translationY", 0f, dy);
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(p, "scaleX", 0f, 1.2f, 1.0f);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(p, "scaleY", 0f, 1.2f, 1.0f);

                AnimatorSet set = new AnimatorSet();
                set.playTogether(fadeIn, moveX, moveY, scaleX, scaleY);
                set.setDuration(800);
                set.setInterpolator(new DecelerateInterpolator(2f));
                set.start();

                // Subtle float loop
                handler.postDelayed(() -> floatParticle(p, dx, dy), 850);
            }, delay);
        }
    }

    private void floatParticle(View p, float baseX, float baseY) {
        if (!isAnimating) return;
        float offsetX = baseX + (new Random().nextFloat() - 0.5f) * 20f;
        float offsetY = baseY + (new Random().nextFloat() - 0.5f) * 20f;

        ObjectAnimator moveX = ObjectAnimator.ofFloat(p, "translationX", baseX, offsetX);
        ObjectAnimator moveY = ObjectAnimator.ofFloat(p, "translationY", baseY, offsetY);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(moveX, moveY);
        set.setDuration(1200);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isAnimating) floatParticle(p, offsetX, offsetY);
            }
        });
        set.start();
    }

    // ─── Loading dots + version ──────────────────────────────────────────────
    private void animateBottomElements() {
        ObjectAnimator dotsIn = ObjectAnimator.ofFloat(llLoadingDots, "alpha", 0f, 1f);
        dotsIn.setDuration(400);
        dotsIn.start();

        ObjectAnimator versionIn = ObjectAnimator.ofFloat(tvSplashVersion, "alpha", 0f, 1f);
        versionIn.setDuration(500);
        versionIn.start();
    }

    // ─── Loading dot wave loop ───────────────────────────────────────────────
    private int dotCycle = 0;

    private void startLoadingDotLoop() {
        animateDotsWave();
    }

    private void animateDotsWave() {
        if (!isAnimating) return;
        View[] dots = {vLoadDot1, vLoadDot2, vLoadDot3};
        int active = dotCycle % 3;

        for (int i = 0; i < dots.length; i++) {
            final int idx = i;
            final View dot = dots[i];
            if (idx == active) {
                // Active dot: scale up + brighter
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(dot, "scaleX", 1.0f, 1.4f, 1.0f);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(dot, "scaleY", 1.0f, 1.4f, 1.0f);
                ObjectAnimator alpha = ObjectAnimator.ofFloat(dot, "alpha", 0.5f, 1.0f, 0.5f);
                AnimatorSet s = new AnimatorSet();
                s.playTogether(scaleX, scaleY, alpha);
                s.setDuration(400);
                s.start();
            } else {
                ObjectAnimator alpha = ObjectAnimator.ofFloat(dot, "alpha", 1.0f, 0.3f);
                alpha.setDuration(400);
                alpha.start();
            }
        }

        dotCycle++;
        handler.postDelayed(this::animateDotsWave, 450);
    }

    // ─── Navigate ────────────────────────────────────────────────────────────
    private void navigateNext() {
        if (isFinishing()) return;

        // Fade out everything
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(findViewById(android.R.id.content), "alpha", 1f, 0f);
        fadeOut.setDuration(350);
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                doNavigate();
            }
        });
        fadeOut.start();
    }

    private void doNavigate() {
        isAnimating = false;

        SharedPreferences prefs = getSharedPreferences("molly_agent_prefs", Context.MODE_PRIVATE);
        boolean hasSeenIntro = prefs.getBoolean(IntroActivity.PREF_INTRO_KEY, false);
        MollyApiClient apiClient = new MollyApiClient(this);

        Intent intent;
        if (!hasSeenIntro) {
            intent = new Intent(this, IntroActivity.class);
        } else if (apiClient.isConfigured()) {
            intent = new Intent(this, ChatActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }

        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        isAnimating = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
