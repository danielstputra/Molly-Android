package com.molly.agent.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.molly.agent.R;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.ModelProviderItem;
import com.molly.agent.model.ProviderModelItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProviderModelsDialogHelper {

    public interface OnModelsUpdatedListener {
        void onUpdated();
    }

    public static void show(Context context, MollyApiClient apiClient, ModelProviderItem provider, OnModelsUpdatedListener updatedListener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_provider_models, null);

        TextView tvAvatar = view.findViewById(R.id.tvModelsDialogAvatar);
        TextView tvTitle = view.findViewById(R.id.tvModelsDialogTitle);
        ImageView btnClose = view.findViewById(R.id.btnModelsDialogClose);

        View btnFetchRemote = view.findViewById(R.id.btnFetchRemoteModels);
        TextView tvFetchRemoteLabel = view.findViewById(R.id.tvFetchRemoteLabel);
        View btnAddManual = view.findViewById(R.id.btnAddManualModel);

        EditText etSearch = view.findViewById(R.id.etSearchModelDialog);
        TextView tabAll = view.findViewById(R.id.tabModelTypeAll);
        TextView tabChat = view.findViewById(R.id.tabModelTypeChat);
        TextView tabEmbedding = view.findViewById(R.id.tabModelTypeEmbedding);
        TextView tabRerank = view.findViewById(R.id.tabModelTypeRerank);

        ProgressBar pbLoading = view.findViewById(R.id.pbModelDialogLoading);
        RecyclerView rvModels = view.findViewById(R.id.rvDialogModels);
        TextView tvEmpty = view.findViewById(R.id.tvEmptyDialogModels);
        TextView btnSave = view.findViewById(R.id.btnSaveDialogModels);

        tvAvatar.setText(provider.getAvatarEmoji());
        tvTitle.setText("Kelola Model: " + (provider.displayName != null ? provider.displayName : provider.providerId));

        // Copy models list for local editing
        final List<ProviderModelItem> localModels = new ArrayList<>();
        if (provider.enabledModels != null) {
            for (ProviderModelItem m : provider.enabledModels) {
                ProviderModelItem copy = new ProviderModelItem();
                copy.id = m.id;
                copy.displayName = m.displayName;
                copy.type = m.type;
                copy.source = m.source;
                copy.enabled = m.enabled;
                copy.contextLength = m.contextLength;
                copy.dimension = m.dimension;
                copy.batchSize = m.batchSize;
                localModels.add(copy);
            }
        }

        final String[] currentFilterType = new String[]{"all"};
        final String[] currentSearchQuery = new String[]{""};

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Inner Adapter
        class ModelsAdapter extends RecyclerView.Adapter<ModelsAdapter.ViewHolder> {
            private final List<ProviderModelItem> displayList = new ArrayList<>();

            void updateList(List<ProviderModelItem> items) {
                displayList.clear();
                displayList.addAll(items);
                notifyDataSetChanged();
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_provider_model_row, parent, false);
                return new ViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                ProviderModelItem model = displayList.get(position);

                holder.swEnabled.setOnCheckedChangeListener(null);
                holder.swEnabled.setChecked(model.enabled);
                holder.swEnabled.setOnCheckedChangeListener((btn, isChecked) -> {
                    model.enabled = isChecked;
                });

                holder.tvName.setText(model.displayName != null && !model.displayName.isEmpty() ? model.displayName : model.id);
                holder.tvId.setText(model.id);

                holder.tvType.setText(model.getTypeLabel());
                holder.tvType.setTextColor(model.getTypeBadgeColor());

                String meta = model.getMetaDescription();
                if (meta.isEmpty()) {
                    holder.tvMeta.setText(model.source != null ? ("Sumber: " + model.source) : "Model standar");
                } else {
                    holder.tvMeta.setText(meta);
                }

                // Test connection badge
                if (model.testStatus != null) {
                    holder.tvTestBadge.setVisibility(View.VISIBLE);
                    if ("available".equalsIgnoreCase(model.testStatus)) {
                        holder.tvTestBadge.setText("✓ Tersedia");
                        holder.tvTestBadge.setTextColor(context.getColor(R.color.status_green));
                    } else if ("testing".equalsIgnoreCase(model.testStatus)) {
                        holder.tvTestBadge.setText("... Menguji");
                        holder.tvTestBadge.setTextColor(context.getColor(R.color.primary_gold));
                    } else if ("unsupported".equalsIgnoreCase(model.testStatus)) {
                        holder.tvTestBadge.setText("⚠️ Tidak Didukung");
                        holder.tvTestBadge.setTextColor(0xFFF59E0B);
                    } else {
                        holder.tvTestBadge.setText("✕ Gagal");
                        holder.tvTestBadge.setTextColor(context.getColor(R.color.status_red));
                    }
                } else {
                    holder.tvTestBadge.setVisibility(View.GONE);
                }

                // Klik tombol Uji Koneksi
                holder.btnTest.setOnClickListener(v -> {
                    model.testStatus = "testing";
                    notifyItemChanged(holder.getAdapterPosition());

                    String spec = provider.providerId + ":" + model.id;
                    apiClient.testModelStatus(spec, new MollyApiClient.ApiCallback<JsonObject>() {
                        @Override
                        public void onSuccess(JsonObject result) {
                            String status = result.has("status") ? result.get("status").getAsString() : "available";
                            String msg = result.has("message") ? result.get("message").getAsString() : "";
                            model.testStatus = status;
                            model.testMessage = msg;
                            notifyItemChanged(holder.getAdapterPosition());
                            Toast.makeText(context, "Status [" + model.id + "]: " + status + (msg.isEmpty() ? "" : " - " + msg), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(String error) {
                            model.testStatus = "error";
                            model.testMessage = error;
                            notifyItemChanged(holder.getAdapterPosition());
                            Toast.makeText(context, "Uji gagal: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            }

            @Override
            public int getItemCount() {
                return displayList.size();
            }

            class ViewHolder extends RecyclerView.ViewHolder {
                SwitchCompat swEnabled;
                TextView tvName;
                TextView tvId;
                TextView tvType;
                TextView tvMeta;
                TextView tvTestBadge;
                TextView btnTest;

                ViewHolder(@NonNull View itemView) {
                    super(itemView);
                    swEnabled = itemView.findViewById(R.id.swModelRowEnabled);
                    tvName = itemView.findViewById(R.id.tvModelRowName);
                    tvId = itemView.findViewById(R.id.tvModelRowId);
                    tvType = itemView.findViewById(R.id.tvModelRowType);
                    tvMeta = itemView.findViewById(R.id.tvModelRowMeta);
                    tvTestBadge = itemView.findViewById(R.id.tvModelRowTestBadge);
                    btnTest = itemView.findViewById(R.id.btnTestModelConnection);
                }
            }
        }

        ModelsAdapter adapter = new ModelsAdapter();
        rvModels.setLayoutManager(new LinearLayoutManager(context));
        rvModels.setAdapter(adapter);

        // Helper untuk memfilter tampilan daftar model
        Runnable applyFilter = () -> {
            List<ProviderModelItem> filtered = new ArrayList<>();
            for (ProviderModelItem m : localModels) {
                // Type filter
                if (!currentFilterType[0].equals("all") && !currentFilterType[0].equalsIgnoreCase(m.type)) {
                    continue;
                }
                // Search query filter
                if (!currentSearchQuery[0].isEmpty()) {
                    String id = m.id != null ? m.id.toLowerCase(Locale.ROOT) : "";
                    String name = m.displayName != null ? m.displayName.toLowerCase(Locale.ROOT) : "";
                    if (!id.contains(currentSearchQuery[0]) && !name.contains(currentSearchQuery[0])) {
                        continue;
                    }
                }
                filtered.add(m);
            }
            adapter.updateList(filtered);
            tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
            rvModels.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        };

        // Filter tab selection helper
        Runnable updateTabsUi = () -> {
            tabAll.setBackgroundTintList(context.getColorStateList(currentFilterType[0].equals("all") ? R.color.user_bubble_bg : R.color.card_dark));
            tabAll.setTextColor(context.getColor(currentFilterType[0].equals("all") ? R.color.primary_gold : R.color.text_muted));

            tabChat.setBackgroundTintList(context.getColorStateList(currentFilterType[0].equals("chat") ? R.color.user_bubble_bg : R.color.card_dark));
            tabChat.setTextColor(context.getColor(currentFilterType[0].equals("chat") ? R.color.primary_gold : R.color.text_muted));

            tabEmbedding.setBackgroundTintList(context.getColorStateList(currentFilterType[0].equals("embedding") ? R.color.user_bubble_bg : R.color.card_dark));
            tabEmbedding.setTextColor(context.getColor(currentFilterType[0].equals("embedding") ? R.color.primary_gold : R.color.text_muted));

            tabRerank.setBackgroundTintList(context.getColorStateList(currentFilterType[0].equals("rerank") ? R.color.user_bubble_bg : R.color.card_dark));
            tabRerank.setTextColor(context.getColor(currentFilterType[0].equals("rerank") ? R.color.primary_gold : R.color.text_muted));
        };

        tabAll.setOnClickListener(v -> { currentFilterType[0] = "all"; updateTabsUi.run(); applyFilter.run(); });
        tabChat.setOnClickListener(v -> { currentFilterType[0] = "chat"; updateTabsUi.run(); applyFilter.run(); });
        tabEmbedding.setOnClickListener(v -> { currentFilterType[0] = "embedding"; updateTabsUi.run(); applyFilter.run(); });
        tabRerank.setOnClickListener(v -> { currentFilterType[0] = "rerank"; updateTabsUi.run(); applyFilter.run(); });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery[0] = (s != null) ? s.toString().trim().toLowerCase(Locale.ROOT) : "";
                applyFilter.run();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Tarik model remote dari server API
        btnFetchRemote.setOnClickListener(v -> {
            pbLoading.setVisibility(View.VISIBLE);
            tvFetchRemoteLabel.setText("Menarik...");
            btnFetchRemote.setEnabled(false);

            apiClient.fetchRemoteModels(provider.providerId, new MollyApiClient.ApiCallback<List<ProviderModelItem>>() {
                @Override
                public void onSuccess(List<ProviderModelItem> remoteList) {
                    pbLoading.setVisibility(View.GONE);
                    tvFetchRemoteLabel.setText("Tarik Model Remote");
                    btnFetchRemote.setEnabled(true);

                    if (remoteList == null || remoteList.isEmpty()) {
                        Toast.makeText(context, "Tidak ada model yang ditemukan dari server remote.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Gabungkan dengan model lokal yang sudah ada
                    Map<String, ProviderModelItem> existingMap = new HashMap<>();
                    for (ProviderModelItem m : localModels) {
                        existingMap.put(m.id, m);
                    }

                    int newCount = 0;
                    for (ProviderModelItem rem : remoteList) {
                        if (!existingMap.containsKey(rem.id)) {
                            rem.enabled = false; // Default nonaktif agar user memilih
                            localModels.add(rem);
                            newCount++;
                        }
                    }

                    applyFilter.run();
                    Toast.makeText(context, "Ditemukan " + remoteList.size() + " model remote (" + newCount + " baru).", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    pbLoading.setVisibility(View.GONE);
                    tvFetchRemoteLabel.setText("Tarik Model Remote");
                    btnFetchRemote.setEnabled(true);
                    Toast.makeText(context, "Gagal menarik model remote: " + error, Toast.LENGTH_LONG).show();
                }
            });
        });

        // Tambah Model Manual
        btnAddManual.setOnClickListener(v -> {
            showAddManualModelDialog(context, newModel -> {
                localModels.add(0, newModel);
                applyFilter.run();
                Toast.makeText(context, "Model manual '" + newModel.id + "' ditambahkan.", Toast.LENGTH_SHORT).show();
            });
        });

        // Simpan perubahan model yang diaktifkan
        btnSave.setOnClickListener(v -> {
            btnSave.setEnabled(false);
            btnSave.setText("Menyimpan...");

            JsonObject payload = new JsonObject();
            JsonArray modelsArr = new JsonArray();
            for (ProviderModelItem m : localModels) {
                modelsArr.add(m.toJson());
            }
            payload.add("enabled_models", modelsArr);

            apiClient.updateModelProvider(provider.providerId, payload, new MollyApiClient.ApiCallback<ModelProviderItem>() {
                @Override
                public void onSuccess(ModelProviderItem updated) {
                    dialog.dismiss();
                    Toast.makeText(context, "Daftar model penyedia berhasil diperbarui!", Toast.LENGTH_SHORT).show();
                    if (updatedListener != null) updatedListener.onUpdated();
                }

                @Override
                public void onError(String error) {
                    btnSave.setEnabled(true);
                    btnSave.setText("Simpan Model yang Diaktifkan");
                    Toast.makeText(context, "Gagal menyimpan model: " + error, Toast.LENGTH_LONG).show();
                }
            });
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        applyFilter.run();
        dialog.show();
    }

    private interface OnManualModelAddedListener {
        void onAdded(ProviderModelItem model);
    }

    private static void showAddManualModelDialog(Context context, OnManualModelAddedListener listener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_add_manual_model, null);

        EditText etModelId = view.findViewById(R.id.etManualModelId);
        EditText etModelName = view.findViewById(R.id.etManualModelDisplayName);
        RadioGroup rgType = view.findViewById(R.id.rgManualModelType);
        RadioButton rbChat = view.findViewById(R.id.rbTypeChat);
        RadioButton rbEmbedding = view.findViewById(R.id.rbTypeEmbedding);
        RadioButton rbRerank = view.findViewById(R.id.rbTypeRerank);
        EditText etContext = view.findViewById(R.id.etManualModelContext);

        TextView btnCancel = view.findViewById(R.id.btnCancelManualModel);
        TextView btnAdd = view.findViewById(R.id.btnAddManualModelConfirm);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnAdd.setOnClickListener(v -> {
            String id = etModelId.getText().toString().trim();
            String name = etModelName.getText().toString().trim();
            if (id.isEmpty()) {
                etModelId.setError("ID model wajib diisi");
                return;
            }
            if (name.isEmpty()) name = id;

            String type = "chat";
            if (rbEmbedding.isChecked()) type = "embedding";
            else if (rbRerank.isChecked()) type = "rerank";

            ProviderModelItem model = new ProviderModelItem(id, name, type);
            model.source = "manual";
            model.enabled = true;

            String ctxStr = etContext.getText().toString().trim();
            if (!ctxStr.isEmpty()) {
                try { model.contextLength = Integer.parseInt(ctxStr); } catch (Exception ignored) {}
            }

            dialog.dismiss();
            listener.onAdded(model);
        });

        dialog.show();
    }
}
