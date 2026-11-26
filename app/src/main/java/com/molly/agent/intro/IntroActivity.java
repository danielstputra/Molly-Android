package com.molly.agent.intro;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.molly.agent.MainActivity;
import com.molly.agent.R;
import com.molly.agent.api.MollyApiClient;

import java.util.ArrayList;
import java.util.List;

public class IntroActivity extends AppCompatActivity {

    public static final String PREF_INTRO_KEY = "has_seen_intro_v1";

    private ViewPager2 viewPager;
    private TextView btnSkip;
    private MaterialButton btnNextOrStart;
    private View dot1;
    private View dot2;
    private View dot3;
    private View[] dots;

    private List<IntroSlide> slideList;
    private MollyApiClient apiClient;
    private int previousPage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        apiClient = new MollyApiClient(this);

        viewPager = findViewById(R.id.viewPagerIntro);
        btnSkip = findViewById(R.id.btnSkipIntro);
        btnNextOrStart = findViewById(R.id.btnNextOrStart);
        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        dot3 = findViewById(R.id.dot3);
        dots = new View[]{dot1, dot2, dot3};

        initSlidesData();

        IntroAdapter adapter = new IntroAdapter(slideList);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(3);

        // Pasang PageTransformer Interaktif (Parallax + Depth + Fade)
        setupInteractivePageTransformer();

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                animateDotIndicators(position);
                updateControlsForPosition(position);
                previousPage = position;
            }
        });

        btnSkip.setOnClickListener(v -> completeIntro());

        btnNextOrStart.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < slideList.size() - 1) {
                viewPager.setCurrentItem(current + 1, true);
            } else {
                completeIntro();
            }
        });

        // Intro top bar & button entrance animation
        animateEntrance();
    }

    private void initSlidesData() {
        slideList = new ArrayList<>();

        slideList.add(new IntroSlide(
                R.drawable.ic_intro_agent,
                "Agen Cerdas Otonom",
                "Mengenal Molly Agent",
                "Molly adalah platform kecerdasan buatan otonom tingkat lanjut untuk otomasi instruksi, analisis data cerdas, dan orkestrasi alur kerja enterprise yang efisien."
        ));

        slideList.add(new IntroSlide(
                R.drawable.ic_intro_sync,
                "Zero-Login Companion",
                "Sinkronisasi Realtime",
                "Cukup 1x pindai QR Code di layar browser Molly Web. Sesi percakapan dan respon agen AI langsung tersinkronisasi mulus dua arah tanpa perlu login manual."
        ));

        slideList.add(new IntroSlide(
                R.drawable.ic_intro_models,
                "Fleksibel & Terkendali",
                "Multi-Model & Alat Cerdas",
                "Bebas beralih model AI (Gemini, DeepSeek, GPT-4o) dan ganti agen atau mode persetujuan alat (penuh / manual) langsung dari genggaman ponsel Anda."
        ));
    }

    /**
     * ViewPager2 PageTransformer yang memberikan efek 3D Parallax & Depth Zoom saat digeser.
     */
    private void setupInteractivePageTransformer() {
        viewPager.setPageTransformer(new ViewPager2.PageTransformer() {
            private static final float MIN_SCALE = 0.84f;
            private static final float MIN_ALPHA = 0.4f;

            @Override
            public void transformPage(@NonNull View page, float position) {
                int pageWidth = page.getWidth();

                View container = page.findViewById(R.id.flIllustrationContainer);
                View tagline = page.findViewById(R.id.tvIntroTagline);
                View title = page.findViewById(R.id.tvIntroTitle);
                View desc = page.findViewById(R.id.tvIntroDescription);

                if (position < -1) { // [-Infinity,-1)
                    page.setAlpha(0f);
                } else if (position <= 1) { // [-1,1]
                    // Scale & Alpha
                    float scaleFactor = Math.max(MIN_SCALE, 1 - Math.abs(position) * 0.16f);
                    float alphaFactor = Math.max(MIN_ALPHA, 1 - Math.abs(position) * 0.6f);

                    page.setScaleX(scaleFactor);
                    page.setScaleY(scaleFactor);
                    page.setAlpha(alphaFactor);

                    // Parallax effects on inner elements
                    if (container != null) {
                        container.setTranslationX(-position * (pageWidth / 3.0f));
                    }
                    if (tagline != null) {
                        tagline.setTranslationX(-position * (pageWidth / 4.5f));
                    }
                    if (title != null) {
                        title.setTranslationX(-position * (pageWidth / 5.5f));
                    }
                    if (desc != null) {
                        desc.setTranslationX(-position * (pageWidth / 7.0f));
                    }
                } else { // (1,+Infinity]
                    page.setAlpha(0f);
                }
            }
        });
    }

    /**
     * Animasi morphing halus dari bulatan kecil (8dp) menjadi pill panjang (26dp)
     */
    private void animateDotIndicators(int activePosition) {
        int activeWidthPx = dpToPx(26);
        int inactiveWidthPx = dpToPx(8);

        for (int i = 0; i < dots.length; i++) {
            final View dot = dots[i];
            boolean isActive = (i == activePosition);

            int startWidth = dot.getWidth();
            if (startWidth <= 0) {
                startWidth = isActive ? inactiveWidthPx : activeWidthPx;
            }
            int targetWidth = isActive ? activeWidthPx : inactiveWidthPx;

            ValueAnimator widthAnim = ValueAnimator.ofInt(startWidth, targetWidth);
            widthAnim.setDuration(320);
            widthAnim.setInterpolator(isActive ? new OvershootInterpolator(1.4f) : new DecelerateInterpolator());
            widthAnim.addUpdateListener(anim -> {
                ViewGroup.LayoutParams lp = dot.getLayoutParams();
                lp.width = (int) anim.getAnimatedValue();
                dot.setLayoutParams(lp);
            });
            widthAnim.start();

            if (isActive) {
                dot.setBackgroundResource(R.drawable.bg_intro_dot_active);
            } else {
                dot.setBackgroundResource(R.drawable.bg_intro_dot_inactive);
            }
        }
    }

    private void updateControlsForPosition(int position) {
        boolean isLast = (position == slideList.size() - 1);

        if (isLast) {
            btnNextOrStart.setText("Mulai Sekarang");
            btnSkip.animate().alpha(0f).setDuration(200).withEndAction(() -> btnSkip.setVisibility(View.INVISIBLE)).start();

            // Spring pulse bounce pada tombol agar interaktif dan menarik perhatian pengguna
            ObjectAnimator pulseX = ObjectAnimator.ofFloat(btnNextOrStart, "scaleX", 1.0f, 1.05f, 1.0f);
            ObjectAnimator pulseY = ObjectAnimator.ofFloat(btnNextOrStart, "scaleY", 1.0f, 1.05f, 1.0f);
            AnimatorSet bounce = new AnimatorSet();
            bounce.playTogether(pulseX, pulseY);
            bounce.setDuration(450);
            bounce.setInterpolator(new OvershootInterpolator(1.8f));
            bounce.start();
        } else {
            btnNextOrStart.setText("Lanjut");
            if (btnSkip.getVisibility() != View.VISIBLE) {
                btnSkip.setVisibility(View.VISIBLE);
                btnSkip.animate().alpha(1f).setDuration(200).start();
            }
        }
    }

    private void animateEntrance() {
        View topBar = findViewById(R.id.rlIntroTopBar);
        View bottomControls = findViewById(R.id.llIntroBottomControls);

        if (topBar != null) {
            topBar.setTranslationY(-30f);
            topBar.setAlpha(0f);
            topBar.animate().translationY(0f).alpha(1f).setDuration(400).setInterpolator(new DecelerateInterpolator()).start();
        }

        if (bottomControls != null) {
            bottomControls.setTranslationY(40f);
            bottomControls.setAlpha(0f);
            bottomControls.animate().translationY(0f).alpha(1f).setDuration(500).setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private void completeIntro() {
        SharedPreferences prefs = getSharedPreferences("molly_agent_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_INTRO_KEY, true).apply();

        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
