package com.molly.agent.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.molly.agent.R;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.ChangelogVersion;
import com.molly.agent.update.AppUpdateManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ChangelogDialogHelper {

    public static void show(Activity activity, MollyApiClient apiClient) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_changelog, null);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvSubtitle = dialogView.findViewById(R.id.tvChangelogSubtitle);
        ProgressBar pbLoading = dialogView.findViewById(R.id.pbChangelogLoading);
        LinearLayout llContainer = dialogView.findViewById(R.id.llChangelogVersionsContainer);
        TextView btnClose = dialogView.findViewById(R.id.btnChangelogClose);
        TextView btnUpdateNow = dialogView.findViewById(R.id.btnChangelogUpdateNow);

        int currentCode = AppUpdateManager.getCurrentVersionCode(activity);
        String currentName = AppUpdateManager.getCurrentVersionName(activity);

        tvSubtitle.setText("Versi Terpasang: " + currentName + " (Build " + currentCode + ")");
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        if (!apiClient.isConfigured()) {
            pbLoading.setVisibility(View.GONE);
            tvSubtitle.setText("Versi Terpasang: " + currentName + " (Server Offline)");
            populateFallbackChangelog(activity, llContainer, currentCode, currentName);
            return;
        }

        String endpoint = apiClient.getServerUrl() + "/api/system/mobile/changelog?version_code=" + currentCode;
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .get()
                .build();

        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    pbLoading.setVisibility(View.GONE);
                    tvSubtitle.setText("Versi Terpasang: " + currentName + " (Offline)");
                    populateFallbackChangelog(activity, llContainer, currentCode, currentName);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    activity.runOnUiThread(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        pbLoading.setVisibility(View.GONE);
                        populateFallbackChangelog(activity, llContainer, currentCode, currentName);
                    });
                    return;
                }

                String bodyStr = response.body() != null ? response.body().string() : "{}";
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    pbLoading.setVisibility(View.GONE);

                    try {
                        JsonObject root = JsonParser.parseString(bodyStr).getAsJsonObject();
                        boolean hasUpdate = root.has("has_update") && root.get("has_update").getAsBoolean();
                        String latestName = root.has("latest_version_name") ? root.get("latest_version_name").getAsString() : currentName;

                        if (hasUpdate) {
                            tvSubtitle.setText("Versi Terpasang: " + currentName + " • Pembaruan " + latestName + " Tersedia! 🚀");
                            tvSubtitle.setTextColor(Color.parseColor("#38BDF8"));
                            btnUpdateNow.setVisibility(View.VISIBLE);
                            btnUpdateNow.setOnClickListener(v -> {
                                dialog.dismiss();
                                AppUpdateManager.checkForUpdate(activity, apiClient, true);
                            });
                        } else {
                            tvSubtitle.setText("Versi Terpasang: " + currentName + " (Versi Terbaru ✨)");
                            tvSubtitle.setTextColor(Color.parseColor("#10B981"));
                            btnUpdateNow.setVisibility(View.GONE);
                        }

                        List<ChangelogVersion> versions = new ArrayList<>();
                        if (root.has("versions") && root.get("versions").isJsonArray()) {
                            JsonArray arr = root.getAsJsonArray("versions");
                            for (JsonElement elem : arr) {
                                if (!elem.isJsonObject()) continue;
                                JsonObject obj = elem.getAsJsonObject();
                                int vCode = obj.has("version_code") ? obj.get("version_code").getAsInt() : 0;
                                String vName = obj.has("version_name") ? obj.get("version_name").getAsString() : "";
                                String rDate = obj.has("release_date") ? obj.get("release_date").getAsString() : "";
                                String title = obj.has("title") ? obj.get("title").getAsString() : "";
                                String tag = obj.has("tag") ? obj.get("tag").getAsString() : "";

                                List<String> changes = new ArrayList<>();
                                if (obj.has("changes") && obj.get("changes").isJsonArray()) {
                                    for (JsonElement c : obj.getAsJsonArray("changes")) {
                                        changes.add(c.getAsString());
                                    }
                                }
                                versions.add(new ChangelogVersion(vCode, vName, rDate, title, tag, changes));
                            }
                        }

                        renderChangelogVersions(activity, llContainer, versions, currentCode);

                    } catch (Exception ex) {
                        populateFallbackChangelog(activity, llContainer, currentCode, currentName);
                    }
                });
            }
        });
    }

    private static void renderChangelogVersions(Activity activity, LinearLayout container, List<ChangelogVersion> versions, int currentCode) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(activity);

        for (ChangelogVersion ver : versions) {
            View itemView = inflater.inflate(R.layout.item_changelog_version, container, false);

            TextView tvBadge = itemView.findViewById(R.id.tvItemVersionBadge);
            TextView tvTag = itemView.findViewById(R.id.tvItemVersionTag);
            TextView tvDate = itemView.findViewById(R.id.tvItemReleaseDate);
            TextView tvTitle = itemView.findViewById(R.id.tvItemVersionTitle);
            LinearLayout llChanges = itemView.findViewById(R.id.llItemChangesContainer);

            tvBadge.setText(ver.getVersionName());
            tvDate.setText(ver.getReleaseDate());
            tvTitle.setText(ver.getTitle());

            // Tentukan status tag dan styling warna
            if (ver.getVersionCode() == currentCode) {
                tvTag.setText("TERPASANG");
                tvTag.setTextColor(Color.parseColor("#34D399"));
                tvTag.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#064E3B")));
                tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#059669")));
            } else if (ver.getVersionCode() > currentCode) {
                tvTag.setText("BARU ⚡");
                tvTag.setTextColor(Color.parseColor("#F59E0B"));
                tvTag.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#451A03")));
                tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#06B6D4")));
            } else {
                tvTag.setText(ver.getTag().isEmpty() ? "STABLE" : ver.getTag());
                tvTag.setTextColor(Color.parseColor("#9CA3AF"));
                tvTag.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1E293B")));
                tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#334155")));
            }

            // Tambahkan daftar perubahan
            llChanges.removeAllViews();
            for (String change : ver.getChanges()) {
                TextView tvPoint = new TextView(activity);
                tvPoint.setText("• " + change);
                tvPoint.setTextColor(Color.parseColor("#D1D5DB"));
                tvPoint.setTextSize(12f);
                tvPoint.setLineSpacing(4f, 1f);
                tvPoint.setPadding(0, 2, 0, 4);
                llChanges.addView(tvPoint);
            }

            container.addView(itemView);
        }
    }

    private static void populateFallbackChangelog(Activity activity, LinearLayout container, int currentCode, String currentName) {
        List<ChangelogVersion> fallbacks = new ArrayList<>();
        List<String> changes = new ArrayList<>();
        changes.add("Built-in Tool Native generate_image untuk pembuatan gambar AI visual (Google Imagen 3, OpenAI DALL-E 3, SiliconFlow).");
        changes.add("Auto-Config API Key langsung via obrolan (GEMINI_API_KEY=... / OPENAI_API_KEY=...).");
        changes.add("Fitur Lengkap Attach File: Foto/Gambar, Dokumen, Musik/Audio, Video, dan Berkas.");
        changes.add("Widget Text & Code Block dengan Tombol floating [menyalin] mengambang.");
        changes.add("Widget Prompt interaktif dengan aksen teal (#06B6D4) & copy button.");
        changes.add("Widget Generative UI Form (Radio pill-chips, Input text, Action button).");
        changes.add("Pilihan Model AI 100% responsif di toolbar chat.");

        fallbacks.add(new ChangelogVersion(currentCode, currentName, "25 Nov 2026", "Built-in Image Generator, Attachments & Generative UI", "TERPASANG", changes));
        renderChangelogVersions(activity, container, fallbacks, currentCode);
    }
}
