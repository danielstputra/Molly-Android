package com.molly.agent;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.molly.agent.api.MollyApiClient;
import com.molly.agent.intro.IntroActivity;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 1400;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Validasi integritas aplikasi saat peluncuran (Anti-Tamper, Anti-Clone, Anti-Debugger)
        if (!com.molly.agent.security.AppSecurityGuard.enforceSecurity(this)) {
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(this::navigateNext, SPLASH_DELAY_MS);
    }

    private void navigateNext() {
        if (isFinishing()) return;

        SharedPreferences prefs = getSharedPreferences("molly_agent_prefs", Context.MODE_PRIVATE);
        boolean hasSeenIntro = prefs.getBoolean(IntroActivity.PREF_INTRO_KEY, false);

        MollyApiClient apiClient = new MollyApiClient(this);

        Intent intent;
        if (!hasSeenIntro) {
            intent = new Intent(SplashActivity.this, IntroActivity.class);
        } else if (apiClient.isConfigured()) {
            // Jika sudah pernah pairing dan sesi masih aktif, langsung masuk ke chat
            intent = new Intent(SplashActivity.this, ChatActivity.class);
        } else {
            intent = new Intent(SplashActivity.this, MainActivity.class);
        }

        startActivity(intent);
        finish();
    }
}
