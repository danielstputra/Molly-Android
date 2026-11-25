package com.molly.agent;

import android.app.AlertDialog;
import android.os.Bundle;
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

import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.WorkspaceFileItem;

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

    private TextView tvBreadcrumbRoot;
    private TextView tvBreadcrumbSub;
    private TextView tvItemCount;

    private LinearLayout llBatchActionBar;
    private TextView tvSelectedCount;
    private TextView btnBatchDelete;

    private CheckBox cbSelectAll;
    private ProgressBar pbLoading;
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

        tvBreadcrumbRoot = findViewById(R.id.tvBreadcrumbRoot);
        tvBreadcrumbSub = findViewById(R.id.tvBreadcrumbSub);
        tvItemCount = findViewById(R.id.tvItemCount);

        llBatchActionBar = findViewById(R.id.llBatchActionBar);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        btnBatchDelete = findViewById(R.id.btnBatchDelete);

        cbSelectAll = findViewById(R.id.cbSelectAll);
        pbLoading = findViewById(R.id.pbLoading);
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
                    showFileDetailDialog(item);
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

        activeTab.setBackgroundTintList(getColorStateList(R.color.primary_gold_dark));
        activeTab.setTextColor(getColor(R.color.primary_gold));
    }

    private void loadFiles(String path, String query) {
        pbLoading.setVisibility(View.VISIBLE);
        selectedPaths.clear();
        updateBatchActionBar();

        apiClient.fetchWorkspaceFiles(path, query, new MollyApiClient.ApiCallback<List<WorkspaceFileItem>>() {
            @Override
            public void onSuccess(List<WorkspaceFileItem> result) {
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
        etName.setHintTextColor(0xFF9CA3AF);

        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
                .setTitle("🗑️ Hapus " + selectedPaths.size() + " Berkas?")
                .setMessage("Apakah Anda yakin ingin menghapus berkas/folder yang dipilih dari Ruang Kerja secara permanen?")
                .setPositiveButton("Hapus Sekarang", (dialog, which) -> {
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
        String[] options = {"Salin Path (" + item.path + ")", "Hapus Berkas"};
        new AlertDialog.Builder(this)
                .setTitle(item.getIcon() + " " + item.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cb != null) {
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("File Path", item.path));
                            Toast.makeText(this, "Path disalin ke papan klip", Toast.LENGTH_SHORT).show();
                        }
                    } else if (which == 1) {
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
        new AlertDialog.Builder(this)
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
