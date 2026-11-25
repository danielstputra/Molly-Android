package com.molly.agent.ui;

import android.app.AlertDialog;
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

        // Helper untuk mengisi data
        Runnable bindData = () -> {
            ContextUsageInfo info = initialInfo != null ? initialInfo : new ContextUsageInfo();

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
            AlertDialog confirmDialog = new AlertDialog.Builder(context)
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
                    .create();
            confirmDialog.show();
            if (confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(context.getColor(R.color.primary_gold));
            }
            if (confirmDialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
                confirmDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF9CA3AF);
            }
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
    }
}
