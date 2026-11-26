package com.molly.agent;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.ArtifactItem;
import com.molly.agent.model.WorkspaceFileItem;
import com.molly.agent.ui.MollyDialog;
import com.molly.agent.ui.ArtifactPreviewDialogHelper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PersonalSpaceActivity extends AppCompatActivity {

    private MollyApiClient apiClient;

    private ImageView btnBack;
    private ImageView btnSearch;
    private ImageView btnNewFolder;
    private ImageView btnRefresh;

    private LinearLayout llSearchContainer;
    private EditText etSearchQuery;
    private ImageView btnClearSearch;

    private TextView tabAllFiles;
    private TextView tabAgentFiles;
    private TextView tabProjectFiles;
    private TextView tabGitChanges;

    // Git Container Views
    private LinearLayout llGitContainer;
    private TextView tvGitBranchBadge;
    private TextView tvGitCommitHash;
    private TextView btnGitRefresh;
    private EditText etGitCommitMsg;
    private TextView btnGitCommit;
    private TextView tvGitFilesSummary;
    private TextView tvGitDiffContent;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private TextView tvBreadcrumbRoot;
    private TextView tvBreadcrumbSub;
    private TextView tvItemCount;

    private LinearLayout llBatchActionBar;
    private TextView tvSelectedCount;
    private TextView btnBatchDelete;

    private CheckBox cbSelectAll;
    private ProgressBar pbLoading;
    private SwipeRefreshLayout swipeRefreshPersonalSpace;
    private RecyclerView rvFileList;
    private LinearLayout llEmptyState;

    private PersonalSpaceFileAdapter adapter;
    private final List<WorkspaceFileItem> currentFileList = new ArrayList<>();
    private final Set<String> selectedPaths = new HashSet<>();

    private String currentDirectory = "/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_space);

        apiClient = new MollyApiClient(this);

        initViews();
        setupListeners();
        loadFiles(currentDirectory, "");
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBackPersonalSpace);
        btnSearch = findViewById(R.id.btnSearchFiles);
        btnNewFolder = findViewById(R.id.btnNewFolder);
        btnRefresh = findViewById(R.id.btnRefreshFiles);

        llSearchContainer = findViewById(R.id.llSearchContainer);
        etSearchQuery = findViewById(R.id.etSearchQuery);
        btnClearSearch = findViewById(R.id.btnClearSearch);

        tabAllFiles = findViewById(R.id.tabAllFiles);
        tabAgentFiles = findViewById(R.id.tabAgentFiles);
        tabProjectFiles = findViewById(R.id.tabProjectFiles);
        tabGitChanges = findViewById(R.id.tabGitChanges);

        llGitContainer = findViewById(R.id.llGitContainer);
        tvGitBranchBadge = findViewById(R.id.tvGitBranchBadge);
        tvGitCommitHash = findViewById(R.id.tvGitCommitHash);
        btnGitRefresh = findViewById(R.id.btnGitRefresh);
        etGitCommitMsg = findViewById(R.id.etGitCommitMsg);
        btnGitCommit = findViewById(R.id.btnGitCommit);
        tvGitFilesSummary = findViewById(R.id.tvGitFilesSummary);
        tvGitDiffContent = findViewById(R.id.tvGitDiffContent);

        tvBreadcrumbRoot = findViewById(R.id.tvBreadcrumbRoot);
        tvBreadcrumbSub = findViewById(R.id.tvBreadcrumbSub);
        tvItemCount = findViewById(R.id.tvItemCount);

        llBatchActionBar = findViewById(R.id.llBatchActionBar);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        btnBatchDelete = findViewById(R.id.btnBatchDelete);

        cbSelectAll = findViewById(R.id.cbSelectAll);
        pbLoading = findViewById(R.id.pbLoading);
        swipeRefreshPersonalSpace = findViewById(R.id.swipeRefreshPersonalSpace);
        if (swipeRefreshPersonalSpace != null) {
            swipeRefreshPersonalSpace.setColorSchemeColors(
                    android.graphics.Color.parseColor("#38BDF8"),
                    android.graphics.Color.parseColor("#EAB308"),
                    android.graphics.Color.parseColor("#3B82F6")
            );
            swipeRefreshPersonalSpace.setProgressBackgroundColorSchemeColor(android.graphics.Color.parseColor("#1E293B"));
            swipeRefreshPersonalSpace.setOnRefreshListener(() -> loadFiles(currentDirectory, etSearchQuery.getText().toString().trim()));
        }
        rvFileList = findViewById(R.id.rvFileList);
        llEmptyState = findViewById(R.id.llEmptyState);

        rvFileList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PersonalSpaceFileAdapter();
        rvFileList.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnSearch.setOnClickListener(v -> {
            if (llSearchContainer.getVisibility() == View.VISIBLE) {
                llSearchContainer.setVisibility(View.GONE);
                etSearchQuery.setText("");
                loadFiles(currentDirectory, "");
            } else {
                llSearchContainer.setVisibility(View.VISIBLE);
                etSearchQuery.requestFocus();
            }
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearchQuery.setText("");
            loadFiles(currentDirectory, "");
        });

        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s != null ? s.toString().trim() : "";
                loadFiles(currentDirectory, q);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnRefresh.setOnClickListener(v -> {
            String q = etSearchQuery.getText() != null ? etSearchQuery.getText().toString().trim() : "";
            loadFiles(currentDirectory, q);
        });

        btnNewFolder.setOnClickListener(v -> showCreateFolderDialog());

        // Breadcrumb clicks
        tvBreadcrumbRoot.setOnClickListener(v -> {
            currentDirectory = "/";
            loadFiles(currentDirectory, "");
        });

        // Quick Access tabs
        tabAllFiles.setOnClickListener(v -> {
            setActiveTab(tabAllFiles);
            currentDirectory = "/";
            loadFiles(currentDirectory, "");
        });

        tabAgentFiles.setOnClickListener(v -> {
            setActiveTab(tabAgentFiles);
            currentDirectory = "/agents";
            loadFiles(currentDirectory, "");
        });

        tabProjectFiles.setOnClickListener(v -> {
            setActiveTab(tabProjectFiles);
            currentDirectory = "/projects";
            loadFiles(currentDirectory, "");
        });

        if (tabGitChanges != null) {
            tabGitChanges.setOnClickListener(v -> {
                setActiveTab(tabGitChanges);
            });
        }
        if (btnGitRefresh != null) {
            btnGitRefresh.setOnClickListener(v -> loadGitStatus());
        }
        if (btnGitCommit != null) {
            btnGitCommit.setOnClickListener(v -> handleGitCommit());
        }

        // Select all CheckBox
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return;
            selectedPaths.clear();
            if (isChecked) {
                for (WorkspaceFileItem item : currentFileList) {
                    selectedPaths.add(item.path);
                }
            }
            updateBatchActionBar();
            adapter.notifyDataSetChanged();
        });

        // Batch Delete button
        btnBatchDelete.setOnClickListener(v -> showBatchDeleteConfirmationDialog());

        // File list item clicks
        adapter.setOnItemClickListener(new PersonalSpaceFileAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(WorkspaceFileItem item) {
                if (item.isDirectory) {
                    currentDirectory = item.path;
                    loadFiles(currentDirectory, "");
                } else {
                    ArtifactItem art = new ArtifactItem(item.path, apiClient.getSessionId());
                    ArtifactPreviewDialogHelper.show(PersonalSpaceActivity.this, apiClient, art, new ArtifactPreviewDialogHelper.OnArtifactActionListener() {
                        @Override
                        public void onDownload(ArtifactItem it) {
                            Toast.makeText(PersonalSpaceActivity.this, "Mengunduh " + it.getName() + "...", Toast.LENGTH_SHORT).show();
                            apiClient.downloadArtifactFile(it.getPath(), it.getName(), new MollyApiClient.ApiCallback<java.io.File>() {
                                @Override
                                public void onSuccess(java.io.File file) {
                                    Toast.makeText(PersonalSpaceActivity.this, "Berhasil diunduh ke folder Download: " + file.getName(), Toast.LENGTH_LONG).show();
                                }

                                @Override
                                public void onError(String error) {
                                    Toast.makeText(PersonalSpaceActivity.this, "Gagal mengunduh: " + error, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        @Override
                        public void onSave(ArtifactItem it) {
                            Toast.makeText(PersonalSpaceActivity.this, "Berkas sudah berada di ruang kerja pribadi", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onSelectChanged(WorkspaceFileItem item, boolean isSelected) {
                if (isSelected) {
                    selectedPaths.add(item.path);
                } else {
                    selectedPaths.remove(item.path);
                }
                cbSelectAll.setChecked(selectedPaths.size() == currentFileList.size() && !currentFileList.isEmpty());
                updateBatchActionBar();
            }

            @Override
            public void onOptionsClick(WorkspaceFileItem item) {
                showFileOptionsBottomSheet(item);
            }
        });
    }

    private void setActiveTab(TextView activeTab) {
        tabAllFiles.setBackgroundTintList(getColorStateList(R.color.card_dark));
        tabAllFiles.setTextColor(0xFF9CA3AF);
        tabAgentFiles.setBackgroundTintList(getColorStateList(R.color.card_dark));
        tabAgentFiles.setTextColor(0xFF9CA3AF);
        tabProjectFiles.setBackgroundTintList(getColorStateList(R.color.card_dark));
        tabProjectFiles.setTextColor(0xFF9CA3AF);
        if (tabGitChanges != null) {
            tabGitChanges.setBackgroundTintList(getColorStateList(R.color.card_dark));
            tabGitChanges.setTextColor(0xFF9CA3AF);
        }

        activeTab.setBackgroundTintList(getColorStateList(R.color.primary_gold_dark));
        activeTab.setTextColor(getColor(R.color.primary_gold));

        if (activeTab == tabGitChanges) {
            if (swipeRefreshPersonalSpace != null) swipeRefreshPersonalSpace.setVisibility(View.GONE);
            if (llEmptyState != null) llEmptyState.setVisibility(View.GONE);
            if (llGitContainer != null) llGitContainer.setVisibility(View.VISIBLE);
            tvBreadcrumbRoot.setText("🌿 Repositori Git");
            tvBreadcrumbSub.setVisibility(View.GONE);
            loadGitStatus();
        } else {
            if (llGitContainer != null) llGitContainer.setVisibility(View.GONE);
            if (swipeRefreshPersonalSpace != null) swipeRefreshPersonalSpace.setVisibility(View.VISIBLE);
            tvBreadcrumbRoot.setText("All files");
        }
    }

    private void loadGitStatus() {
        if (tvGitFilesSummary != null) tvGitFilesSummary.setText("Memuat status repositori Git...");
        new Thread(() -> {
            try {
                String serverUrl = apiClient.getServerUrl();
                OkHttpClient client = apiClient.getOkHttpClient();
                Request request = new Request.Builder()
                        .url(serverUrl + "/api/system/code-studio/status")
                        .addHeader("Authorization", "Bearer " + apiClient.getToken())
                        .get()
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JsonObject root = gson.fromJson(body, JsonObject.class);
                    if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                        JsonObject data = root.getAsJsonObject("data");
                        String branch = data.has("branch") ? data.get("branch").getAsString() : "main";
                        String lastCommit = data.has("last_commit") ? data.get("last_commit").getAsString() : "";
                        int modifiedCount = data.has("modified_files_count") ? data.get("modified_files_count").getAsInt() : 0;

                        mainHandler.post(() -> {
                            if (tvGitBranchBadge != null) tvGitBranchBadge.setText("🌿 " + branch);
                            if (tvGitCommitHash != null && !lastCommit.isEmpty()) {
                                tvGitCommitHash.setText("Commit: " + (lastCommit.length() > 7 ? lastCommit.substring(0, 7) : lastCommit));
                            }
                            if (tvGitFilesSummary != null) {
                                tvGitFilesSummary.setText(modifiedCount > 0
                                        ? "⚠️ " + modifiedCount + " berkas dimodifikasi di cabang ini"
                                        : "✅ Working tree bersih, tidak ada perubahan kode.");
                                tvGitFilesSummary.setTextColor(modifiedCount > 0 ? 0xFFFBBF24 : 0xFF34D399);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (tvGitFilesSummary != null) tvGitFilesSummary.setText("Gagal memuat status: " + e.getMessage());
                });
            }
        }).start();

        new Thread(() -> {
            try {
                String serverUrl = apiClient.getServerUrl();
                OkHttpClient client = apiClient.getOkHttpClient();
                Request request = new Request.Builder()
                        .url(serverUrl + "/api/system/code-studio/diff")
                        .addHeader("Authorization", "Bearer " + apiClient.getToken())
                        .get()
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JsonObject root = gson.fromJson(body, JsonObject.class);
                    if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                        JsonObject data = root.getAsJsonObject("data");
                        String diff = data.has("diff") ? data.get("diff").getAsString() : "";
                        String stat = data.has("stat") ? data.get("stat").getAsString() : "";
                        final String display = (!stat.isEmpty() ? stat + "\n\n" : "") + (!diff.isEmpty() ? diff : "(Tidak ada perbedaan kode aktif)");
                        mainHandler.post(() -> {
                            if (tvGitDiffContent != null) tvGitDiffContent.setText(display);
                        });
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void handleGitCommit() {
        String msg = etGitCommitMsg != null && etGitCommitMsg.getText() != null ? etGitCommitMsg.getText().toString().trim() : "";
        if (msg.isEmpty()) {
            Toast.makeText(this, "Tulis pesan commit terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                String serverUrl = apiClient.getServerUrl();
                OkHttpClient client = apiClient.getOkHttpClient();
                JsonObject payload = new JsonObject();
                payload.addProperty("message", msg);

                Request request = new Request.Builder()
                        .url(serverUrl + "/api/system/code-studio/commit")
                        .addHeader("Authorization", "Bearer " + apiClient.getToken())
                        .post(RequestBody.create(payload.toString(), MediaType.parse("application/json; charset=utf-8")))
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    mainHandler.post(() -> {
                        Toast.makeText(this, "Commit berhasil disimpan!", Toast.LENGTH_SHORT).show();
                        if (etGitCommitMsg != null) etGitCommitMsg.setText("");
                        loadGitStatus();
                    });
                } else {
                    mainHandler.post(() -> Toast.makeText(this, "Gagal melakukan commit", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void loadFiles(String path, String query) {
        pbLoading.setVisibility(View.VISIBLE);
        selectedPaths.clear();
        updateBatchActionBar();

        apiClient.fetchWorkspaceFiles(path, query, new MollyApiClient.ApiCallback<List<WorkspaceFileItem>>() {
            @Override
            public void onSuccess(List<WorkspaceFileItem> result) {
                if (swipeRefreshPersonalSpace != null) swipeRefreshPersonalSpace.setRefreshing(false);
                pbLoading.setVisibility(View.GONE);
                currentFileList.clear();
                if (result != null) {
                    currentFileList.addAll(result);
                }
                updateBreadcrumbs();
                adapter.notifyDataSetChanged();

                if (currentFileList.isEmpty()) {
                    llEmptyState.setVisibility(View.VISIBLE);
                    rvFileList.setVisibility(View.GONE);
                } else {
                    llEmptyState.setVisibility(View.GONE);
                    rvFileList.setVisibility(View.VISIBLE);
                }
                tvItemCount.setText(currentFileList.size() + "item");
            }

            @Override
            public void onError(String error) {
                if (swipeRefreshPersonalSpace != null) swipeRefreshPersonalSpace.setRefreshing(false);
                pbLoading.setVisibility(View.GONE);
                Toast.makeText(PersonalSpaceActivity.this, "Gagal memuat berkas: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateBreadcrumbs() {
        if ("/".equals(currentDirectory) || currentDirectory.isEmpty()) {
            tvBreadcrumbSub.setVisibility(View.GONE);
        } else {
            tvBreadcrumbSub.setVisibility(View.VISIBLE);
            tvBreadcrumbSub.setText(currentDirectory.replace("/", " / "));
        }
    }

    private void updateBatchActionBar() {
        int count = selectedPaths.size();
        if (count > 0) {
            llBatchActionBar.setVisibility(View.VISIBLE);
            tvSelectedCount.setText(count + " berkas dipilih");
        } else {
            llBatchActionBar.setVisibility(View.GONE);
            cbSelectAll.setChecked(false);
        }
    }

    private void showCreateFolderDialog() {
        EditText etName = new EditText(this);
        etName.setHint("Nama folder baru (misal: my_project)");
        etName.setTextColor(0xFFFFFFFF);
        etName.setHintTextColor(0xFF94A3B8);
        etName.setBackgroundResource(R.drawable.bg_input_box);
        etName.setPadding(28, 20, 28, 20);

        new MollyDialog.Builder(this)
                .setTitle("📁 Buat Folder Baru")
                .setMessage("Folder akan dibuat di direktori: " + currentDirectory)
                .setView(etName)
                .setPositiveButton("Buat", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (!name.isEmpty()) {
                        apiClient.createWorkspaceFolder(currentDirectory, name, new MollyApiClient.ApiCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean res) {
                                Toast.makeText(PersonalSpaceActivity.this, "Folder berhasil dibuat", Toast.LENGTH_SHORT).show();
                                loadFiles(currentDirectory, "");
                            }

                            @Override
                            public void onError(String err) {
                                Toast.makeText(PersonalSpaceActivity.this, "Gagal membuat folder: " + err, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showBatchDeleteConfirmationDialog() {
        new MollyDialog.Builder(this)
                .setTitle("🗑️ Hapus " + selectedPaths.size() + " Berkas?")
                .setMessage("Apakah Anda yakin ingin menghapus berkas/folder yang dipilih dari Ruang Kerja secara permanen?")
                .setDestructiveButton("Hapus Sekarang", (dialog, which) -> {
                    apiClient.deleteWorkspaceFiles(new ArrayList<>(selectedPaths), new MollyApiClient.ApiCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean res) {
                            Toast.makeText(PersonalSpaceActivity.this, "Berkas terpilih berhasil dihapus", Toast.LENGTH_SHORT).show();
                            loadFiles(currentDirectory, "");
                        }

                        @Override
                        public void onError(String err) {
                            Toast.makeText(PersonalSpaceActivity.this, "Gagal menghapus: " + err, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showFileOptionsBottomSheet(WorkspaceFileItem item) {
        String[] options = {"🌐 Buka Pratinjau Live", "Salin Path (" + item.path + ")", "Hapus Berkas"};
        new MollyDialog.Builder(this)
                .setTitle(item.getIcon() + " " + item.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        ArtifactItem art = new ArtifactItem(item.path, apiClient.getSessionId());
                        ArtifactPreviewDialogHelper.show(PersonalSpaceActivity.this, apiClient, art, new ArtifactPreviewDialogHelper.OnArtifactActionListener() {
                            @Override
                            public void onDownload(ArtifactItem it) {
                                Toast.makeText(PersonalSpaceActivity.this, "Mengunduh " + it.getName() + "...", Toast.LENGTH_SHORT).show();
                                apiClient.downloadArtifactFile(it.getPath(), it.getName(), new MollyApiClient.ApiCallback<java.io.File>() {
                                    @Override
                                    public void onSuccess(java.io.File file) {
                                        Toast.makeText(PersonalSpaceActivity.this, "Berhasil diunduh ke folder Download: " + file.getName(), Toast.LENGTH_LONG).show();
                                    }

                                    @Override
                                    public void onError(String error) {
                                        Toast.makeText(PersonalSpaceActivity.this, "Gagal mengunduh: " + error, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            @Override
                            public void onSave(ArtifactItem it) {
                                Toast.makeText(PersonalSpaceActivity.this, "Berkas sudah berada di ruang kerja pribadi", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else if (which == 1) {
                        android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cb != null) {
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("File Path", item.path));
                            Toast.makeText(this, "Path disalin ke papan klip", Toast.LENGTH_SHORT).show();
                        }
                    } else if (which == 2) {
                        List<String> list = new ArrayList<>();
                        list.add(item.path);
                        apiClient.deleteWorkspaceFiles(list, new MollyApiClient.ApiCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean res) {
                                Toast.makeText(PersonalSpaceActivity.this, "Berkas berhasil dihapus", Toast.LENGTH_SHORT).show();
                                loadFiles(currentDirectory, "");
                            }

                            @Override
                            public void onError(String err) {
                                Toast.makeText(PersonalSpaceActivity.this, "Gagal menghapus: " + err, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .show();
    }

    private void showFileDetailDialog(WorkspaceFileItem item) {
        new MollyDialog.Builder(this)
                .setTitle(item.getIcon() + " " + item.name)
                .setMessage("Jalur: " + item.path + "\nUkuran: " + item.getFormattedSize() + "\nWaktu Modifikasi: " + item.lastModified)
                .setPositiveButton("Salin Path", (dialog, which) -> {
                    android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cb != null) {
                        cb.setPrimaryClip(android.content.ClipData.newPlainText("File Path", item.path));
                        Toast.makeText(this, "Path disalin ke papan klip", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    // ==========================================
    // Adapter untuk RecyclerView Personal Space
    // ==========================================
    private class PersonalSpaceFileAdapter extends RecyclerView.Adapter<PersonalSpaceFileAdapter.ViewHolder> {

        interface OnItemClickListener {
            void onItemClick(WorkspaceFileItem item);
            void onSelectChanged(WorkspaceFileItem item, boolean isSelected);
            void onOptionsClick(WorkspaceFileItem item);
        }

        private OnItemClickListener listener;

        void setOnItemClickListener(OnItemClickListener l) {
            this.listener = l;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_personal_space_row, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WorkspaceFileItem item = currentFileList.get(position);
            holder.tvIcon.setText(item.getIcon());
            holder.tvName.setText(item.name);
            holder.tvSize.setText(item.isDirectory ? "-" : item.getFormattedSize());
            holder.tvTime.setText(item.lastModified);

            boolean isChecked = selectedPaths.contains(item.path);
            holder.cbSelect.setOnCheckedChangeListener(null);
            holder.cbSelect.setChecked(isChecked);

            holder.cbSelect.setOnCheckedChangeListener((bv, checked) -> {
                if (listener != null) listener.onSelectChanged(item, checked);
            });

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });

            holder.btnOptions.setOnClickListener(v -> {
                if (listener != null) listener.onOptionsClick(item);
            });
        }

        @Override
        public int getItemCount() {
            return currentFileList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            CheckBox cbSelect;
            TextView tvIcon;
            TextView tvName;
            TextView tvSize;
            TextView tvTime;
            ImageView btnOptions;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                cbSelect = itemView.findViewById(R.id.cbSelectFile);
                tvIcon = itemView.findViewById(R.id.tvRowIcon);
                tvName = itemView.findViewById(R.id.tvRowName);
                tvSize = itemView.findViewById(R.id.tvRowSize);
                tvTime = itemView.findViewById(R.id.tvRowTime);
                btnOptions = itemView.findViewById(R.id.btnRowOptions);
            }
        }
    }
}
