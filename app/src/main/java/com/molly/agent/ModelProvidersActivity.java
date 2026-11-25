package com.molly.agent;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.JsonObject;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.ModelProviderItem;
import com.molly.agent.ui.ModelProvidersAdapter;
import com.molly.agent.ui.ProviderEditDialogHelper;
import com.molly.agent.ui.ProviderModelsDialogHelper;

import java.util.ArrayList;
import java.util.List;

public class ModelProvidersActivity extends AppCompatActivity implements ModelProvidersAdapter.ProviderActionListener {

    private MollyApiClient apiClient;
    private final List<ModelProviderItem> allProviders = new ArrayList<>();
    private final List<ModelProviderItem> displayedProviders = new ArrayList<>();
    private ModelProvidersAdapter adapter;

    private TextView tvTotalProviders;
    private TextView tvEnabledProviders;
    private TextView tvWarningProviders;
    private TextView tvTotalModels;

    private EditText etSearch;
    private ImageView btnClearSearch;

    private TextView tabFilterAll;
    private TextView tabFilterActive;
    private TextView tabFilterDisabled;

    private RecyclerView rvProviders;
    private ProgressBar pbLoading;
    private View llEmpty;

    private String currentFilterMode = "all"; // "all", "active", "disabled"
    private String currentSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_providers);

        apiClient = new MollyApiClient(this);

        initViews();
        setupListeners();
        loadProviders();
    }

    private void initViews() {
        ImageView btnBack = findViewById(R.id.btnBack);
        ImageView btnRefreshCache = findViewById(R.id.btnRefreshCache);
        ImageView btnRefreshProviders = findViewById(R.id.btnRefreshProviders);

        btnBack.setOnClickListener(v -> finish());
        btnRefreshCache.setOnClickListener(v -> refreshCache());
        btnRefreshProviders.setOnClickListener(v -> loadProviders());

        tvTotalProviders = findViewById(R.id.tvTotalProviders);
        tvEnabledProviders = findViewById(R.id.tvEnabledProviders);
        tvWarningProviders = findViewById(R.id.tvWarningProviders);
        tvTotalModels = findViewById(R.id.tvTotalModels);

        etSearch = findViewById(R.id.etSearchProviders);
        btnClearSearch = findViewById(R.id.btnClearSearch);

        tabFilterAll = findViewById(R.id.tabFilterAll);
        tabFilterActive = findViewById(R.id.tabFilterActive);
        tabFilterDisabled = findViewById(R.id.tabFilterDisabled);

        rvProviders = findViewById(R.id.rvModelProviders);
        pbLoading = findViewById(R.id.pbProvidersLoading);
        llEmpty = findViewById(R.id.llEmptyProviders);

        FloatingActionButton fabAdd = findViewById(R.id.fabAddProvider);
        fabAdd.setOnClickListener(v -> {
            ProviderEditDialogHelper.show(this, apiClient, null, savedItem -> loadProviders());
        });

        adapter = new ModelProvidersAdapter(this, this);
        rvProviders.setLayoutManager(new LinearLayoutManager(this));
        rvProviders.setAdapter(adapter);
    }

    private void setupListeners() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = (s != null) ? s.toString().trim() : "";
                btnClearSearch.setVisibility(currentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            currentSearchQuery = "";
            applyFilter();
        });

        tabFilterAll.setOnClickListener(v -> setFilterMode("all"));
        tabFilterActive.setOnClickListener(v -> setFilterMode("active"));
        tabFilterDisabled.setOnClickListener(v -> setFilterMode("disabled"));
    }

    private void setFilterMode(String mode) {
        currentFilterMode = mode;
        updateTabStyles();
        applyFilter();
    }

    private void updateTabStyles() {
        int activeBg = 0x33F3BA32; // Gold translucent
        int normalBg = 0xFF16191E; // Dark surface
        int goldColor = getColor(R.color.primary_gold);
        int grayColor = 0xFF9CA3AF;

        tabFilterAll.setBackgroundTintList(ColorStateList.valueOf("all".equals(currentFilterMode) ? activeBg : normalBg));
        tabFilterAll.setTextColor("all".equals(currentFilterMode) ? goldColor : grayColor);

        tabFilterActive.setBackgroundTintList(ColorStateList.valueOf("active".equals(currentFilterMode) ? activeBg : normalBg));
        tabFilterActive.setTextColor("active".equals(currentFilterMode) ? goldColor : grayColor);

        tabFilterDisabled.setBackgroundTintList(ColorStateList.valueOf("disabled".equals(currentFilterMode) ? activeBg : normalBg));
        tabFilterDisabled.setTextColor("disabled".equals(currentFilterMode) ? goldColor : grayColor);
    }

    private void loadProviders() {
        pbLoading.setVisibility(View.VISIBLE);
        llEmpty.setVisibility(View.GONE);

        apiClient.fetchModelProviders(new MollyApiClient.ApiCallback<List<ModelProviderItem>>() {
            @Override
            public void onSuccess(List<ModelProviderItem> result) {
                pbLoading.setVisibility(View.GONE);
                allProviders.clear();
                if (result != null) {
                    allProviders.addAll(result);
                }
                updateStatsOverview();
                applyFilter();
            }

            @Override
            public void onError(String error) {
                pbLoading.setVisibility(View.GONE);
                Toast.makeText(ModelProvidersActivity.this, "Gagal memuat penyedia: " + error, Toast.LENGTH_LONG).show();
                updateStatsOverview();
                applyFilter();
            }
        });
    }

    private void updateStatsOverview() {
        int total = allProviders.size();
        int enabled = 0;
        int warning = 0;
        int totalActiveModels = 0;

        for (ModelProviderItem item : allProviders) {
            if (item.isEnabled) enabled++;
            if (item.hasCredentialWarning()) warning++;
            totalActiveModels += item.getActiveModelsCount();
        }

        tvTotalProviders.setText(String.valueOf(total));
        tvEnabledProviders.setText(String.valueOf(enabled));
        tvWarningProviders.setText(String.valueOf(warning));
        tvTotalModels.setText(String.valueOf(totalActiveModels));

        tabFilterAll.setText("Semua (" + total + ")");
        tabFilterActive.setText("Aktif (" + enabled + ")");
        tabFilterDisabled.setText("Nonaktif (" + (total - enabled) + ")");
    }

    private void applyFilter() {
        displayedProviders.clear();
        String query = currentSearchQuery.toLowerCase();

        for (ModelProviderItem item : allProviders) {
            boolean matchesSearch = query.isEmpty()
                    || item.displayName.toLowerCase().contains(query)
                    || item.providerId.toLowerCase().contains(query);

            if (!matchesSearch) continue;

            if ("active".equals(currentFilterMode) && !item.isEnabled) continue;
            if ("disabled".equals(currentFilterMode) && item.isEnabled) continue;

            displayedProviders.add(item);
        }

        adapter.setItems(displayedProviders);
        llEmpty.setVisibility(displayedProviders.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void refreshCache() {
        Toast.makeText(this, "Memperbarui cache model di server...", Toast.LENGTH_SHORT).show();
        apiClient.refreshModelCache(new MollyApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                Toast.makeText(ModelProvidersActivity.this, "Cache model AI berhasil diperbarui secara instan!", Toast.LENGTH_SHORT).show();
                loadProviders();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ModelProvidersActivity.this, "Gagal memperbarui cache: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==========================================
    // ProviderActionListener Callbacks
    // ==========================================

    @Override
    public void onToggleEnabled(ModelProviderItem item, boolean enabled) {
        JsonObject payload = item.toJson();
        payload.addProperty("is_enabled", enabled);

        apiClient.updateModelProvider(item.providerId, payload, new MollyApiClient.ApiCallback<ModelProviderItem>() {
            @Override
            public void onSuccess(ModelProviderItem result) {
                item.isEnabled = enabled;
                updateStatsOverview();
                applyFilter();
                String statusStr = enabled ? "diaktifkan" : "dinonaktifkan";
                Toast.makeText(ModelProvidersActivity.this, item.displayName + " berhasil " + statusStr, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ModelProvidersActivity.this, "Gagal mengubah status: " + error, Toast.LENGTH_LONG).show();
                applyFilter(); // Kembalikan ke state semula
            }
        });
    }

    @Override
    public void onManageModels(ModelProviderItem item) {
        ProviderModelsDialogHelper.show(this, apiClient, item, () -> {
            loadProviders();
        });
    }

    @Override
    public void onConfigProvider(ModelProviderItem item) {
        ProviderEditDialogHelper.show(this, apiClient, item, savedItem -> {
            loadProviders();
        });
    }

    @Override
    public void onDeleteProvider(ModelProviderItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Hapus Penyedia Model?")
                .setMessage("Apakah Anda yakin ingin menghapus konfigurasi penyedia \"" + item.displayName + "\" (" + item.providerId + ")? Semua model dari penyedia ini tidak akan tersedia.")
                .setPositiveButton("Ya, Hapus", (dialog, which) -> {
                    apiClient.deleteModelProvider(item.providerId, new MollyApiClient.ApiCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean result) {
                            Toast.makeText(ModelProvidersActivity.this, "Penyedia berhasil dihapus", Toast.LENGTH_SHORT).show();
                            loadProviders();
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(ModelProvidersActivity.this, "Gagal menghapus: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}
