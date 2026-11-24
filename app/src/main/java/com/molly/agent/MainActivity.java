package com.molly.agent;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.molly.agent.api.MollyApiClient;

public class MainActivity extends AppCompatActivity {
    private MollyApiClient apiClient;

    private View statusIndicatorDot;
    private TextView tvConnectionStatus;
    private TextView tvServerDetails;
    private TextView tvActiveSession;
    private MaterialButton btnOpenActiveChat;
    private MaterialButton btnDisconnectMain;
    private MaterialButton btnScanQr;
    private MaterialButton btnManualConnect;

    private final ActivityResultLauncher<ScanOptions> qrScannerLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    processQrPayload(result.getContents());
                } else {
                    Toast.makeText(this, "Pemindaian QR dibatalkan", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        apiClient = new MollyApiClient(this);

        statusIndicatorDot = findViewById(R.id.statusIndicatorDot);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvServerDetails = findViewById(R.id.tvServerDetails);
        tvActiveSession = findViewById(R.id.tvActiveSession);
        btnOpenActiveChat = findViewById(R.id.btnOpenActiveChat);
        btnDisconnectMain = findViewById(R.id.btnDisconnectMain);
        btnScanQr = findViewById(R.id.btnScanQr);
        btnManualConnect = findViewById(R.id.btnManualConnect);
        TextView btnViewIntroAgain = findViewById(R.id.btnViewIntroAgain);
        View btnAboutApp = findViewById(R.id.btnAboutApp);

        btnScanQr.setOnClickListener(v -> startQrScan());
        btnManualConnect.setOnClickListener(v -> showManualConnectDialog());
        btnOpenActiveChat.setOnClickListener(v -> openChatActivity());
        btnDisconnectMain.setOnClickListener(v -> showDisconnectConfirmationDialog());
        if (btnViewIntroAgain != null) {
            btnViewIntroAgain.setOnClickListener(v -> {
                Intent introIntent = new Intent(this, com.molly.agent.intro.IntroActivity.class);
                startActivity(introIntent);
            });
        }
        if (btnAboutApp != null) {
            btnAboutApp.setOnClickListener(v -> com.molly.agent.ui.AboutAppDialogHelper.show(this));
        }

        apiClient.setSessionExpirationListener(reason -> {
            updateUiStatus();
            Toast.makeText(this, "Sesi login di Molly Web telah berakhir atau logout. Sambungan otomatis diputuskan.", Toast.LENGTH_LONG).show();
        });

        updateUiStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        apiClient.loadConfig();
        if (apiClient.isConfigured()) {
            apiClient.verifySession(new MollyApiClient.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    updateUiStatus();
                }

                @Override
                public void onError(String error) {
                    updateUiStatus();
                }
            });
        } else {
            updateUiStatus();
        }
    }

    private void updateUiStatus() {
        if (apiClient.isConfigured()) {
            statusIndicatorDot.setBackgroundTintList(getColorStateList(R.color.status_green));
            tvConnectionStatus.setText(R.string.status_connected);
            tvServerDetails.setText("Server: " + apiClient.getServerUrl());
            tvActiveSession.setText("Sesi Aktif: " + apiClient.getSessionId());
            btnOpenActiveChat.setVisibility(View.VISIBLE);
            btnDisconnectMain.setVisibility(View.VISIBLE);
        } else {
            statusIndicatorDot.setBackgroundTintList(getColorStateList(R.color.status_red));
            tvConnectionStatus.setText(R.string.status_disconnected);
            tvServerDetails.setText("Server: Belum terhubung");
            tvActiveSession.setText("Sesi: Pindai QR di Molly Web");
            btnOpenActiveChat.setVisibility(View.GONE);
            btnDisconnectMain.setVisibility(View.GONE);
        }
    }

    private void showDisconnectConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Putuskan Molly Agent?")
                .setMessage("Apakah Anda yakin ingin memutuskan sambungan dengan server Molly? Sesi pairing akan dihapus dari HP.")
                .setPositiveButton("Ya, Putuskan", (dialog, which) -> {
                    apiClient.disconnect();
                    updateUiStatus();
                    Toast.makeText(this, "Koneksi Molly Agent telah diputuskan", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void startQrScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Arahkan kamera ke QR Code di Molly Web");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        qrScannerLauncher.launch(options);
    }

    private void processQrPayload(String rawPayload) {
        try {
            String serverUrl = "";
            String token = "molly-guest-session";
            String sessionId = "";
            String agentId = "";
            String agentName = "Molly Agent";

            String trimmed = rawPayload.trim();
            String tunnelUrl = "";

            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                JsonObject obj = JsonParser.parseString(trimmed).getAsJsonObject();
                if (obj.has("server_url")) serverUrl = obj.get("server_url").getAsString().trim();
                if (obj.has("tunnel_url")) tunnelUrl = obj.get("tunnel_url").getAsString().trim();
                if (obj.has("token") && !obj.get("token").getAsString().trim().isEmpty()) {
                    token = obj.get("token").getAsString().trim();
                }
                if (obj.has("session_id") && !obj.get("session_id").getAsString().trim().isEmpty()) {
                    sessionId = obj.get("session_id").getAsString().trim();
                }
                if (obj.has("agent_id")) agentId = obj.get("agent_id").getAsString().trim();
                if (obj.has("agent_name")) agentName = obj.get("agent_name").getAsString().trim();
            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                // User hanya menempelkan URL server
                serverUrl = trimmed;
            } else if (trimmed.contains(":") || trimmed.contains(".")) {
                // User menempelkan host:port atau domain tanpa skema http
                serverUrl = "http://" + trimmed;
            }

            // Bersihkan trailing slash
            if (serverUrl.endsWith("/")) {
                serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
            }

            if (serverUrl.isEmpty()) {
                Toast.makeText(this, "URL Server tidak valid atau tidak ditemukan dalam pairing.", Toast.LENGTH_LONG).show();
                return;
            }

            apiClient.savePairingData(serverUrl, tunnelUrl, token, sessionId, agentId, agentName);
            Toast.makeText(this, "Berhasil terhubung ke Molly!", Toast.LENGTH_SHORT).show();
            updateUiStatus();
            openChatActivity();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal memproses data pairing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showManualConnectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.manual_connect);

        final EditText input = new EditText(this);
        input.setHint("Tempel JSON pairing dari Molly Web di sini...");
        input.setLines(4);
        builder.setView(input);

        builder.setPositiveButton(R.string.connect, (dialog, which) -> {
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                processQrPayload(text);
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void openChatActivity() {
        Intent intent = new Intent(this, ChatActivity.class);
        startActivity(intent);
    }
}
