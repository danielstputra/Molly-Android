package com.molly.agent.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.molly.agent.R;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.ContextUsageInfo;

public class ContextUsageDialogHelper {

    public interface OnUsageUpdatedListener {
        void onUpdated(ContextUsageInfo info);
    }

    public static void show(Context context, MollyApiClient apiClient, ContextUsageInfo initialInfo, OnUsageUpdatedListener listener) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_context_usage_status, null);

        TextView tvMainPercent = dialogView.findViewById(R.id.tvMainPercent);
        TextView tvContextUsageRatio = dialogView.findViewById(R.id.tvContextUsageRatio);
        ProgressBar pbContextUsage = dialogView.findViewById(R.id.pbContextUsage);
        TextView tvTotalModelUsage = dialogView.findViewById(R.id.tvTotalModelUsage);
        TextView tvCacheHitRatio = dialogView.findViewById(R.id.tvCacheHitRatio);

        TextView tvModelName = dialogView.findViewById(R.id.tvModelName);
        TextView tvCallCount = dialogView.findViewById(R.id.tvCallCount);
        TextView tvInputOutput = dialogView.findViewById(R.id.tvInputOutput);
        TextView tvCacheStats = dialogView.findViewById(R.id.tvCacheStats);
        TextView tvReasoningStats = dialogView.findViewById(R.id.tvReasoningStats);

        View segSystem = dialogView.findViewById(R.id.segSystem);
        View segTools = dialogView.findViewById(R.id.segTools);
        View segContent = dialogView.findViewById(R.id.segContent);
        View segToolMsg = dialogView.findViewById(R.id.segToolMsg);

        TextView tvSystemTokens = dialogView.findViewById(R.id.tvSystemTokens);
        TextView tvToolsCountLabel = dialogView.findViewById(R.id.tvToolsCountLabel);
        TextView tvToolsTokens = dialogView.findViewById(R.id.tvToolsTokens);
        TextView tvContentCountLabel = dialogView.findViewById(R.id.tvContentCountLabel);
        TextView tvContentTokens = dialogView.findViewById(R.id.tvContentTokens);
        TextView tvToolMsgCountLabel = dialogView.findViewById(R.id.tvToolMsgCountLabel);
        TextView tvToolMsgTokens = dialogView.findViewById(R.id.tvToolMsgTokens);

        TextView tvRecentCallTokens = dialogView.findViewById(R.id.tvRecentCallTokens);
        ImageView btnCompressContext = dialogView.findViewById(R.id.btnCompressContext);

        View llWarningsBox = dialogView.findViewById(R.id.llWarningsBox);
        TextView tvWarningMessage = dialogView.findViewById(R.id.tvWarningMessage);

        View llArtifactsSection = dialogView.findViewById(R.id.llArtifactsSection);
        View btnToggleArtifacts = dialogView.findViewById(R.id.btnToggleArtifacts);
        ImageView ivArtifactsChevron = dialogView.findViewById(R.id.ivArtifactsChevron);
        TextView tvArtifactsCount = dialogView.findViewById(R.id.tvArtifactsCount);
        LinearLayout llArtifactsContainer = dialogView.findViewById(R.id.llArtifactsContainer);

        ProgressBar pbStatusRefreshing = dialogView.findViewById(R.id.pbStatusRefreshing);
        ImageView btnRefreshStatus = dialogView.findViewById(R.id.btnRefreshStatus);
        View btnCloseContextStatus = dialogView.findViewById(R.id.btnCloseContextStatus);
        View btnCloseContextBottom = dialogView.findViewById(R.id.btnCloseContextBottom);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        if (btnCloseContextStatus != null) {
            btnCloseContextStatus.setOnClickListener(v -> dialog.dismiss());
        }
        if (btnCloseContextBottom != null) {
            btnCloseContextBottom.setOnClickListener(v -> dialog.dismiss());
        }

        final boolean[] isArtifactsExpanded = {true};
        if (btnToggleArtifacts != null) {
            btnToggleArtifacts.setOnClickListener(v -> {
                isArtifactsExpanded[0] = !isArtifactsExpanded[0];
                if (llArtifactsContainer != null) {
                    llArtifactsContainer.setVisibility(isArtifactsExpanded[0] ? View.VISIBLE : View.GONE);
                }
                if (ivArtifactsChevron != null) {
                    ivArtifactsChevron.setImageResource(isArtifactsExpanded[0] ? R.drawable.ic_chevron_down : R.drawable.ic_chevron_up);
                }
            });
        }

        // Helper untuk mengisi data
        Runnable bindData = () -> {
            ContextUsageInfo info = initialInfo != null ? initialInfo : new ContextUsageInfo();

            // 0. Bind Warnings (Matches Web Status Panel)
            if (llWarningsBox != null) {
                if (info.warnings != null && !info.warnings.isEmpty()) {
                    llWarningsBox.setVisibility(View.VISIBLE);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < info.warnings.size(); i++) {
                        if (i > 0) sb.append("\n");
                        sb.append(info.warnings.get(i));
                    }
                    if (tvWarningMessage != null) tvWarningMessage.setText(sb.toString());
                } else {
                    llWarningsBox.setVisibility(View.GONE);
                }
            }

            tvMainPercent.setText(info.percent + "%");
            tvContextUsageRatio.setText(ContextUsageInfo.formatToken(info.usedTokens) + " / " + ContextUsageInfo.formatToken(info.limitTokens));

            pbContextUsage.setProgress(Math.max(1, info.percent));
            if (info.percent >= 90) {
                pbContextUsage.setProgressTintList(ColorStateList.valueOf(0xFFEF4444));
            } else if (info.percent >= 75) {
                pbContextUsage.setProgressTintList(ColorStateList.valueOf(0xFFF59E0B));
            } else {
                pbContextUsage.setProgressTintList(ColorStateList.valueOf(0xFF38BDF8));
            }

            tvTotalModelUsage.setText(ContextUsageInfo.formatToken(info.totalModelUsage));
            tvCacheHitRatio.setText(info.cacheHitRatio + "%");

            String mName = !info.modelName.isEmpty() ? info.modelName : apiClient.getSelectedModelId();
            tvModelName.setText(mName);
            tvCallCount.setText(info.modelCallCount + " calls");

            tvInputOutput.setText(ContextUsageInfo.formatToken(info.modelInputTokens) + " / " + ContextUsageInfo.formatToken(info.modelOutputTokens));
            int cacheRatio = info.modelCacheHitRatio >= 0 ? info.modelCacheHitRatio : info.cacheHitRatio;
            tvCacheStats.setText(ContextUsageInfo.formatToken(info.modelCacheTokens) + " · " + cacheRatio + "%");
            tvReasoningStats.setText(ContextUsageInfo.formatToken(info.modelReasoningTokens));

            // Weight calculation for stacked bar - hanya tampilkan segmen yang memiliki token > 0
            long sTok = info.systemTokens;
            long tTok = info.toolsTokens;
            long cTok = info.contentTokens;
            long mTok = info.toolMsgTokens;

            updateSegmentBar(segSystem, sTok);
            updateSegmentBar(segTools, tTok);
            updateSegmentBar(segContent, cTok);
            updateSegmentBar(segToolMsg, mTok);

            tvSystemTokens.setText(ContextUsageInfo.formatToken(info.systemTokens));
            tvToolsCountLabel.setText("Definisi alat (" + info.toolsCount + ")");
            tvToolsTokens.setText(ContextUsageInfo.formatToken(info.toolsTokens));

            tvContentCountLabel.setText("Pesan konten (" + info.contentCount + ")");
            tvContentTokens.setText(ContextUsageInfo.formatToken(info.contentTokens));

            tvToolMsgCountLabel.setText("Pesan alat (" + info.toolMsgCount + ")");
            tvToolMsgTokens.setText(ContextUsageInfo.formatToken(info.toolMsgTokens));

            tvRecentCallTokens.setText("Input " + ContextUsageInfo.formatToken(info.recentInputTokens) + " · Output " + ContextUsageInfo.formatToken(info.recentOutputTokens));

            // 4. Bind Artifacts (Matches Web Status Panel)
            if (llArtifactsSection != null) {
                if (info.artifacts != null && !info.artifacts.isEmpty()) {
                    llArtifactsSection.setVisibility(View.VISIBLE);
                    if (tvArtifactsCount != null) {
                        tvArtifactsCount.setText(String.valueOf(info.artifacts.size()));
                    }
                    if (llArtifactsContainer != null) {
                        llArtifactsContainer.removeAllViews();
                        float density = context.getResources().getDisplayMetrics().density;
                        int pad8 = (int) (8 * density);
                        int pad12 = (int) (12 * density);
                        int margin4 = (int) (4 * density);
                        int iconSize = (int) (18 * density);

                        for (com.molly.agent.model.ArtifactItem art : info.artifacts) {
                            LinearLayout row = new LinearLayout(context);
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                            row.setPadding(pad12, pad8, pad12, pad8);
                            row.setBackgroundResource(R.drawable.bg_action_pill);
                            row.setBackgroundTintList(ColorStateList.valueOf(0xFF1E242C));
                            row.setClickable(true);
                            row.setFocusable(true);

                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            lp.topMargin = margin4;
                            lp.bottomMargin = margin4;
                            row.setLayoutParams(lp);

                            ImageView icon = new ImageView(context);
                            icon.setImageResource(R.drawable.ic_artifact_file);
                            icon.setImageTintList(ColorStateList.valueOf(0xFF34D399));
                            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
                            icon.setLayoutParams(iconLp);
                            row.addView(icon);

                            TextView tvName = new TextView(context);
                            tvName.setText(art.getName());
                            tvName.setTextColor(Color.parseColor("#E2E8F0"));
                            tvName.setTextSize(13);
                            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                            nameLp.leftMargin = pad8;
                            nameLp.setMarginStart(pad8);
                            tvName.setLayoutParams(nameLp);
                            row.addView(tvName);

                            row.setOnClickListener(v -> {
                                if (context instanceof android.app.Activity) {
                                    ArtifactPreviewDialogHelper.show((android.app.Activity) context, apiClient, art, new ArtifactPreviewDialogHelper.OnArtifactActionListener() {
                                        @Override
                                        public void onDownload(com.molly.agent.model.ArtifactItem it) {
                                            android.widget.Toast.makeText(context, "Mengunduh " + it.getName() + "...", android.widget.Toast.LENGTH_SHORT).show();
                                            apiClient.downloadArtifactFile(it.getPath(), it.getName(), new com.molly.agent.api.MollyApiClient.ApiCallback<java.io.File>() {
                                                @Override
                                                public void onSuccess(java.io.File file) {
                                                    android.widget.Toast.makeText(context, "Berhasil diunduh ke folder Download: " + file.getName(), android.widget.Toast.LENGTH_LONG).show();
                                                }

                                                @Override
                                                public void onError(String error) {
                                                    android.widget.Toast.makeText(context, "Gagal mengunduh: " + error, android.widget.Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                        }

                                        @Override
                                        public void onSave(com.molly.agent.model.ArtifactItem it) {
                                            apiClient.saveArtifactToWorkspace(it.getPath(), "/saved_artifacts", new com.molly.agent.api.MollyApiClient.ApiCallback<Boolean>() {
                                                @Override
                                                public void onSuccess(Boolean result) {
                                                    android.widget.Toast.makeText(context, "Berhasil disimpan ke Ruang Kerja (/saved_artifacts)", android.widget.Toast.LENGTH_SHORT).show();
                                                }

                                                @Override
                                                public void onError(String error) {
                                                    android.widget.Toast.makeText(context, "Gagal menyimpan: " + error, android.widget.Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                        }
                                    });
                                }
                            });

                            llArtifactsContainer.addView(row);
                        }
                    }
                } else {
                    llArtifactsSection.setVisibility(View.GONE);
                }
            }
        };

        bindData.run();

        // Refresh action
        btnRefreshStatus.setOnClickListener(v -> {
            pbStatusRefreshing.setVisibility(View.VISIBLE);
            btnRefreshStatus.setVisibility(View.GONE);

            apiClient.fetchContextUsage(new MollyApiClient.ApiCallback<ContextUsageInfo>() {
                @Override
                public void onSuccess(ContextUsageInfo result) {
                    pbStatusRefreshing.setVisibility(View.GONE);
                    btnRefreshStatus.setVisibility(View.VISIBLE);
                    if (result != null) {
                        copyInto(result, initialInfo);
                        bindData.run();
                        if (listener != null) listener.onUpdated(result);
                    }
                }

                @Override
                public void onError(String error) {
                    pbStatusRefreshing.setVisibility(View.GONE);
                    btnRefreshStatus.setVisibility(View.VISIBLE);
                    Toast.makeText(context, "Gagal memuat status: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Compress Context action
        btnCompressContext.setOnClickListener(v -> {
            new MollyDialog.Builder(context)
                    .setTitle("Kompres Konteks?")
                    .setMessage("Apakah Anda ingin memicu kompresi riwayat konteks sesi ini untuk menghemat kuota token?")
                    .setPositiveButton("Kompres", (d, w) -> {
                        Toast.makeText(context, "Memicu kompresi konteks...", Toast.LENGTH_SHORT).show();
                        apiClient.compressContext(new MollyApiClient.ApiCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean result) {
                                Toast.makeText(context, "Kompresi konteks berhasil diproses", Toast.LENGTH_SHORT).show();
                                btnRefreshStatus.performClick();
                            }

                            @Override
                            public void onError(String error) {
                                Toast.makeText(context, "Kompresi gagal: " + error, Toast.LENGTH_LONG).show();
                            }
                        });
                    })
                    .setNegativeButton("Batal", null)
                    .show();
        });

        dialog.show();

        // Otomatis refresh awal jika belum ada data nyata
        if (initialInfo == null || initialInfo.usedTokens == 0) {
            btnRefreshStatus.performClick();
        }
    }

    private static void updateSegmentBar(View segView, long tokens) {
        if (segView == null) return;
        if (tokens > 0) {
            segView.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) segView.getLayoutParams();
            lp.weight = tokens;
            segView.setLayoutParams(lp);
        } else {
            segView.setVisibility(View.GONE);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) segView.getLayoutParams();
            lp.weight = 0;
            segView.setLayoutParams(lp);
        }
    }

    private static void copyInto(ContextUsageInfo src, ContextUsageInfo target) {
        if (src == null || target == null) return;
        target.percent = src.percent;
        target.usedTokens = src.usedTokens;
        target.limitTokens = src.limitTokens;
        target.totalModelUsage = src.totalModelUsage;
        target.cacheHitRatio = src.cacheHitRatio;
        target.modelName = src.modelName;
        target.modelCallCount = src.modelCallCount;
        target.modelInputTokens = src.modelInputTokens;
        target.modelOutputTokens = src.modelOutputTokens;
        target.modelCacheTokens = src.modelCacheTokens;
        target.modelCacheHitRatio = src.modelCacheHitRatio;
        target.modelReasoningTokens = src.modelReasoningTokens;
        target.systemTokens = src.systemTokens;
        target.toolsTokens = src.toolsTokens;
        target.toolsCount = src.toolsCount;
        target.contentTokens = src.contentTokens;
        target.contentCount = src.contentCount;
        target.toolMsgTokens = src.toolMsgTokens;
        target.toolMsgCount = src.toolMsgCount;
        target.summaryActive = src.summaryActive;
        target.summaryTokens = src.summaryTokens;
        target.recentInputTokens = src.recentInputTokens;
        target.recentOutputTokens = src.recentOutputTokens;
        target.artifacts.clear();
        if (src.artifacts != null) target.artifacts.addAll(src.artifacts);
        target.warnings.clear();
        if (src.warnings != null) target.warnings.addAll(src.warnings);
    }
}
