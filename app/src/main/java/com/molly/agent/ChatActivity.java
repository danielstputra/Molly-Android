package com.molly.agent;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.molly.agent.ui.MessageAdapter;
import com.molly.agent.adapter.SessionDrawerAdapter;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.ChatMessage;
import com.molly.agent.model.ChatDisplayItem;
import com.molly.agent.model.ChatSessionItem;
import com.molly.agent.model.SessionHistoryManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ChatActivity extends AppCompatActivity implements SessionDrawerAdapter.OnSessionClickListener {
    private MollyApiClient apiClient;
    private SessionHistoryManager sessionManager;
    private MessageAdapter messageAdapter;

    private DrawerLayout drawerLayout;
    private ImageView btnMenu;
    private ImageView btnClearChat;
    private View btnDisconnect;
    private TextView tvAgentName;

    private RecyclerView rvMessages;
    private LinearLayout llThinking;
    private EditText etMessage;
    private FloatingActionButton btnSend;

    // Action pills seperti Gambar 2
    private TextView btnSelectAgent;
    private TextView btnSelectApproval;
    private TextView btnSelectModel;

    // Komponen Drawer Sesi
    private MaterialButton btnDrawerNewSession;
    private TextView tabActiveSessions;
    private TextView tabArchivedSessions;
    private RecyclerView rvDrawerSessions;
    private TextView tvEmptySessions;
    private SessionDrawerAdapter drawerAdapter;
    private boolean showArchived = false;

    private List<MollyApiClient.ModelItem> availableModels = new ArrayList<>();
    private List<MollyApiClient.AgentItem> availableAgents = new ArrayList<>();

    private boolean isSessionExpiredHandled = false;
    private final Handler heartbeatHandler = new Handler(Looper.getMainLooper());
    private static final long HEARTBEAT_INTERVAL_MS = 7000;

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isSessionExpiredHandled) return;
            apiClient.verifySession(new MollyApiClient.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    if (!isFinishing() && !isSessionExpiredHandled) {
                        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS);
                    }
                }

                @Override
                public void onError(String error) {
                    if (!isFinishing() && !isSessionExpiredHandled) {
                        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS);
                    }
                }
            });
        }
    };

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private boolean isPolling = false;
    private int pollCount = 0;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPolling) return;
            fetchLatestMessages(false);
            pollCount++;
            if (pollCount < 35) { // Poll up to 50 seconds
                pollHandler.postDelayed(this, 1500);
            } else {
                stopPolling();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        apiClient = new MollyApiClient(this);
        sessionManager = new SessionHistoryManager(this);

        apiClient.setSessionExpirationListener(reason -> {
            handleSessionExpired(reason);
        });

        if (!apiClient.isConfigured()) {
            Toast.makeText(this, "Sesi belum terhubung. Silakan scan QR code terlebih dahulu.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        drawerLayout = findViewById(R.id.drawerLayout);
        btnMenu = findViewById(R.id.btnMenu);
        btnClearChat = findViewById(R.id.btnClearChat);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        tvAgentName = findViewById(R.id.tvAgentName);

        rvMessages = findViewById(R.id.rvMessages);
        llThinking = findViewById(R.id.llThinking);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        btnSelectAgent = findViewById(R.id.btnSelectAgent);
        btnSelectApproval = findViewById(R.id.btnSelectApproval);
        btnSelectModel = findViewById(R.id.btnSelectModel);

        // Inisialisasi komponen Drawer
        btnDrawerNewSession = findViewById(R.id.btnDrawerNewSession);
        tabActiveSessions = findViewById(R.id.tabActiveSessions);
        tabArchivedSessions = findViewById(R.id.tabArchivedSessions);
        rvDrawerSessions = findViewById(R.id.rvDrawerSessions);
        tvEmptySessions = findViewById(R.id.tvEmptySessions);

        rvDrawerSessions.setLayoutManager(new LinearLayoutManager(this));

        tvAgentName.setText(apiClient.getAgentName());

        messageAdapter = new MessageAdapter();
        messageAdapter.setApprovalListener(new MessageAdapter.OnToolApprovalListener() {
            @Override
            public void onApprove(ChatDisplayItem item) {
                handleToolApprovalDecision(item, true);
            }

            @Override
            public void onReject(ChatDisplayItem item) {
                handleToolApprovalDecision(item, false);
            }
        });
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(messageAdapter);

        // Click listeners
        btnMenu.setOnClickListener(v -> {
            loadDrawerSessions();
            drawerLayout.openDrawer(GravityCompat.START);
        });

        btnClearChat.setOnClickListener(v -> showClearChatConfirmationDialog());
        btnDisconnect.setOnClickListener(v -> showDisconnectConfirmationDialog());
        btnSend.setOnClickListener(v -> handleSendMessage());

        // Event klik untuk action pills (Gambar 2)
        btnSelectAgent.setOnClickListener(v -> showAgentPickerDialog());
        btnSelectApproval.setOnClickListener(v -> showApprovalModeDialog());
        btnSelectModel.setOnClickListener(v -> showModelPickerDialog());

        // Event Drawer
        btnDrawerNewSession.setOnClickListener(v -> handleNewSessionChat());
        tabActiveSessions.setOnClickListener(v -> {
            showArchived = false;
            updateTabUi();
            loadDrawerSessions();
        });
        tabArchivedSessions.setOnClickListener(v -> {
            showArchived = true;
            updateTabUi();
            loadDrawerSessions();
        });

        View btnDrawerServerConfig = findViewById(R.id.btnDrawerServerConfig);
        if (btnDrawerServerConfig != null) {
            btnDrawerServerConfig.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                showServerConfigDialog();
            });
        }

        View btnDrawerAbout = findViewById(R.id.btnDrawerAbout);
        if (btnDrawerAbout != null) {
            btnDrawerAbout.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                com.molly.agent.ui.AboutAppDialogHelper.show(this);
            });
        }

        updateActionPillsUi();

        // Inisialisasi awal model & agent dari cache lokal / default agar UI langsung responsif
        availableModels = apiClient.getCachedOrFallbackModels();
        availableAgents = apiClient.getCachedOrFallbackAgents();

        // Catat sesi awal di history
        sessionManager.recordOrUpdateSession(
                apiClient.getSessionId(),
                "Percakapan Sesi",
                "",
                apiClient.getAgentName(),
                apiClient.getSelectedModelName()
        );

        // Ambil metadata models dan agents dari server Molly
        loadMetadata();

        // Load awal riwayat percakapan sesi
        fetchLatestMessages(true);

        // Sinkronkan daftar sesi dengan server di background
        syncServerSessions();
    }

    private void updateTabUi() {
        if (!showArchived) {
            tabActiveSessions.setBackgroundResource(R.drawable.bg_action_pill);
            tabActiveSessions.setBackgroundTintList(getColorStateList(R.color.user_bubble_bg));
            tabActiveSessions.setTextColor(getColor(R.color.primary_gold));

            tabArchivedSessions.setBackgroundResource(android.R.color.transparent);
            tabArchivedSessions.setTextColor(getColor(R.color.text_muted));
        } else {
            tabArchivedSessions.setBackgroundResource(R.drawable.bg_action_pill);
            tabArchivedSessions.setBackgroundTintList(getColorStateList(R.color.user_bubble_bg));
            tabArchivedSessions.setTextColor(getColor(R.color.primary_gold));

            tabActiveSessions.setBackgroundResource(android.R.color.transparent);
            tabActiveSessions.setTextColor(getColor(R.color.text_muted));
        }
    }

    private void loadDrawerSessions() {
        List<ChatSessionItem> items = sessionManager.getSessions(showArchived);
        if (items.isEmpty()) {
            tvEmptySessions.setVisibility(View.VISIBLE);
            tvEmptySessions.setText(showArchived ? "Tidak ada sesi diarsipkan" : "Belum ada riwayat sesi");
        } else {
            tvEmptySessions.setVisibility(View.GONE);
        }

        drawerAdapter = new SessionDrawerAdapter(this, items, apiClient.getSessionId(), this);
        rvDrawerSessions.setAdapter(drawerAdapter);
    }

    private void syncServerSessions() {
        apiClient.fetchRecentSessions(false, new MollyApiClient.ApiCallback<List<ChatSessionItem>>() {
            @Override
            public void onSuccess(List<ChatSessionItem> result) {
                if (result != null && !result.isEmpty()) {
                    sessionManager.mergeServerSessions(result);
                    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        loadDrawerSessions();
                    }
                }
            }

            @Override
            public void onError(String error) {
                // Abaikan error jaringan background
            }
        });
    }

    @Override
    public void onSessionSelect(ChatSessionItem session) {
        if (session.getId().equals(apiClient.getSessionId())) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }

        stopPolling();
        apiClient.switchSession(session.getId());
        messageAdapter.setItems(new ArrayList<>());
        drawerLayout.closeDrawer(GravityCompat.START);

        Toast.makeText(this, "Membuka: " + session.getTitle(), Toast.LENGTH_SHORT).show();
        fetchLatestMessages(true);
    }

    @Override
    public void onSessionArchive(ChatSessionItem session) {
        boolean willBeArchived = !session.isArchived();
        sessionManager.setArchived(session.getId(), willBeArchived);

        apiClient.archiveSession(session.getId(), new MollyApiClient.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {}
            @Override
            public void onError(String error) {}
        });

        Toast.makeText(this, willBeArchived ? "Sesi berhasil diarsipkan" : "Sesi dikembalikan ke aktif", Toast.LENGTH_SHORT).show();
        loadDrawerSessions();

        // Jika sesi yang diarsipkan adalah sesi yang sedang aktif dibuka, buat sesi baru
        if (session.getId().equals(apiClient.getSessionId()) && willBeArchived) {
            handleNewSessionChat();
        }
    }

    @Override
    public void onSessionDelete(ChatSessionItem session) {
        new AlertDialog.Builder(this)
                .setTitle("Hapus Sesi?")
                .setMessage("Hapus percakapan '" + session.getTitle() + "'? Tindakan ini tidak dapat dibatalkan.")
                .setPositiveButton("Hapus", (dialog, which) -> {
                    sessionManager.deleteSession(session.getId());
                    apiClient.archiveSession(session.getId(), new MollyApiClient.ApiCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean result) {}
                        @Override
                        public void onError(String error) {}
                    });

                    Toast.makeText(this, "Sesi percakapan telah dihapus", Toast.LENGTH_SHORT).show();
                    loadDrawerSessions();

                    if (session.getId().equals(apiClient.getSessionId())) {
                        handleNewSessionChat();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void handleNewSessionChat() {
        stopPolling();
        apiClient.createNewSession();
        messageAdapter.setItems(new ArrayList<>());
        drawerLayout.closeDrawer(GravityCompat.START);
        Toast.makeText(this, "Sesi chat baru siap. Ketik pesan untuk memulai.", Toast.LENGTH_SHORT).show();
    }

    private void showClearChatConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Bersihkan Chat?")
                .setMessage("Apakah Anda yakin ingin mengosongkan tampilan pesan pada sesi saat ini?")
                .setPositiveButton("Bersihkan", (dialog, which) -> {
                    messageAdapter.setItems(new ArrayList<>());
                    Toast.makeText(this, "Tampilan pesan dibersihkan", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void updateActionPillsUi() {
        btnSelectAgent.setText("+ " + apiClient.getAgentName() + " ⌵");

        String approvalText = "kepercayaan penuh ⌵";
        if ("default".equalsIgnoreCase(apiClient.getSelectedApprovalMode()) || "manual".equalsIgnoreCase(apiClient.getSelectedApprovalMode())) {
            approvalText = "persetujuan manual ⌵";
        }
        btnSelectApproval.setText(approvalText);

        btnSelectModel.setText(apiClient.getSelectedModelName() + " ⌵");
    }

    private void loadMetadata() {
        apiClient.fetchMetadata(new MollyApiClient.ApiCallback<MollyApiClient.MetadataResponse>() {
            @Override
            public void onSuccess(MollyApiClient.MetadataResponse result) {
                if (result.models != null && !result.models.isEmpty()) {
                    availableModels = result.models;
                }
                if (result.agents != null && !result.agents.isEmpty()) {
                    availableAgents = result.agents;
                }
            }

            @Override
            public void onError(String error) {
                // Gunakan default jika offline
            }
        });
    }

    private void showAgentPickerDialog() {
        if (availableAgents == null || availableAgents.isEmpty()) {
            availableAgents = apiClient.getCachedOrFallbackAgents();
        }

        String[] names = new String[availableAgents.size()];
        int selectedIndex = 0;
        for (int i = 0; i < availableAgents.size(); i++) {
            names[i] = availableAgents.get(i).name;
            if (availableAgents.get(i).name.equalsIgnoreCase(apiClient.getAgentName()) ||
                availableAgents.get(i).id.equalsIgnoreCase(apiClient.getAgentId())) {
                selectedIndex = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Pilih Agent Molly")
                .setSingleChoiceItems(names, selectedIndex, (dialog, which) -> {
                    MollyApiClient.AgentItem chosen = availableAgents.get(which);
                    apiClient.setSelectedAgent(chosen.id, chosen.name);
                    tvAgentName.setText(chosen.name);
                    updateActionPillsUi();
                    dialog.dismiss();
                })
                .setNegativeButton("Batal", null)
                .show();

        // Refresh metadata di background non-blocking
        loadMetadata();
    }

    private void showModelPickerDialog() {
        if (availableModels == null || availableModels.isEmpty()) {
            availableModels = apiClient.getCachedOrFallbackModels();
        }

        String[] displayNames = new String[availableModels.size()];
        int selectedIndex = 0;
        for (int i = 0; i < availableModels.size(); i++) {
            MollyApiClient.ModelItem m = availableModels.get(i);
            displayNames[i] = m.name + " (" + m.provider + ")";
            if (m.name.equalsIgnoreCase(apiClient.getSelectedModelName()) ||
                m.id.equalsIgnoreCase(apiClient.getSelectedModelId())) {
                selectedIndex = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Pilih Model & Provider AI")
                .setSingleChoiceItems(displayNames, selectedIndex, (dialog, which) -> {
                    MollyApiClient.ModelItem chosen = availableModels.get(which);
                    apiClient.setSelectedModel(chosen.id, chosen.name);
                    updateActionPillsUi();
                    dialog.dismiss();
                })
                .setNegativeButton("Batal", null)
                .show();

        // Refresh metadata di background non-blocking
        loadMetadata();
    }

    private void showApprovalModeDialog() {
        String[] modes = new String[]{"kepercayaan penuh (Otomatis)", "persetujuan manual"};
        int selectedIndex = ("default".equalsIgnoreCase(apiClient.getSelectedApprovalMode()) || "manual".equalsIgnoreCase(apiClient.getSelectedApprovalMode())) ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle("Mode Persetujuan Tool")
                .setSingleChoiceItems(modes, selectedIndex, (dialog, which) -> {
                    if (which == 0) {
                        apiClient.setSelectedApprovalMode("always_trust");
                    } else {
                        apiClient.setSelectedApprovalMode("default");
                    }
                    updateActionPillsUi();
                    dialog.dismiss();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void handleNetworkTimeoutError(String error, String featureName) {
        String serverUrl = apiClient.getServerUrl();
        boolean isLocalIp = apiClient.isLocalIp(serverUrl);
        String tunnelCandidate = apiClient.getTunnelUrl();

        if (error != null && (error.toLowerCase().contains("timeout") || error.toLowerCase().contains("failed to connect") || error.toLowerCase().contains("unreachable"))) {
            StringBuilder msg = new StringBuilder();
            if (isLocalIp) {
                msg.append("Aplikasi gagal menghubungi server di IP LAN lokal:\n")
                   .append(serverUrl).append("\n\n")
                   .append("Jika ponsel Anda menggunakan data seluler (4G/5G) atau Wi-Fi luar, IP lokal tidak dapat dijangkau dan menyebabkan timeout.\n\n");
            } else {
                msg.append("Koneksi ke server timeout:\n")
                   .append(serverUrl).append("\n\n")
                   .append("Server backend atau tunnel Cloudflare belum merespon.\n\n");
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle("⚠️ Koneksi Timeout")
                    .setMessage(msg.toString());

            if (!tunnelCandidate.isEmpty() && !tunnelCandidate.equalsIgnoreCase(serverUrl)) {
                builder.setPositiveButton("Ganti ke Cloudflare Tunnel", (dialog, which) -> {
                    apiClient.updateServerUrl(tunnelCandidate);
                    Toast.makeText(this, "Beralih ke URL Tunnel: " + tunnelCandidate, Toast.LENGTH_SHORT).show();
                    loadMetadata();
                    syncServerSessions();
                });
                builder.setNeutralButton("Ubah URL Manual", (dialog, which) -> showServerConfigDialog());
            } else {
                builder.setPositiveButton("Konfigurasi URL", (dialog, which) -> showServerConfigDialog());
            }

            builder.setNegativeButton("Tutup", null);
            builder.show();
        } else {
            Toast.makeText(this, "Gagal memuat " + featureName + ": " + error, Toast.LENGTH_LONG).show();
        }
    }

    private void showServerConfigDialog() {
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);

        android.widget.TextView tvHint = new android.widget.TextView(this);
        tvHint.setText("Masukkan URL server backend Molly (IP lokal atau Cloudflare Tunnel publik):");
        tvHint.setTextColor(getColor(R.color.text_muted));
        tvHint.setTextSize(13);
        container.addView(tvHint);

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("https://... atau http://192.168.x.x:5050");
        input.setText(apiClient.getServerUrl());
        input.setSingleLine(true);
        container.addView(input);

        String tunnelCandidate = apiClient.getTunnelUrl();
        if (!tunnelCandidate.isEmpty() && !tunnelCandidate.equalsIgnoreCase(apiClient.getServerUrl())) {
            android.widget.TextView tvQuickTunnel = new android.widget.TextView(this);
            tvQuickTunnel.setText("🔗 Klik untuk isi URL Tunnel tersimpan:\n" + tunnelCandidate);
            tvQuickTunnel.setTextColor(getColor(R.color.primary_gold));
            tvQuickTunnel.setTextSize(12);
            tvQuickTunnel.setPadding(0, 16, 0, 0);
            tvQuickTunnel.setOnClickListener(v -> input.setText(tunnelCandidate));
            container.addView(tvQuickTunnel);
        }

        new AlertDialog.Builder(this)
                .setTitle("🌐 Konfigurasi URL Server")
                .setView(container)
                .setPositiveButton("Simpan & Hubungkan", (d, w) -> {
                    String newUrl = input.getText().toString().trim();
                    if (!newUrl.isEmpty()) {
                        apiClient.updateServerUrl(newUrl);
                        Toast.makeText(this, "URL Server diperbarui. Memuat ulang data...", Toast.LENGTH_SHORT).show();
                        loadMetadata();
                        syncServerSessions();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
        stopSessionHeartbeat();
    }

    private void handleToolApprovalDecision(ChatDisplayItem item, boolean approve) {
        Toast.makeText(this, approve ? "Menyetujui eksekusi alat..." : "Menolak eksekusi alat...", Toast.LENGTH_SHORT).show();
        startThinking();
        apiClient.submitToolApproval(item.getTurnId(), item.getWaitpointId(), approve, new MollyApiClient.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                startPolling();
            }

            @Override
            public void onError(String error) {
                stopThinking();
                Toast.makeText(ChatActivity.this, "Gagal merespon persetujuan: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleSendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        etMessage.setText("");

        // Tampilkan pesan user langsung di RecyclerView secara optimis
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String time = sdf.format(new Date());
        ChatDisplayItem localMsg = new ChatDisplayItem(UUID.randomUUID().toString(), ChatDisplayItem.TYPE_USER, "user", text, time);
        messageAdapter.addItem(localMsg);
        rvMessages.smoothScrollToPosition(messageAdapter.getItemCount() - 1);

        startThinking();

        // Update riwayat judul sesi lokal dari snippet prompt
        String titleSnippet = text.length() > 30 ? text.substring(0, 30) + "..." : text;
        if (!apiClient.getSessionId().isEmpty()) {
            sessionManager.recordOrUpdateSession(
                    apiClient.getSessionId(),
                    titleSnippet,
                    text,
                    apiClient.getAgentName(),
                    apiClient.getSelectedModelName()
            );
        }

        // Kirim prompt ke server Molly
        apiClient.sendPrompt(text, new MollyApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                // Catat/perbarui sesi dengan ID yang valid (termasuk jika dibuat secara auto-healing)
                sessionManager.recordOrUpdateSession(
                        apiClient.getSessionId(),
                        titleSnippet,
                        text,
                        apiClient.getAgentName(),
                        apiClient.getSelectedModelName()
                );
                // Memulai polling respon agent yang sedang streaming
                startPolling();
            }

            @Override
            public void onError(String error) {
                stopThinking();
                Toast.makeText(ChatActivity.this, "Gagal mengirim: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void fetchLatestMessages(boolean scrollToBottom) {
        apiClient.fetchSessionDisplayItems(new MollyApiClient.ApiCallback<List<ChatDisplayItem>>() {
            @Override
            public void onSuccess(List<ChatDisplayItem> list) {
                if (list != null && !list.isEmpty()) {
                    ChatDisplayItem lastItem = list.get(list.size() - 1);
                    boolean isProcessing = false;
                    for (ChatDisplayItem it : list) {
                        String st = it.getToolStatus();
                        if ("in_progress".equalsIgnoreCase(st) || "waiting".equalsIgnoreCase(st)) {
                            isProcessing = true;
                            break;
                        }
                    }

                    if (!isProcessing && (lastItem.getItemType() == ChatDisplayItem.TYPE_AGENT || "completed".equalsIgnoreCase(lastItem.getToolStatus()))) {
                        stopThinking();
                    }

                    messageAdapter.setItems(list);
                    if (scrollToBottom) {
                        rvMessages.scrollToPosition(list.size() - 1);
                    }
                }
            }

            @Override
            public void onError(String error) {
                // Abaikan error jaringan sementara saat polling
            }
        });
    }

    private void startThinking() {
        llThinking.setVisibility(View.VISIBLE);
    }

    private void stopThinking() {
        llThinking.setVisibility(View.GONE);
    }

    private void startPolling() {
        stopPolling();
        isPolling = true;
        pollCount = 0;
        pollHandler.postDelayed(pollRunnable, 1000);
    }

    private void stopPolling() {
        isPolling = false;
        pollHandler.removeCallbacks(pollRunnable);
    }

    private void showDisconnectConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Putuskan Molly Agent?")
                .setMessage("Apakah Anda yakin ingin memutuskan sambungan dengan server Molly? Sesi ini akan ditutup dan Anda harus scan QR code kembali untuk menghubungkannya.")
                .setPositiveButton("Ya, Putuskan", (dialog, which) -> {
                    apiClient.disconnect();
                    Toast.makeText(this, "Koneksi Molly Agent telah diputuskan", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void handleSessionExpired(String reason) {
        if (isSessionExpiredHandled || isFinishing()) return;
        isSessionExpiredHandled = true;

        stopPolling();
        stopSessionHeartbeat();

        new AlertDialog.Builder(this)
                .setTitle("Koneksi Diputuskan")
                .setMessage("Sesi login Anda di Molly Web telah berakhir atau Anda telah logout. Demi keamanan, sesi chat di aplikasi Android otomatis diputuskan.")
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    finish();
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!apiClient.isConfigured()) {
            handleSessionExpired("Sesi tidak terkonfigurasi");
            return;
        }
        startSessionHeartbeat();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSessionHeartbeat();
    }

    private void startSessionHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable);
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS);
    }

    private void stopSessionHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable);
    }
}
