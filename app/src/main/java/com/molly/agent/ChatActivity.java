package com.molly.agent;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.graphics.Color;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.molly.agent.ui.MessageAdapter;
import com.molly.agent.adapter.SessionDrawerAdapter;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.ArtifactItem;
import com.molly.agent.model.AttachmentItem;
import com.molly.agent.model.ChatMessage;
import com.molly.agent.model.ChatDisplayItem;
import com.molly.agent.model.ChatSessionItem;
import com.molly.agent.model.SessionHistoryManager;
import com.molly.agent.ui.ArtifactPreviewDialogHelper;
import com.molly.agent.ui.MollyDialog;

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
    private SwipeRefreshLayout swipeRefreshChat;
    private LinearLayout llThinking;
    private android.animation.ObjectAnimator thinkingAnimator;
    private EditText etMessage;
    private FloatingActionButton btnSend;

    // Conversation Reading Failed / Reread Banner (Gambar 2)
    private View llRereadBanner;
    private TextView tvRereadErrorMsg;
    private TextView btnRereadConversation;

    // Status Monitoring Token & Context Usage
    private View btnContextStatus;
    private TextView tvToolbarContextPercent;
    private com.molly.agent.model.ContextUsageInfo currentContextUsageInfo = new com.molly.agent.model.ContextUsageInfo();

    // Segmen / Linimasa Percakapan & Floating Scroll Bottom
    private com.molly.agent.ui.ChatSegmentNavigatorHelper segmentNavigatorHelper;

    // Action pills seperti Gambar 2
    private TextView btnSelectAgent;
    private TextView btnSelectApproval;
    private TextView btnSelectModel;

    // Lampiran Berkas (Attachments)
    private final List<AttachmentItem> pendingAttachments = new ArrayList<>();
    private HorizontalScrollView hsvAttachmentStaging;
    private LinearLayout llAttachmentStagingContainer;

    // Komponen Drawer Sesi
    private MaterialButton btnDrawerNewSession;
    private TextView tabActiveSessions;
    private TextView tabArchivedSessions;
    private RecyclerView rvDrawerSessions;
    private TextView tvEmptySessions;
    private SessionDrawerAdapter drawerAdapter;
    private boolean showArchived = false;
    private boolean isProgrammaticChange = false;

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
                        if (!isPolling) {
                            fetchLatestMessages(false);
                        }
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

    private final androidx.activity.result.ActivityResultLauncher<Intent> schedulerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String targetThreadId = result.getData().getStringExtra(ScheduledTasksActivity.EXTRA_OPEN_SESSION_ID);
                    if (targetThreadId != null && !targetThreadId.isEmpty()) {
                        stopPolling();
                        apiClient.switchSession(targetThreadId);
                        messageAdapter.setItems(new ArrayList<>());
        if (segmentNavigatorHelper != null) segmentNavigatorHelper.updateSegments(null);
                        if (segmentNavigatorHelper != null) segmentNavigatorHelper.updateSegments(null);
                        Toast.makeText(this, "Membuka sesi eksekusi tugas...", Toast.LENGTH_SHORT).show();
                        fetchLatestMessages(true);
                        refreshContextUsageSilently();
                    }
                }
            });

    private final ActivityResultLauncher<String> pickMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) handleAttachmentPicked(uri, "image");
            });

    private final ActivityResultLauncher<String[]> pickDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) handleAttachmentPicked(uri, "document");
            });

    private final ActivityResultLauncher<String> pickAudioLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) handleAttachmentPicked(uri, "audio");
            });

    private final ActivityResultLauncher<String> pickVideoLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) handleAttachmentPicked(uri, "video");
            });

    private final ActivityResultLauncher<String> pickAnyFileLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) handleAttachmentPicked(uri, "file");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Validasi integritas aplikasi saat runtime (Anti-Clone, Anti-Tamper, Anti-Debug)
        if (!com.molly.agent.security.AppSecurityGuard.enforceSecurity(this)) {
            return;
        }

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
        swipeRefreshChat = findViewById(R.id.swipeRefreshChat);
        if (swipeRefreshChat != null) {
            swipeRefreshChat.setColorSchemeColors(
                    Color.parseColor("#38BDF8"),
                    Color.parseColor("#EAB308"),
                    Color.parseColor("#3B82F6")
            );
            swipeRefreshChat.setProgressBackgroundColorSchemeColor(Color.parseColor("#1E293B"));
            swipeRefreshChat.setOnRefreshListener(() -> {
                fetchLatestMessages(false);
                refreshContextUsageSilently();
            });
        }
        llThinking = findViewById(R.id.llThinking);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        btnSelectAgent = findViewById(R.id.btnSelectAgent);
        btnSelectApproval = findViewById(R.id.btnSelectApproval);
        btnSelectModel = findViewById(R.id.btnSelectModel);

        // Banner Reread Conversation (Gambar 2)
        llRereadBanner = findViewById(R.id.llRereadBanner);
        tvRereadErrorMsg = findViewById(R.id.tvRereadErrorMsg);
        btnRereadConversation = findViewById(R.id.btnRereadConversation);
        if (btnRereadConversation != null) {
            btnRereadConversation.setOnClickListener(v -> rereadConversation());
        }

        // Status Monitoring Token & Context Usage
        btnContextStatus = findViewById(R.id.btnContextStatus);
        tvToolbarContextPercent = findViewById(R.id.tvToolbarContextPercent);
        if (btnContextStatus != null) {
            btnContextStatus.setOnClickListener(v -> showContextUsageDialog());
        }

        // Inisialisasi komponen Drawer
        btnDrawerNewSession = findViewById(R.id.btnDrawerNewSession);
        tabActiveSessions = findViewById(R.id.tabActiveSessions);
        tabArchivedSessions = findViewById(R.id.tabArchivedSessions);
        rvDrawerSessions = findViewById(R.id.rvDrawerSessions);
        tvEmptySessions = findViewById(R.id.tvEmptySessions);

        hsvAttachmentStaging = findViewById(R.id.hsvAttachmentStaging);
        llAttachmentStagingContainer = findViewById(R.id.llAttachmentStagingContainer);

        rvDrawerSessions.setLayoutManager(new LinearLayoutManager(this));

        tvAgentName.setText(apiClient.getAgentName());

        messageAdapter = new MessageAdapter();
        messageAdapter.setWidgetActionListener(this::sendWidgetCustomMessage);
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
        messageAdapter.setOnArtifactActionListener(new MessageAdapter.OnArtifactActionListener() {
            @Override
            public void onPreview(ArtifactItem item) {
                ArtifactPreviewDialogHelper.show(ChatActivity.this, apiClient, item, new ArtifactPreviewDialogHelper.OnArtifactActionListener() {
                    @Override
                    public void onDownload(ArtifactItem it) {
                        downloadArtifact(it);
                    }

                    @Override
                    public void onSave(ArtifactItem it) {
                        saveArtifactToWorkspace(it);
                    }
                });
            }

            @Override
            public void onDownload(ArtifactItem item) {
                downloadArtifact(item);
            }

            @Override
            public void onSave(ArtifactItem item) {
                saveArtifactToWorkspace(item);
            }
        });
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(messageAdapter);

        // Inisialisasi Linimasa Segmen Percakapan & Floating Preview Card (Screenshot Match)
        View llSegmentNavigator = findViewById(R.id.llSegmentNavigator);
        LinearLayout llSegmentNodes = findViewById(R.id.llSegmentNodes);
        View btnSegmentPrev = findViewById(R.id.btnSegmentPrev);
        View btnSegmentNext = findViewById(R.id.btnSegmentNext);
        View btnScrollBottom = findViewById(R.id.btnScrollBottom);
        View cardSegmentPreview = findViewById(R.id.cardSegmentPreview);
        TextView tvPreviewTitle = findViewById(R.id.tvPreviewTitle);
        TextView tvPreviewSnippet = findViewById(R.id.tvPreviewSnippet);
        TextView tvPreviewCounter = findViewById(R.id.tvPreviewCounter);
        TextView tvPreviewTime = findViewById(R.id.tvPreviewTime);

        segmentNavigatorHelper = new com.molly.agent.ui.ChatSegmentNavigatorHelper(
                this,
                rvMessages,
                layoutManager,
                llSegmentNavigator,
                llSegmentNodes,
                btnSegmentPrev,
                btnSegmentNext,
                btnScrollBottom,
                cardSegmentPreview,
                tvPreviewTitle,
                tvPreviewSnippet,
                tvPreviewCounter,
                tvPreviewTime
        );

        // Click listeners
        btnMenu.setOnClickListener(v -> {
            loadDrawerSessions();
            drawerLayout.openDrawer(GravityCompat.START);
        });

        ImageView btnDocuments = findViewById(R.id.btnDocuments);
        if (btnDocuments != null) {
            btnDocuments.setOnClickListener(v -> startActivity(new Intent(this, PersonalSpaceActivity.class)));
        }

        btnClearChat.setOnClickListener(v -> showClearChatConfirmationDialog());
        btnDisconnect.setOnClickListener(v -> showDisconnectConfirmationDialog());
        btnSend.setOnClickListener(v -> {
            if (isAgentGenerating) {
                handleCancelGeneration();
            } else {
                handleSendMessage();
            }
        });

        // Event klik untuk action pills (Gambar 2)
        btnSelectAgent.setOnClickListener(v -> showAgentPickerDialog());
        btnSelectApproval.setOnClickListener(v -> showApprovalModeDialog());
        btnSelectModel.setOnClickListener(v -> showModelPickerDialog());

        // Tombol + di sebelah kiri input pesan (Video 1 & 2)
        ImageView btnAddAttachment = findViewById(R.id.btnAddAttachment);
        if (btnAddAttachment != null) {
            btnAddAttachment.setOnClickListener(v -> showAttachmentOptionsDialog());
        }

        // Listener teks ketikan pengguna untuk trigger popup @mention otomatis
        etMessage.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isProgrammaticChange) return;
                if (s != null && s.length() > 0) {
                    int cursor = etMessage.getSelectionStart();
                    if (cursor > 0 && s.charAt(cursor - 1) == '@') {
                        // Buka popup mention picker (Skills, Agents, Files)
                        com.molly.agent.ui.MentionPickerHelper.show(ChatActivity.this, apiClient, "", item -> {
                            insertMentionIntoInput(item.name);
                        });
                    }
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

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

        View btnDrawerWorkspaceFiles = findViewById(R.id.btnDrawerWorkspaceFiles);
        if (btnDrawerWorkspaceFiles != null) {
            btnDrawerWorkspaceFiles.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                startActivity(new Intent(this, PersonalSpaceActivity.class));
            });
        }

        View btnDrawerScheduler = findViewById(R.id.btnDrawerScheduler);
        if (btnDrawerScheduler != null) {
            btnDrawerScheduler.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                schedulerLauncher.launch(new Intent(this, ScheduledTasksActivity.class));
            });
        }

        View btnDrawerCodingStudio = findViewById(R.id.btnDrawerCodingStudio);
        if (btnDrawerCodingStudio != null) {
            btnDrawerCodingStudio.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                startActivity(new Intent(this, CodingStudioActivity.class));
            });
        }

        View btnDrawerProviders = findViewById(R.id.btnDrawerProviders);
        if (btnDrawerProviders != null) {
            btnDrawerProviders.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                startActivity(new Intent(this, ModelProvidersActivity.class));
            });
        }

        View btnDrawerStatus = findViewById(R.id.btnDrawerStatus);
        if (btnDrawerStatus != null) {
            btnDrawerStatus.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                showContextUsageDialog();
            });
        }

        View btnDrawerServerConfig = findViewById(R.id.btnDrawerServerConfig);
        if (btnDrawerServerConfig != null) {
            btnDrawerServerConfig.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                showServerConfigDialog();
            });
        }

        View btnDrawerCheckUpdate = findViewById(R.id.btnDrawerCheckUpdate);
        if (btnDrawerCheckUpdate != null) {
            btnDrawerCheckUpdate.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                com.molly.agent.update.AppUpdateManager.checkForUpdate(this, apiClient, true);
            });
        }

        View btnDrawerChangelog = findViewById(R.id.btnDrawerChangelog);
        TextView tvDrawerChangelogBadge = findViewById(R.id.tvDrawerChangelogBadge);
        if (tvDrawerChangelogBadge != null) {
            tvDrawerChangelogBadge.setText(com.molly.agent.update.AppUpdateManager.getCurrentVersionName(this) + " ❯");
        }
        if (btnDrawerChangelog != null) {
            btnDrawerChangelog.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                com.molly.agent.ui.ChangelogDialogHelper.show(this, apiClient);
            });
        }

        View btnDrawerAbout = findViewById(R.id.btnDrawerAbout);
        if (btnDrawerAbout != null) {
            btnDrawerAbout.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                com.molly.agent.ui.AboutAppDialogHelper.show(this, apiClient);
            });
        }

        // Auto-check pembaruan aplikasi secara realtime di latar belakang
        com.molly.agent.update.AppUpdateManager.checkForUpdate(this, apiClient, false);

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

        // Ambil awal monitoring status token & context usage
        refreshContextUsageSilently();

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
        if (segmentNavigatorHelper != null) segmentNavigatorHelper.updateSegments(null);
        drawerLayout.closeDrawer(GravityCompat.START);

        Toast.makeText(this, "Membuka: " + session.getTitle(), Toast.LENGTH_SHORT).show();
        fetchLatestMessages(true);
        refreshContextUsageSilently();
    }

    @Override
    public void onSessionTogglePin(ChatSessionItem session) {
        boolean newPinnedState = !session.isPinned();
        session.setPinned(newPinnedState);
        sessionManager.setPinned(session.getId(), newPinnedState);

        apiClient.updateSession(session.getId(), null, newPinnedState, new MollyApiClient.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {}
            @Override
            public void onError(String error) {}
        });

        Toast.makeText(this, newPinnedState ? "Percakapan disematkan ke atas 📌" : "Sematan percakapan dilepas", Toast.LENGTH_SHORT).show();
        loadDrawerSessions();
    }

    @Override
    public void onSessionRename(ChatSessionItem session) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        int padV = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        container.setPadding(padH, padV, padH, padV);

        TextView tvDesc = new TextView(this);
        tvDesc.setText("Beri judul yang singkat dan mudah dikenali");
        tvDesc.setTextColor(0xFF9CA3AF);
        tvDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
        tvDesc.setLayoutParams(descLp);
        container.addView(tvDesc);

        final EditText input = new EditText(this);
        input.setText(session.getTitle());
        input.setSelectAllOnFocus(true);
        input.setTextColor(0xFFFFFFFF);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        input.setBackgroundResource(R.drawable.bg_widget_radio_item);
        int inputPad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
        input.setPadding(inputPad, inputPad, inputPad, inputPad);
        container.addView(input);

        new com.molly.agent.ui.MollyDialog.Builder(this)
                .setTitle("Ubah Nama Percakapan")
                .setView(container)
                .setPositiveButton("Simpan", (dialog, which) -> {
                    String newTitle = input.getText() != null ? input.getText().toString().trim() : "";
                    if (newTitle.isEmpty()) {
                        Toast.makeText(this, "Judul percakapan tidak boleh kosong", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    session.setTitle(newTitle);
                    sessionManager.renameSession(session.getId(), newTitle);

                    apiClient.updateSession(session.getId(), newTitle, null, new MollyApiClient.ApiCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean result) {}
                        @Override
                        public void onError(String error) {}
                    });

                    Toast.makeText(this, "Nama percakapan berhasil diperbarui", Toast.LENGTH_SHORT).show();
                    loadDrawerSessions();
                })
                .setNegativeButton("Batal", null)
                .show();
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
        new com.molly.agent.ui.MollyDialog.Builder(this)
                .setTitle("Hapus Sesi?")
                .setMessage("Hapus percakapan '" + session.getTitle() + "'? Tindakan ini tidak dapat dibatalkan.")
                .setDestructiveButton("Hapus", (dialog, which) -> {
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
        if (segmentNavigatorHelper != null) segmentNavigatorHelper.updateSegments(null);
        drawerLayout.closeDrawer(GravityCompat.START);
        Toast.makeText(this, "Sesi chat baru siap. Ketik pesan untuk memulai.", Toast.LENGTH_SHORT).show();
    }

    private void showClearChatConfirmationDialog() {
        new com.molly.agent.ui.MollyDialog.Builder(this)
                .setTitle("Bersihkan Chat?")
                .setMessage("Apakah Anda yakin ingin mengosongkan tampilan pesan pada sesi saat ini?")
                .setDestructiveButton("Bersihkan", (dialog, which) -> {
                    messageAdapter.setItems(new ArrayList<>());
        if (segmentNavigatorHelper != null) segmentNavigatorHelper.updateSegments(null);
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

        new com.molly.agent.ui.MollyDialog.Builder(this)
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

    private void showAttachmentOptionsDialog() {
        String[] options = {
                "📷 Foto / Gambar",
                "📄 Dokumen & PDF",
                "🎵 Musik & Audio",
                "🎬 Video",
                "📎 Semua Jenis Berkas",
                "📁 Sisipkan Berkas Ruang Kerja",
                "📂 Buka Ruang Kerja Penuh (Personal Space)",
                "💡 @ Sebutkan Skill atau Agen"
        };
        new com.molly.agent.ui.MollyDialog.Builder(this)
                .setTitle("Tambah Konten / Lampiran")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        pickMediaLauncher.launch("image/*");
                    } else if (which == 1) {
                        pickDocumentLauncher.launch(new String[]{
                                "application/pdf",
                                "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "text/*"
                        });
                    } else if (which == 2) {
                        pickAudioLauncher.launch("audio/*");
                    } else if (which == 3) {
                        pickVideoLauncher.launch("video/*");
                    } else if (which == 4) {
                        pickAnyFileLauncher.launch("*/*");
                    } else if (which == 5) {
                        com.molly.agent.ui.WorkspaceFilesDialogHelper.show(this, apiClient, file -> {
                            String current = etMessage.getText() != null ? etMessage.getText().toString() : "";
                            etMessage.setText(current + " [Berkas: " + file.path + "] ");
                            etMessage.setSelection(etMessage.getText().length());
                        });
                    } else if (which == 6) {
                        startActivity(new Intent(this, PersonalSpaceActivity.class));
                    } else if (which == 7) {
                        com.molly.agent.ui.MentionPickerHelper.show(this, apiClient, "", item -> {
                            insertMentionIntoInput(item.name);
                        });
                    }
                })
                .show();
    }

    private void handleAttachmentPicked(Uri uri, String category) {
        String fileName = "attachment";
        long fileSize = 0;
        String mimeType = getContentResolver().getType(uri);

        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {}

        if (mimeType == null || mimeType.isEmpty()) {
            if ("image".equalsIgnoreCase(category)) mimeType = "image/jpeg";
            else if ("audio".equalsIgnoreCase(category)) mimeType = "audio/mpeg";
            else if ("video".equalsIgnoreCase(category)) mimeType = "video/mp4";
            else if ("document".equalsIgnoreCase(category)) mimeType = "application/pdf";
            else mimeType = "application/octet-stream";
        }

        byte[] fileBytes;
        try (java.io.InputStream is = getContentResolver().openInputStream(uri);
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            fileBytes = bos.toByteArray();
            if (fileSize <= 0) fileSize = fileBytes.length;
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membaca berkas: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        final AttachmentItem item = new AttachmentItem(fileName, fileSize, mimeType, category, uri.toString());
        item.setUploading(true);
        pendingAttachments.add(item);
        renderAttachmentStaging();

        final String finalFileName = fileName;
        final String finalMimeType = mimeType;

        apiClient.uploadAttachment(fileBytes, finalFileName, finalMimeType, new MollyApiClient.ApiCallback<AttachmentItem>() {
            @Override
            public void onSuccess(AttachmentItem uploadedItem) {
                item.setId(uploadedItem.getId());
                item.setFileName(uploadedItem.getFileName());
                item.setFileSize(uploadedItem.getFileSize());
                item.setMimeType(uploadedItem.getMimeType());
                item.setCategory(uploadedItem.getCategory());
                item.setImageUrl(uploadedItem.getImageUrl());
                item.setThumbnailUrl(uploadedItem.getThumbnailUrl());
                item.setUploading(false);
                item.setUploaded(true);
                renderAttachmentStaging();
                Toast.makeText(ChatActivity.this, "Lampiran siap: " + finalFileName, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                item.setUploading(false);
                item.setErrorMessage(error);
                renderAttachmentStaging();
                Toast.makeText(ChatActivity.this, "Gagal mengunggah lampiran: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderAttachmentStaging() {
        if (llAttachmentStagingContainer == null || hsvAttachmentStaging == null) return;
        llAttachmentStagingContainer.removeAllViews();

        if (pendingAttachments.isEmpty()) {
            hsvAttachmentStaging.setVisibility(View.GONE);
            return;
        }

        hsvAttachmentStaging.setVisibility(View.VISIBLE);
        for (int i = 0; i < pendingAttachments.size(); i++) {
            final AttachmentItem item = pendingAttachments.get(i);
            View chip = getLayoutInflater().inflate(R.layout.item_attachment_staging_chip, llAttachmentStagingContainer, false);
            TextView tvEmoji = chip.findViewById(R.id.tvAttachmentEmoji);
            ImageView ivThumb = chip.findViewById(R.id.ivAttachmentThumb);
            TextView tvName = chip.findViewById(R.id.tvAttachmentName);
            TextView tvStatus = chip.findViewById(R.id.tvAttachmentStatus);
            View btnRemove = chip.findViewById(R.id.btnRemoveAttachment);

            tvEmoji.setText(item.getCategoryEmoji());
            tvName.setText(item.getFileName());

            if (item.isUploading()) {
                tvStatus.setText("Mengunggah...");
                tvStatus.setTextColor(0xFF38BDF8);
            } else if (item.isUploaded()) {
                tvStatus.setText(item.getFormattedSize() + " • Siap ✓");
                tvStatus.setTextColor(0xFF10B981);
            } else if (item.getErrorMessage() != null) {
                tvStatus.setText("Gagal upload");
                tvStatus.setTextColor(0xFFEF4444);
            }

            if ("image".equalsIgnoreCase(item.getCategory()) && item.getImageUrl() != null && item.getImageUrl().startsWith("data:image")) {
                try {
                    String b64 = item.getImageUrl().substring(item.getImageUrl().indexOf(",") + 1);
                    byte[] dec = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(dec, 0, dec.length);
                    if (bmp != null) {
                        ivThumb.setImageBitmap(bmp);
                        ivThumb.setVisibility(View.VISIBLE);
                        tvEmoji.setVisibility(View.GONE);
                    }
                } catch (Exception ignored) {}
            }

            btnRemove.setOnClickListener(v -> {
                pendingAttachments.remove(item);
                renderAttachmentStaging();
            });

            llAttachmentStagingContainer.addView(chip);
        }
    }

    private void insertMentionIntoInput(String name) {
        if (name == null || name.trim().isEmpty()) return;
        isProgrammaticChange = true;
        try {
            android.text.Editable editable = etMessage.getText();
            String mentionText = "@" + name.trim() + " ";

            if (editable == null || editable.length() == 0) {
                etMessage.setText(mentionText);
                etMessage.setSelection(etMessage.getText().length());
                return;
            }

            int cursor = etMessage.getSelectionStart();
            if (cursor < 0) cursor = editable.length();

            // Cari karakter '@' terdekat sebelum posisi kursor
            String textBeforeCursor = editable.subSequence(0, cursor).toString();
            int lastAt = textBeforeCursor.lastIndexOf('@');

            if (lastAt >= 0) {
                editable.replace(lastAt, cursor, mentionText);
                etMessage.setSelection(lastAt + mentionText.length());
            } else {
                editable.insert(cursor, mentionText);
                etMessage.setSelection(cursor + mentionText.length());
            }
        } finally {
            isProgrammaticChange = false;
        }
    }

    private void showModelPickerDialog() {
        com.molly.agent.ui.ModelSelectorDialogHelper.show(
                this,
                apiClient,
                apiClient.getSelectedModelId(),
                (chosenId, chosenName) -> {
                    apiClient.setSelectedModel(chosenId, chosenName);
                    updateActionPillsUi();
                    Toast.makeText(this, "Model AI dialihkan ke: " + chosenName, Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void showApprovalModeDialog() {
        String[] modes = new String[]{"Kepercayaan Penuh (Otomatis)", "Persetujuan Manual"};
        int selectedIndex = ("default".equalsIgnoreCase(apiClient.getSelectedApprovalMode()) || "manual".equalsIgnoreCase(apiClient.getSelectedApprovalMode())) ? 1 : 0;

        new MollyDialog.Builder(this)
                .setTitle("Mode Persetujuan Tool")
                .setMessage("Tentukan apakah agen dapat menjalankan tool secara mandiri atau membutuhkan persetujuan Anda setiap kali.")
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

            com.molly.agent.ui.MollyDialog.Builder builder = new com.molly.agent.ui.MollyDialog.Builder(this)
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

        new com.molly.agent.ui.MollyDialog.Builder(this)
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

    private void downloadArtifact(ArtifactItem item) {
        if (item == null || item.getPath() == null) return;
        Toast.makeText(this, "Mengunduh " + item.getName() + "...", Toast.LENGTH_SHORT).show();
        apiClient.downloadArtifactFile(item.getPath(), item.getName(), new MollyApiClient.ApiCallback<java.io.File>() {
            @Override
            public void onSuccess(java.io.File file) {
                Toast.makeText(ChatActivity.this, "Berhasil diunduh ke folder Download: " + file.getName(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Gagal mengunduh: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveArtifactToWorkspace(ArtifactItem item) {
        if (item == null || item.getPath() == null) return;
        com.molly.agent.ui.SaveArtifactDialogHelper.show(this, apiClient, item, savedPath -> {
            // Berhasil disimpan ke direktori yang dipilih oleh pengguna
        });
    }

    private void handleSendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty() && pendingAttachments.isEmpty()) return;

        for (AttachmentItem att : pendingAttachments) {
            if (att.isUploading()) {
                Toast.makeText(this, "Mohon tunggu hingga berkas selesai diunggah...", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        etMessage.setText("");

        final List<AttachmentItem> attachmentsToSend = new ArrayList<>(pendingAttachments);
        pendingAttachments.clear();
        renderAttachmentStaging();

        if (text.isEmpty()) {
            text = "Berikut lampiran berkas:";
        }

        // Tampilkan pesan user langsung di RecyclerView secara optimis
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String time = sdf.format(new Date());
        ChatDisplayItem localMsg = new ChatDisplayItem(UUID.randomUUID().toString(), ChatDisplayItem.TYPE_USER, "user", text, time);
        localMsg.setAttachments(attachmentsToSend);
        messageAdapter.addItem(localMsg);
        rvMessages.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
        if (segmentNavigatorHelper != null) {
            segmentNavigatorHelper.updateSegments(messageAdapter.getItems());
        }

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

        // Kirim prompt ke server Molly bersama lampiran berkas
        final String promptToSend = text;
        apiClient.sendPrompt(promptToSend, attachmentsToSend, new MollyApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                // Catat/perbarui sesi dengan ID yang valid (termasuk jika dibuat secara auto-healing)
                sessionManager.recordOrUpdateSession(
                        apiClient.getSessionId(),
                        titleSnippet,
                        promptToSend,
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

    /**
     * Mengirim pesan aksi formulir / widget interaktif ke agen secara langsung
     */
    public void sendWidgetCustomMessage(String text) {
        if (text == null || text.trim().isEmpty()) return;
        final String actionText = text.trim();

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String time = sdf.format(new Date());
        ChatDisplayItem localMsg = new ChatDisplayItem(UUID.randomUUID().toString(), ChatDisplayItem.TYPE_USER, "user", actionText, time);
        messageAdapter.addItem(localMsg);
        rvMessages.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
        if (segmentNavigatorHelper != null) {
            segmentNavigatorHelper.updateSegments(messageAdapter.getItems());
        }

        startThinking();

        String titleSnippet = actionText.length() > 30 ? actionText.substring(0, 30) + "..." : actionText;
        if (!apiClient.getSessionId().isEmpty()) {
            sessionManager.recordOrUpdateSession(
                    apiClient.getSessionId(),
                    titleSnippet,
                    actionText,
                    apiClient.getAgentName(),
                    apiClient.getSelectedModelName()
            );
        }

        apiClient.sendPrompt(actionText, new MollyApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                sessionManager.recordOrUpdateSession(
                        apiClient.getSessionId(),
                        titleSnippet,
                        actionText,
                        apiClient.getAgentName(),
                        apiClient.getSelectedModelName()
                );
                startPolling();
            }

            @Override
            public void onError(String error) {
                stopThinking();
                Toast.makeText(ChatActivity.this, "Gagal mengirim aksi formulir: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void rereadConversation() {
        if (btnRereadConversation != null) {
            btnRereadConversation.setText("Membaca ulang...");
            btnRereadConversation.setEnabled(false);
        }
        startThinking();
        fetchLatestMessages(true);
    }

    private void fetchLatestMessages(boolean scrollToBottom) {
        apiClient.fetchSessionDisplayItems(new MollyApiClient.ApiCallback<List<ChatDisplayItem>>() {
            @Override
            public void onSuccess(List<ChatDisplayItem> list) {
                if (swipeRefreshChat != null) {
                    swipeRefreshChat.setRefreshing(false);
                }
                if (btnRereadConversation != null) {
                    btnRereadConversation.setText("Reread conversation");
                    btnRereadConversation.setEnabled(true);
                }
                if (llRereadBanner != null) {
                    llRereadBanner.setVisibility(View.GONE);
                }

                if (list != null && !list.isEmpty()) {
                    ChatDisplayItem lastItem = list.get(list.size() - 1);
                    boolean isProcessing = false;
                    for (ChatDisplayItem it : list) {
                        if (it.getTurnId() != null && !it.getTurnId().isEmpty()) {
                            lastActiveTurnId = it.getTurnId();
                        }
                        String st = it.getToolStatus();
                        if ("in_progress".equalsIgnoreCase(st) || "waiting".equalsIgnoreCase(st)) {
                            isProcessing = true;
                            break;
                        }
                    }

                    if (!isProcessing && (lastItem.getItemType() == ChatDisplayItem.TYPE_AGENT || "completed".equalsIgnoreCase(lastItem.getToolStatus()))) {
                        stopThinking();
                    }

                    int oldSize = messageAdapter.getItemCount();
                    messageAdapter.setItems(list);
                    if (segmentNavigatorHelper != null) {
                        segmentNavigatorHelper.updateSegments(list);
                    }
                    if (scrollToBottom) {
                        rvMessages.scrollToPosition(list.size() - 1);
                    } else if (oldSize > 0 && list.size() > oldSize) {
                        rvMessages.smoothScrollToPosition(list.size() - 1);
                    }
                }
            }

            @Override
            public void onError(String error) {
                if (swipeRefreshChat != null) {
                    swipeRefreshChat.setRefreshing(false);
                }
                if (btnRereadConversation != null) {
                    btnRereadConversation.setText("Reread conversation");
                    btnRereadConversation.setEnabled(true);
                }
                // Tampilkan banner "Conversation reading failed, please try again. [Reread conversation]"
                if (llRereadBanner != null) {
                    llRereadBanner.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private boolean isAgentGenerating = false;
    private String lastActiveTurnId = null;

    private void startThinking() {
        isAgentGenerating = true;
        llThinking.setVisibility(View.VISIBLE);
        llThinking.setAlpha(1f);

        if (thinkingAnimator == null) {
            thinkingAnimator = android.animation.ObjectAnimator.ofFloat(llThinking, "alpha", 1.0f, 0.45f);
            thinkingAnimator.setDuration(900);
            thinkingAnimator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            thinkingAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        }
        if (!thinkingAnimator.isRunning()) {
            thinkingAnimator.start();
        }

        if (btnSend != null) {
            btnSend.setImageResource(R.drawable.ic_stop);
            btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#EF4444")));
        }
    }

    private void stopThinking() {
        isAgentGenerating = false;
        if (thinkingAnimator != null && thinkingAnimator.isRunning()) {
            thinkingAnimator.cancel();
        }
        llThinking.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            llThinking.setVisibility(View.GONE);
            llThinking.setAlpha(1f);
        }).start();

        if (btnSend != null) {
            btnSend.setImageResource(R.drawable.ic_arrow_up);
            btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F59E0B")));
        }
        refreshContextUsageSilently();
    }

    private void handleCancelGeneration() {
        Toast.makeText(this, "Menghentikan respon agen...", Toast.LENGTH_SHORT).show();
        stopThinking();
        stopPolling();
        apiClient.cancelCurrentTurn(lastActiveTurnId, new MollyApiClient.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                fetchLatestMessages(true);
            }

            @Override
            public void onError(String error) {
                fetchLatestMessages(true);
            }
        });
    }

    private void showContextUsageDialog() {
        com.molly.agent.ui.ContextUsageDialogHelper.show(this, apiClient, currentContextUsageInfo, info -> {
            updateContextUsageToolbar(info);
        });
    }

    private void updateContextUsageToolbar(com.molly.agent.model.ContextUsageInfo info) {
        if (info == null) return;
        if (tvToolbarContextPercent != null) {
            tvToolbarContextPercent.setText(info.percent + "%");
            if (info.percent >= 90) {
                tvToolbarContextPercent.setTextColor(0xFFEF4444);
            } else if (info.percent >= 75) {
                tvToolbarContextPercent.setTextColor(0xFFF59E0B);
            } else {
                tvToolbarContextPercent.setTextColor(0xFF38BDF8);
            }
        }
        TextView tvDrawerStatusBadge = findViewById(R.id.tvDrawerStatusBadge);
        if (tvDrawerStatusBadge != null) {
            tvDrawerStatusBadge.setText(info.percent + "% ❯");
            if (info.percent >= 90) {
                tvDrawerStatusBadge.setTextColor(0xFFEF4444);
            } else if (info.percent >= 75) {
                tvDrawerStatusBadge.setTextColor(0xFFF59E0B);
            } else {
                tvDrawerStatusBadge.setTextColor(0xFF38BDF8);
            }
        }
    }

    private void refreshContextUsageSilently() {
        apiClient.fetchContextUsage(new MollyApiClient.ApiCallback<com.molly.agent.model.ContextUsageInfo>() {
            @Override
            public void onSuccess(com.molly.agent.model.ContextUsageInfo result) {
                if (result != null) {
                    currentContextUsageInfo = result;
                    updateContextUsageToolbar(result);
                }
            }

            @Override
            public void onError(String error) {
                // Abaikan jika offline / sementara
            }
        });
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
        new MollyDialog.Builder(this)
                .setTitle("Putuskan Molly Agent?")
                .setMessage("Apakah Anda yakin ingin memutuskan sambungan dengan server Molly? Sesi ini akan ditutup dan Anda harus scan QR code kembali untuk menghubungkannya.")
                .setDestructiveButton("Ya, Putuskan", (dialog, which) -> {
                    apiClient.notifyDisconnect(new MollyApiClient.ApiCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean result) {}
                        @Override
                        public void onError(String error) {}
                    });
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

        new MollyDialog.Builder(this)
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
        refreshContextUsageSilently();
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
