package com.molly.agent.intro;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

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
    private ImageView dot1;
    private ImageView dot2;
    private ImageView dot3;

    private List<IntroSlide> slideList;
    private MollyApiClient apiClient;

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

        initSlidesData();

        IntroAdapter adapter = new IntroAdapter(slideList);
        viewPager.setAdapter(adapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateIndicators(position);
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

    private void updateIndicators(int position) {
        dot1.setImageResource(position == 0 ? R.drawable.dot_active : R.drawable.dot_inactive);
        dot2.setImageResource(position == 1 ? R.drawable.dot_active : R.drawable.dot_inactive);
        dot3.setImageResource(position == 2 ? R.drawable.dot_active : R.drawable.dot_inactive);

        if (position == slideList.size() - 1) {
            btnNextOrStart.setText("Mulai Sekarang");
        } else {
            btnNextOrStart.setText("Lanjut");
        }
    }

    private void completeIntro() {
        SharedPreferences prefs = getSharedPreferences("molly_agent_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_INTRO_KEY, true).apply();

        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
