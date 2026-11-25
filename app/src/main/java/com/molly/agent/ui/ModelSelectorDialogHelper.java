package com.molly.agent.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.molly.agent.ModelProvidersActivity;
import com.molly.agent.R;
import com.molly.agent.api.MollyApiClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModelSelectorDialogHelper {

    public static class GroupedModelEntry {
        public String id;
        public String name;
        public String provider;
        public boolean isHeader;
        public boolean isSelected;

        public GroupedModelEntry(String id, String name, String provider, boolean isHeader, boolean isSelected) {
            this.id = id;
            this.name = name;
            this.provider = provider;
            this.isHeader = isHeader;
            this.isSelected = isSelected;
        }
    }

    public interface OnModelSelectedListener {
        void onModelSelected(String modelId, String modelName);
    }

    public static void show(
            Context context,
            MollyApiClient apiClient,
            String currentSelectedModelId,
            OnModelSelectedListener listener
    ) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_model_selector_grouped, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        ImageView btnClose = view.findViewById(R.id.btnCloseModelSelector);
        RecyclerView rvModels = view.findViewById(R.id.rvGroupedModels);
        View btnConfigProviders = view.findViewById(R.id.btnConfigProvidersFooter);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnConfigProviders.setOnClickListener(v -> {
            dialog.dismiss();
            context.startActivity(new Intent(context, ModelProvidersActivity.class));
        });

        List<GroupedModelEntry> displayEntries = new ArrayList<>();
        GroupedModelAdapter adapter = new GroupedModelAdapter(displayEntries, entry -> {
            dialog.dismiss();
            if (listener != null) listener.onModelSelected(entry.id, entry.name);
        });

        rvModels.setLayoutManager(new LinearLayoutManager(context));
        rvModels.setAdapter(adapter);

        apiClient.fetchMetadataJson(new MollyApiClient.ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject root) {
                displayEntries.clear();
                Map<String, List<JsonObject>> byProvider = new LinkedHashMap<>();

                if (root.has("models") && root.get("models").isJsonArray()) {
                    JsonArray arr = root.getAsJsonArray("models");
                    for (JsonElement el : arr) {
                        JsonObject m = el.getAsJsonObject();
                        String prov = m.has("provider") ? m.get("provider").getAsString() : "Umum";
                        if (!byProvider.containsKey(prov)) {
                            byProvider.put(prov, new ArrayList<>());
                        }
                        byProvider.get(prov).add(m);
                    }
                }

                for (Map.Entry<String, List<JsonObject>> entry : byProvider.entrySet()) {
                    String providerName = entry.getKey();
                    boolean first = true;
                    for (JsonObject m : entry.getValue()) {
                        String id = m.has("id") ? m.get("id").getAsString() : "";
                        String name = m.has("name") ? m.get("name").getAsString() : id;
                        boolean selected = id.equalsIgnoreCase(currentSelectedModelId)
                                || name.equalsIgnoreCase(currentSelectedModelId)
                                || (currentSelectedModelId != null && currentSelectedModelId.endsWith(id));

                        GroupedModelEntry row = new GroupedModelEntry(id, name, providerName, first, selected);
                        displayEntries.add(row);
                        first = false;
                    }
                }

                if (displayEntries.isEmpty()) {
                    populateFallbackEntries(displayEntries, currentSelectedModelId);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(String error) {
                displayEntries.clear();
                populateFallbackEntries(displayEntries, currentSelectedModelId);
                adapter.notifyDataSetChanged();
            }
        });

        dialog.show();
    }

    private static void populateFallbackEntries(List<GroupedModelEntry> displayEntries, String selectedId) {
        // Antigravity Tools
        displayEntries.add(new GroupedModelEntry("gemini-4-hydrogen-medium", "gemini-4-hydrogen-medium", "Antigravity Tools", true, "gemini-4-hydrogen-medium".equals(selectedId)));
        displayEntries.add(new GroupedModelEntry("gemini-3.8-flash-medium", "gemini-3.8-flash-medium", "Antigravity Tools", false, "gemini-3.8-flash-medium".equals(selectedId)));

        // SiliconFlow
        displayEntries.add(new GroupedModelEntry("deepseek-ai/DeepSeek-V4.1-Flash", "deepseek-ai/DeepSeek-V4.1-Flash", "SiliconFlow", true, "deepseek-ai/DeepSeek-V4.1-Flash".equals(selectedId)));
        displayEntries.add(new GroupedModelEntry("zai-org/GLM-5.3", "zai-org/GLM-5.3", "SiliconFlow", false, "zai-org/GLM-5.3".equals(selectedId)));
    }

    static class GroupedModelAdapter extends RecyclerView.Adapter<GroupedModelAdapter.ViewHolder> {
        private final List<GroupedModelEntry> items;
        private final OnItemClickListener listener;

        public interface OnItemClickListener {
            void onItemClick(GroupedModelEntry item);
        }

        public GroupedModelAdapter(List<GroupedModelEntry> items, OnItemClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_model_selector_row, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            GroupedModelEntry item = items.get(position);

            if (item.isHeader) {
                holder.tvHeader.setVisibility(View.VISIBLE);
                holder.tvHeader.setText(item.provider);
            } else {
                holder.tvHeader.setVisibility(View.GONE);
            }

            holder.tvName.setText(item.name);
            holder.tvCheckmark.setVisibility(item.isSelected ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvHeader;
            TextView tvName;
            TextView tvCheckmark;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvHeader = itemView.findViewById(R.id.tvModelProviderHeader);
                tvName = itemView.findViewById(R.id.tvModelRowName);
                tvCheckmark = itemView.findViewById(R.id.tvModelRowCheckmark);
            }
        }
    }
}
