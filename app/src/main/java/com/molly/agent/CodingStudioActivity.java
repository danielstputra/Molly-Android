package com.molly.agent;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.molly.agent.api.MollyApiClient;

import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CodingStudioActivity extends AppCompatActivity {

    private MollyApiClient apiClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private ImageView btnBack;
    private ImageView btnRefresh;
    private ProgressBar pbLoading;

    private TextView tabDiff;
    private TextView tabWorktrees;
    private TextView tabAgents;

    private ScrollView viewTabDiff;
    private LinearLayout viewTabWorktrees;
    private ScrollView viewTabAgents;

    // Tab 1 UI
    private TextView tvGitStatusHeader;
    private TextView tvGitFilesSummary;
    private EditText etCommitMessage;
    private MaterialButton btnCommit;
    private MaterialButton btnDiscardAll;
    private TextView tvDiffContent;

    // Tab 2 UI
    private EditText etWorktreeName;
    private MaterialButton btnCreateWorktree;
    private LinearLayout llWorktreeListContainer;

    // Tab 3 UI
    private LinearLayout llProvidersListContainer;
    private EditText etCliCommand;
    private MaterialButton btnRunCli;
    private TextView tvCliOutput;

    private String currentTab = "diff";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coding_studio);

        apiClient = MollyApiClient.getInstance(this);

        initViews();
        setupListeners();

        loadAllStatus();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnRefresh = findViewById(R.id.btnRefresh);
        pbLoading = findViewById(R.id.pbLoading);

        tabDiff = findViewById(R.id.tabDiff);
        tabWorktrees = findViewById(R.id.tabWorktrees);
        tabAgents = findViewById(R.id.tabAgents);

        viewTabDiff = findViewById(R.id.viewTabDiff);
        viewTabWorktrees = findViewById(R.id.viewTabWorktrees);
        viewTabAgents = findViewById(R.id.viewTabAgents);

        tvGitStatusHeader = findViewById(R.id.tvGitStatusHeader);
        tvGitFilesSummary = findViewById(R.id.tvGitFilesSummary);
        etCommitMessage = findViewById(R.id.etCommitMessage);
        btnCommit = findViewById(R.id.btnCommit);
        btnDiscardAll = findViewById(R.id.btnDiscardAll);
        tvDiffContent = findViewById(R.id.tvDiffContent);

        etWorktreeName = findViewById(R.id.etWorktreeName);
        btnCreateWorktree = findViewById(R.id.btnCreateWorktree);
        llWorktreeListContainer = findViewById(R.id.llWorktreeListContainer);

        llProvidersListContainer = findViewById(R.id.llProvidersListContainer);
        etCliCommand = findViewById(R.id.etCliCommand);
        btnRunCli = findViewById(R.id.btnRunCli);
        tvCliOutput = findViewById(R.id.tvCliOutput);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnRefresh.setOnClickListener(v -> loadAllStatus());

        tabDiff.setOnClickListener(v -> switchTab("diff"));
        tabWorktrees.setOnClickListener(v -> switchTab("worktrees"));
        tabAgents.setOnClickListener(v -> switchTab("agents"));

        btnCommit.setOnClickListener(v -> handleCommit());
        btnDiscardAll.setOnClickListener(v -> handleDiscardAll());
        btnCreateWorktree.setOnClickListener(v -> handleCreateWorktree());
        btnRunCli.setOnClickListener(v -> handleExecuteCli());
    }

    private void switchTab(String tab) {
        currentTab = tab;
        tabDiff.setBackgroundTintList(android.content.res.ColorStateList.valueOf("diff".equals(tab) ? 0xFF0284C7 : 0xFF1A1E26));
        tabDiff.setTextColor("diff".equals(tab) ? 0xFFFFFFFF : 0xFF9CA3AF);

        tabWorktrees.setBackgroundTintList(android.content.res.ColorStateList.valueOf("worktrees".equals(tab) ? 0xFF0284C7 : 0xFF1A1E26));
        tabWorktrees.setTextColor("worktrees".equals(tab) ? 0xFFFFFFFF : 0xFF9CA3AF);

        tabAgents.setBackgroundTintList(android.content.res.ColorStateList.valueOf("agents".equals(tab) ? 0xFF0284C7 : 0xFF1A1E26));
        tabAgents.setTextColor("agents".equals(tab) ? 0xFFFFFFFF : 0xFF9CA3AF);

        viewTabDiff.setVisibility("diff".equals(tab) ? View.VISIBLE : View.GONE);
        viewTabWorktrees.setVisibility("worktrees".equals(tab) ? View.VISIBLE : View.GONE);
        viewTabAgents.setVisibility("agents".equals(tab) ? View.VISIBLE : View.GONE);
    }

    private void loadAllStatus() {
        pbLoading.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                String serverUrl = apiClient.getServerUrl();
                OkHttpClient client = apiClient.getOkHttpClient();
                Request request = new Request.Builder()
                        .url(serverUrl + "/api/system/t3code/status")
                        .addHeader("Authorization", "Bearer " + apiClient.getToken())
                        .get()
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JsonObject root = gson.fromJson(body, JsonObject.class);
                    if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                        JsonObject data = root.getAsJsonObject("data");
                        mainHandler.post(() -> updateStatusUi(data));
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, "Gagal memuat status: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                mainHandler.post(() -> pbLoading.setVisibility(View.GONE));
            }
        }).start();

        loadDiff();
    }

    private void updateStatusUi(JsonObject data) {
        // 1. Git Status
        if (data.has("git") && data.get("git").isJsonObject()) {
            JsonObject git = data.getAsJsonObject("git");
            boolean isClean = git.has("clean") && git.get("clean").getAsBoolean();
            int modified = git.has("modified") && git.get("modified").isJsonArray() ? git.getAsJsonArray("modified").size() : 0;
            int untracked = git.has("untracked") && git.get("untracked").isJsonArray() ? git.getAsJsonArray("untracked").size() : 0;

            tvGitStatusHeader.setText(isClean ? "Status Git: Clean Working Tree ✅" : "Status Git: " + modified + " Berkas Dimodifikasi ⚠️");
            tvGitStatusHeader.setTextColor(isClean ? 0xFF34D399 : 0xFFFBBF24);
            tvGitFilesSummary.setText(modified + " berkas dimodifikasi • " + untracked + " berkas baru (untracked)");
        }

        // 2. Worktrees List
        llWorktreeListContainer.removeAllViews();
        if (data.has("worktrees") && data.get("worktrees").isJsonArray()) {
            JsonArray wtList = data.getAsJsonArray("worktrees");
            for (int i = 0; i < wtList.size(); i++) {
                if (!wtList.get(i).isJsonObject()) continue;
                JsonObject wt = wtList.get(i).getAsJsonObject();
                String branch = wt.has("branch") ? wt.get("branch").getAsString() : "detached";
                String path = wt.has("path") ? wt.get("path").getAsString() : "-";
                String commit = wt.has("commit") ? wt.get("commit").getAsString() : "-";

                View item = getLayoutInflater().inflate(R.layout.item_workspace_file_row, llWorktreeListContainer, false);
                TextView tvName = item.findViewById(R.id.tvFileName);
                TextView tvPath = item.findViewById(R.id.tvFilePath);
                TextView tvIcon = item.findViewById(R.id.tvFileIcon);

                tvName.setText("🌿 " + branch);
                tvName.setTextColor(0xFF38BDF8);
                tvPath.setText("Commit: " + commit + " • " + path);
                if (tvIcon != null) tvIcon.setText("🌿");

                llWorktreeListContainer.addView(item);
            }
        }

        // 3. Providers List
        llProvidersListContainer.removeAllViews();
        if (data.has("providers") && data.get("providers").isJsonObject()) {
            JsonObject provs = data.getAsJsonObject("providers");
            for (Map.Entry<String, JsonElement> entry : provs.entrySet()) {
                String name = entry.getKey();
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject pinfo = entry.getValue().getAsJsonObject();
                boolean installed = pinfo.has("installed") && pinfo.get("installed").getAsBoolean();
                String ver = pinfo.has("version") && !pinfo.get("version").isJsonNull() ? pinfo.get("version").getAsString() : (installed ? "Terpasang" : "Tidak Ditemukan");
                String pth = pinfo.has("path") && !pinfo.get("path").isJsonNull() ? pinfo.get("path").getAsString() : "-";

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setBackgroundResource(R.drawable.bg_widget_card);
                int p = (int) (10 * getResources().getDisplayMetrics().density);
                row.setPadding(p, p, p, p);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
                row.setLayoutParams(lp);

                TextView tvPName = new TextView(this);
                tvPName.setText((installed ? "🟢 " : "⚪ ") + name);
                tvPName.setTextColor(0xFFFFFFFF);
                tvPName.setTextSize(14);
                tvPName.setTypeface(null, android.graphics.Typeface.BOLD);
                row.addView(tvPName);

                TextView tvPVer = new TextView(this);
                tvPVer.setText("Versi: " + ver + "\nJalur: " + pth);
                tvPVer.setTextColor(0xFF94A3B8);
                tvPVer.setTextSize(11);
                row.addView(tvPVer);

                llProvidersListContainer.addView(row);
            }
        }
    }

    private void loadDiff() {
        new Thread(() -> {
            try {
                String serverUrl = apiClient.getServerUrl();
                OkHttpClient client = apiClient.getOkHttpClient();
                Request request = new Request.Builder()
                        .url(serverUrl + "/api/system/t3code/diff")
                        .addHeader("Authorization", "Bearer " + apiClient.getToken())
                        .get()
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JsonObject root = gson.fromJson(body, JsonObject.class);
                    if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                        JsonObject d = root.getAsJsonObject("data");
                        String diff = d.has("diff") ? d.get("diff").getAsString() : "";
                        String stat = d.has("stat") ? d.get("stat").getAsString() : "";
                        final String display = (!stat.isEmpty() ? stat + "\n\n" : "") + (!diff.isEmpty() ? diff : "(Tidak ada perbedaan kode aktif)");
                        mainHandler.post(() -> tvDiffContent.setText(display));
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void handleCommit() {
        String msg = etCommitMessage.getText().toString().trim();
        if (msg.isEmpty()) {
            Toast.makeText(this, "Ketik pesan commit terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }

        pbLoading.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                String serverUrl = apiClient.getServerUrl();
                OkHttpClient client = apiClient.getOkHttpClient();
                JsonObject payload = new JsonObject();
                payload.addProperty("message", msg);

                Request request = new Request.Builder()
                        .url(serverUrl + "/api/system/t3code/commit")
                        .addHeader("Authorization", "Bearer " + apiClient.getToken())
                        .post(RequestBody.create(payload.toString(), MediaType.parse("application/json")))
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    mainHandler.post(() -> {
                        Toast.makeText(this, "Perubahan berhasil dicommit ke Git!", Toast.LENGTH_SHORT).show();
                        etCommitMessage.setText("");
                        loadAllStatus();
                    });
                } else {
                    mainHandler.post(() -> Toast.makeText(this, "Gagal commit", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                mainHandler.post(() -> pbLoading.setVisibility(View.GONE));
            }
        }).start();
    }

    private void handleDiscardAll() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Discard Perubahan")
                .setMessage("Yakin ingin membatalkan semua perubahan kode yang belum dicommit?")
                .setPositiveButton("Batalkan Perubahan", (d, w) -> {
                    pbLoading.setVisibility(View.VISIBLE);
                    new Thread(() -> {
                        try {
                            String serverUrl = apiClient.getServerUrl();
                            OkHttpClient client = apiClient.getOkHttpClient();
                            JsonObject payload = new JsonObject();

                            Request request = new Request.Builder()
                                    .url(serverUrl + "/api/system/t3code/discard")
                                    .addHeader("Authorization", "Bearer " + apiClient.getToken())
                                    .post(RequestBody.create(payload.toString(), MediaType.parse("application/json")))
                                    .build();

                            Response response = client.newCall(request).execute();
                            if (response.isSuccessful()) {
                                mainHandler.post(() -> {
                                    Toast.makeText(this, "Perubahan telah dibatalkan.", Toast.LENGTH_SHORT).show();
                                    loadAllStatus();
                                });
                            }
                        } catch (Exception e) {
                            mainHandler.post(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        } finally {
                            mainHandler.post(() -> pbLoading.setVisibility(View.GONE));
                        }
                    }).start();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void handleCreateWorktree() {
        String name = etWorktreeName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Ketik nama worktree baru", Toast.LENGTH_SHORT).show();
            return;
        }

        pbLoading.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                String serverUrl = apiClient.getServerUrl();
                OkHttpClient client = apiClient.getOkHttpClient();
                JsonObject payload = new JsonObject();
                payload.addProperty("name", name);
                payload.addProperty("base_branch", "main");

                Request request = new Request.Builder()
                        .url(serverUrl + "/api/system/t3code/worktrees")
                        .addHeader("Authorization", "Bearer " + apiClient.getToken())
                        .post(RequestBody.create(payload.toString(), MediaType.parse("application/json")))
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    mainHandler.post(() -> {
                        Toast.makeText(this, "Worktree berhasil dibuat!", Toast.LENGTH_SHORT).show();
                        etWorktreeName.setText("");
                        loadAllStatus();
                    });
                } else {
                    mainHandler.post(() -> Toast.makeText(this, "Gagal membuat worktree", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                mainHandler.post(() -> pbLoading.setVisibility(View.GONE));
            }
        }).start();
    }

    private void handleExecuteCli() {
        String cmd = etCliCommand.getText().toString().trim();
        if (cmd.isEmpty()) return;

        tvCliOutput.setText("Mengeksekusi: " + cmd + "...");
        pbLoading.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                String serverUrl = apiClient.getServerUrl();
                OkHttpClient client = apiClient.getOkHttpClient();
                JsonObject payload = new JsonObject();
                payload.addProperty("provider", "auto");
                payload.addProperty("command", cmd);
                payload.addProperty("timeout_seconds", 60);

                Request request = new Request.Builder()
                        .url(serverUrl + "/api/system/t3code/execute")
                        .addHeader("Authorization", "Bearer " + apiClient.getToken())
                        .post(RequestBody.create(payload.toString(), MediaType.parse("application/json")))
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JsonObject root = gson.fromJson(body, JsonObject.class);
                    if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                        JsonObject d = root.getAsJsonObject("data");
                        String out = d.has("stdout") ? d.get("stdout").getAsString() : "";
                        String err = d.has("stderr") ? d.get("stderr").getAsString() : "";
                        final String res = !out.isEmpty() ? out : (!err.isEmpty() ? err : "(Selesai)");
                        mainHandler.post(() -> tvCliOutput.setText(res));
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> tvCliOutput.setText("Gagal: " + e.getMessage()));
            } finally {
                mainHandler.post(() -> pbLoading.setVisibility(View.GONE));
            }
        }).start();
    }
}
