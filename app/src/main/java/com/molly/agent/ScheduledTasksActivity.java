package com.molly.agent;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.JsonObject;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.ScheduledTaskItem;
import com.molly.agent.ui.ScheduledTaskEditDialogHelper;
import com.molly.agent.ui.ScheduledTaskHistoryDialogHelper;
import com.molly.agent.ui.ScheduledTasksAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScheduledTasksActivity extends AppCompatActivity implements ScheduledTasksAdapter.TaskActionListener {

    public static final String EXTRA_OPEN_SESSION_ID = "extra_open_session_id";

    private MollyApiClient apiClient;
    private ScheduledTasksAdapter adapter;

    private ImageView btnBack;
    private ImageView btnRefreshTasks;
    private EditText etSearchTasks;
    private ImageView btnClearSearch;

    private TextView tabFilterAll;
    private TextView tabFilterActive;
    private TextView tabFilterPaused;

    private RecyclerView rvTasks;
    private ProgressBar pbLoading;
    private View llEmptyTasks;
    private FloatingActionButton fabAddTask;

    private final List<ScheduledTaskItem> allTasks = new ArrayList<>();
    private int currentFilterMode = 0; // 0 = Semua, 1 = Aktif, 2 = Dijeda
    private String currentSearchQuery = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scheduled_tasks);

        apiClient = new MollyApiClient(this);

        initViews();
        setupListeners();

        loadTasks(true);
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnRefreshTasks = findViewById(R.id.btnRefreshTasks);
        etSearchTasks = findViewById(R.id.etSearchTasks);
        btnClearSearch = findViewById(R.id.btnClearSearch);

        tabFilterAll = findViewById(R.id.tabFilterAll);
        tabFilterActive = findViewById(R.id.tabFilterActive);
        tabFilterPaused = findViewById(R.id.tabFilterPaused);

        rvTasks = findViewById(R.id.rvScheduledTasks);
        pbLoading = findViewById(R.id.pbTasksLoading);
        llEmptyTasks = findViewById(R.id.llEmptyTasks);
        fabAddTask = findViewById(R.id.fabAddTask);

        adapter = new ScheduledTasksAdapter(this, this);
        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        rvTasks.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnRefreshTasks.setOnClickListener(v -> loadTasks(false));

        btnClearSearch.setOnClickListener(v -> etSearchTasks.setText(""));

        etSearchTasks.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = (s != null) ? s.toString().trim().toLowerCase(Locale.ROOT) : "";
                btnClearSearch.setVisibility(currentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        tabFilterAll.setOnClickListener(v -> {
            currentFilterMode = 0;
            updateFilterTabsUi();
            applyFilter();
        });

        tabFilterActive.setOnClickListener(v -> {
            currentFilterMode = 1;
            updateFilterTabsUi();
            applyFilter();
        });

        tabFilterPaused.setOnClickListener(v -> {
            currentFilterMode = 2;
            updateFilterTabsUi();
            applyFilter();
        });

        fabAddTask.setOnClickListener(v -> {
            ScheduledTaskEditDialogHelper.show(this, apiClient, null, () -> loadTasks(false));
        });
    }

    private void updateFilterTabsUi() {
        tabFilterAll.setBackgroundTintList(getColorStateList(currentFilterMode == 0 ? R.color.user_bubble_bg : R.color.card_dark));
        tabFilterAll.setTextColor(getColor(currentFilterMode == 0 ? R.color.primary_gold : R.color.text_muted));

        tabFilterActive.setBackgroundTintList(getColorStateList(currentFilterMode == 1 ? R.color.user_bubble_bg : R.color.card_dark));
        tabFilterActive.setTextColor(getColor(currentFilterMode == 1 ? R.color.primary_gold : R.color.text_muted));

        tabFilterPaused.setBackgroundTintList(getColorStateList(currentFilterMode == 2 ? R.color.user_bubble_bg : R.color.card_dark));
        tabFilterPaused.setTextColor(getColor(currentFilterMode == 2 ? R.color.primary_gold : R.color.text_muted));
    }

    private void loadTasks(boolean showFullLoading) {
        if (showFullLoading) {
            pbLoading.setVisibility(View.VISIBLE);
            llEmptyTasks.setVisibility(View.GONE);
        }

        apiClient.fetchScheduledTasks(new MollyApiClient.ApiCallback<List<ScheduledTaskItem>>() {
            @Override
            public void onSuccess(List<ScheduledTaskItem> result) {
                pbLoading.setVisibility(View.GONE);

                allTasks.clear();
                if (result != null) {
                    allTasks.addAll(result);
                }
                applyFilter();
            }

            @Override
            public void onError(String error) {
                pbLoading.setVisibility(View.GONE);
                Toast.makeText(ScheduledTasksActivity.this, "Gagal memuat tugas: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applyFilter() {
        List<ScheduledTaskItem> filtered = new ArrayList<>();
        int countAll = allTasks.size();
        int countActive = 0;
        int countPaused = 0;

        for (ScheduledTaskItem task : allTasks) {
            if (task.enabled) {
                countActive++;
            } else {
                countPaused++;
            }

            // Cek filter mode
            if (currentFilterMode == 1 && !task.enabled) continue;
            if (currentFilterMode == 2 && task.enabled) continue;

            // Cek query pencarian
            if (!currentSearchQuery.isEmpty()) {
                String name = task.name != null ? task.name.toLowerCase(Locale.ROOT) : "";
                String prompt = task.prompt != null ? task.prompt.toLowerCase(Locale.ROOT) : "";
                String agent = task.agentName != null ? task.agentName.toLowerCase(Locale.ROOT) : "";
                if (!name.contains(currentSearchQuery) && !prompt.contains(currentSearchQuery) && !agent.contains(currentSearchQuery)) {
                    continue;
                }
            }

            filtered.add(task);
        }

        tabFilterAll.setText("Semua (" + countAll + ")");
        tabFilterActive.setText("Aktif (" + countActive + ")");
        tabFilterPaused.setText("Dijeda (" + countPaused + ")");

        adapter.setItems(filtered);

        if (filtered.isEmpty()) {
            llEmptyTasks.setVisibility(View.VISIBLE);
            rvTasks.setVisibility(View.GONE);
        } else {
            llEmptyTasks.setVisibility(View.GONE);
            rvTasks.setVisibility(View.VISIBLE);
        }
    }

    // ==========================================
    // TaskActionListener Implementation
    // ==========================================

    @Override
    public void onToggleEnabled(ScheduledTaskItem task, boolean enabled) {
        JsonObject patch = new JsonObject();
        patch.addProperty("enabled", enabled);

        apiClient.updateScheduledTask(task.id, patch, new MollyApiClient.ApiCallback<ScheduledTaskItem>() {
            @Override
            public void onSuccess(ScheduledTaskItem result) {
                task.enabled = enabled;
                applyFilter();
                Toast.makeText(ScheduledTasksActivity.this, enabled ? "Tugas diaktifkan" : "Tugas dijeda", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ScheduledTasksActivity.this, "Gagal mengubah status: " + error, Toast.LENGTH_SHORT).show();
                applyFilter(); // Reset view switch
            }
        });
    }

    @Override
    public void onRunNow(ScheduledTaskItem task) {
        Toast.makeText(this, "⚡ Memulai eksekusi tugas '" + task.name + "'...", Toast.LENGTH_SHORT).show();

        apiClient.runScheduledTaskNow(task.id, new MollyApiClient.ApiCallback<com.molly.agent.model.ScheduledRunItem>() {
            @Override
            public void onSuccess(com.molly.agent.model.ScheduledRunItem result) {
                Toast.makeText(ScheduledTasksActivity.this, "Eksekusi berhasil dimulai! Menampilkan riwayat...", Toast.LENGTH_SHORT).show();
                onViewHistory(task);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ScheduledTasksActivity.this, "Gagal menjalankan tugas: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onViewHistory(ScheduledTaskItem task) {
        ScheduledTaskHistoryDialogHelper.show(this, apiClient, task, threadId -> {
            // Buka sesi obrolan hasil eksekusi tugas
            Intent data = new Intent();
            data.putExtra(EXTRA_OPEN_SESSION_ID, threadId);
            setResult(RESULT_OK, data);
            finish();
        });
    }

    @Override
    public void onEdit(ScheduledTaskItem task) {
        ScheduledTaskEditDialogHelper.show(this, apiClient, task, () -> loadTasks(false));
    }

    @Override
    public void onDelete(ScheduledTaskItem task) {
        new AlertDialog.Builder(this)
                .setTitle("Hapus Tugas Terjadwal?")
                .setMessage("Apakah Anda yakin ingin menghapus tugas \"" + task.name + "\"? Tindakan ini tidak dapat dibatalkan.")
                .setPositiveButton("Hapus", (dialog, which) -> {
                    apiClient.deleteScheduledTask(task.id, new MollyApiClient.ApiCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean result) {
                            Toast.makeText(ScheduledTasksActivity.this, "Tugas berhasil dihapus", Toast.LENGTH_SHORT).show();
                            loadTasks(false);
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(ScheduledTasksActivity.this, "Gagal menghapus: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}
