package com.molly.agent.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.molly.agent.R;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.MentionItem;

import java.util.ArrayList;
import java.util.List;

public class MentionPickerHelper {

    public interface OnMentionSelectedListener {
        void onMentionSelected(MentionItem item);
    }

    public static void show(
            Context context,
            MollyApiClient apiClient,
            String initialQuery,
            OnMentionSelectedListener listener
    ) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_mention_picker, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (context.getResources().getDisplayMetrics().widthPixels * 0.94),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        EditText etSearch = view.findViewById(R.id.etSearchMention);
        ImageView btnClose = view.findViewById(R.id.btnCloseMention);
        RecyclerView rvList = view.findViewById(R.id.rvMentionList);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        List<MentionItem> allItems = new ArrayList<>();
        List<MentionItem> filteredItems = new ArrayList<>();

        MentionAdapter adapter = new MentionAdapter(filteredItems, item -> {
            dialog.dismiss();
            if (listener != null) listener.onMentionSelected(item);
        });

        rvList.setLayoutManager(new LinearLayoutManager(context));
        rvList.setAdapter(adapter);

        // Ambil data metadata (skills dan agents) dari backend Molly
        apiClient.fetchMetadataJson(new MollyApiClient.ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject root) {
                allItems.clear();

                // 1. Tambah Skills
                if (root.has("skills") && root.get("skills").isJsonArray()) {
                    JsonArray skillsArr = root.getAsJsonArray("skills");
                    for (int i = 0; i < skillsArr.size(); i++) {
                        JsonObject s = skillsArr.get(i).getAsJsonObject();
                        String id = s.has("id") ? s.get("id").getAsString() : "";
                        String name = s.has("name") ? s.get("name").getAsString() : id;
                        String desc = s.has("description") ? s.get("description").getAsString() : "";
                        MentionItem item = new MentionItem(id, name, desc, MentionItem.TYPE_SKILL, "🤹");
                        if (i == 0) {
                            item.showGroupHeader = true;
                            item.groupHeaderTitle = "Skills";
                        }
                        allItems.add(item);
                    }
                }

                // 2. Tambah Agents
                if (root.has("agents") && root.get("agents").isJsonArray()) {
                    JsonArray agentsArr = root.getAsJsonArray("agents");
                    for (int i = 0; i < agentsArr.size(); i++) {
                        JsonObject a = agentsArr.get(i).getAsJsonObject();
                        String id = a.has("id") ? a.get("id").getAsString() : "";
                        String name = a.has("name") ? a.get("name").getAsString() : id;
                        String desc = a.has("description") ? a.get("description").getAsString() : "";
                        MentionItem item = new MentionItem(id, name, desc, MentionItem.TYPE_AGENT, "🤖");
                        if (i == 0) {
                            item.showGroupHeader = true;
                            item.groupHeaderTitle = "Agents";
                        }
                        allItems.add(item);
                    }
                }

                filter(initialQuery, allItems, filteredItems, adapter);
            }

            @Override
            public void onError(String error) {
                // Fallback default skills & agents
                populateFallbackItems(allItems);
                filter(initialQuery, allItems, filteredItems, adapter);
            }
        });

        if (initialQuery != null && !initialQuery.isEmpty()) {
            etSearch.setText(initialQuery);
            etSearch.setSelection(initialQuery.length());
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s != null ? s.toString() : "", allItems, filteredItems, adapter);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        dialog.show();
    }

    private static void populateFallbackItems(List<MentionItem> allItems) {
        allItems.clear();
        MentionItem s1 = new MentionItem("deep_research", "Riset Mendalam", "Metodologi orkestrasi riset mendalam: memperjelas cakupan, memecah perencanaan...", MentionItem.TYPE_SKILL, "🤹");
        s1.showGroupHeader = true;
        s1.groupHeaderTitle = "Skills";
        allItems.add(s1);

        allItems.add(new MentionItem("mysql_reporter", "MySQL Reporter", "Menghasilkan laporan kueri MySQL...", MentionItem.TYPE_SKILL, "📊"));
        allItems.add(new MentionItem("knowledge_base", "Basis Pengetahuan", "Gunakan basis pengetahuan Molly untuk melakukan penelusuran...", MentionItem.TYPE_SKILL, "📖"));
        allItems.add(new MentionItem("image_generator", "Pembuat Gambar", "Menghasilkan dan menyimpan gambar di dalam sandbox Agent...", MentionItem.TYPE_SKILL, "🎨"));
        allItems.add(new MentionItem("chat_components", "Komponen Percakapan", "Menampilkan gambar, kartu grafis-teks, dan diagram numerik...", MentionItem.TYPE_SKILL, "🔲"));

        MentionItem a1 = new MentionItem("default-chatbot", "Asisten Cerdas", "Chatbot dasar yang dapat menjawab pertanyaan serta mengaktifkan tools...", MentionItem.TYPE_AGENT, "🤖");
        a1.showGroupHeader = true;
        a1.groupHeaderTitle = "Agents";
        allItems.add(a1);
        allItems.add(new MentionItem("deep-research-agent", "Riset Mendalam (Agent)", "Untuk tugas riset mendalam multi-sumber yang memerlukan verifikasi fakta...", MentionItem.TYPE_AGENT, "🤖"));
    }

    private static void filter(String query, List<MentionItem> all, List<MentionItem> target, MentionAdapter adapter) {
        target.clear();
        String q = query != null ? query.trim().toLowerCase() : "";
        for (MentionItem it : all) {
            if (q.isEmpty() || it.name.toLowerCase().contains(q) || it.description.toLowerCase().contains(q)) {
                target.add(it);
            }
        }
        adapter.notifyDataSetChanged();
    }

    static class MentionAdapter extends RecyclerView.Adapter<MentionAdapter.ViewHolder> {
        private final List<MentionItem> items;
        private final OnMentionSelectedListener listener;

        public MentionAdapter(List<MentionItem> items, OnMentionSelectedListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mention_row, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MentionItem item = items.get(position);

            if (item.showGroupHeader && item.groupHeaderTitle != null && !item.groupHeaderTitle.isEmpty()) {
                holder.tvGroupHeader.setVisibility(View.VISIBLE);
                holder.tvGroupHeader.setText(item.groupHeaderTitle);
            } else {
                holder.tvGroupHeader.setVisibility(View.GONE);
            }

            holder.tvIcon.setText(item.icon);
            holder.tvName.setText(item.name);
            holder.tvDesc.setText(item.description);
            holder.tvBadge.setText(MentionItem.TYPE_AGENT.equals(item.type) ? "Agent" : "Skill");

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onMentionSelected(item);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvGroupHeader;
            TextView tvIcon;
            TextView tvName;
            TextView tvDesc;
            TextView tvBadge;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvGroupHeader = itemView.findViewById(R.id.tvMentionGroupHeader);
                tvIcon = itemView.findViewById(R.id.tvMentionIcon);
                tvName = itemView.findViewById(R.id.tvMentionName);
                tvDesc = itemView.findViewById(R.id.tvMentionDesc);
                tvBadge = itemView.findViewById(R.id.tvMentionTypeBadge);
            }
        }
    }
}
