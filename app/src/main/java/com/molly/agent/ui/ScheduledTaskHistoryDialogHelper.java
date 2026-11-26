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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.molly.agent.R;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.ScheduledRunItem;
import com.molly.agent.model.ScheduledTaskItem;

import java.util.ArrayList;
import java.util.List;

public class ScheduledTaskHistoryDialogHelper {

    public interface OnSessionOpenListener {
        void onOpenSession(String threadId);
    }

    public static void show(Context context, MollyApiClient apiClient, ScheduledTaskItem task, OnSessionOpenListener sessionListener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_scheduled_task_history, null);

        TextView tvTaskName = view.findViewById(R.id.tvHistoryTaskName);
        ImageView btnRefresh = view.findViewById(R.id.btnRefreshHistory);
        ProgressBar pbLoading = view.findViewById(R.id.pbHistoryLoading);
        TextView tvEmpty = view.findViewById(R.id.tvEmptyHistory);
        RecyclerView rvRuns = view.findViewById(R.id.rvHistoryRuns);
        TextView btnClose = view.findViewById(R.id.btnCloseHistory);

        tvTaskName.setText(task.name != null ? task.name : "Tugas " + task.id);

        RunsAdapter adapter = new RunsAdapter(context, sessionListener);
        rvRuns.setLayoutManager(new LinearLayoutManager(context));
        rvRuns.setAdapter(adapter);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        dialog.setCancelable(true);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        Runnable loader = () -> {
            pbLoading.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
            rvRuns.setVisibility(View.GONE);

            apiClient.fetchScheduledTaskHistory(task.id, new MollyApiClient.ApiCallback<List<ScheduledRunItem>>() {
                @Override
                public void onSuccess(List<ScheduledRunItem> result) {
                    pbLoading.setVisibility(View.GONE);
                    if (result == null || result.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        rvRuns.setVisibility(View.GONE);
                    } else {
                        tvEmpty.setVisibility(View.GONE);
                        rvRuns.setVisibility(View.VISIBLE);
                        adapter.setItems(result);
                    }
                }

                @Override
                public void onError(String error) {
                    pbLoading.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("Gagal memuat riwayat: " + error);
                    Toast.makeText(context, "Gagal memuat riwayat: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        };

        btnRefresh.setOnClickListener(v -> loader.run());
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        loader.run();
    }

    // ==========================================
    // Inner Adapter for Runs History
    // ==========================================
    static class RunsAdapter extends RecyclerView.Adapter<RunsAdapter.ViewHolder> {
        private final Context context;
        private final OnSessionOpenListener sessionListener;
        private final List<ScheduledRunItem> items = new ArrayList<>();

        public RunsAdapter(Context context, OnSessionOpenListener sessionListener) {
            this.context = context;
            this.sessionListener = sessionListener;
        }

        public void setItems(List<ScheduledRunItem> newItems) {
            this.items.clear();
            this.items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_scheduled_run, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ScheduledRunItem item = items.get(position);

            holder.tvStatus.setText(item.getStatusLabel());
            holder.tvStatus.setTextColor(item.getStatusColor());
            holder.tvStatus.setBackgroundTintList(ColorStateList.valueOf(item.getStatusColor() & 0x33FFFFFF | 0x22000000));

            boolean isManual = "manual".equalsIgnoreCase(item.trigger);
            holder.tvTrigger.setText(isManual ? "⚡ Manual" : "⏰ Jadwal");
            holder.tvTime.setText(item.getFormattedTime());

            if (item.errorMessage != null && !item.errorMessage.trim().isEmpty()) {
                holder.tvError.setVisibility(View.VISIBLE);
                holder.tvError.setText(item.errorMessage.trim());
            } else {
                holder.tvError.setVisibility(View.GONE);
            }

            if (item.sessionAvailable && item.threadId != null && !item.threadId.isEmpty()) {
                holder.llSessionAction.setVisibility(View.VISIBLE);
                holder.btnOpenSession.setOnClickListener(v -> {
                    if (sessionListener != null) {
                        sessionListener.onOpenSession(item.threadId);
                    }
                });
            } else {
                holder.llSessionAction.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvStatus;
            TextView tvTrigger;
            TextView tvTime;
            TextView tvError;
            View llSessionAction;
            TextView btnOpenSession;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvStatus = itemView.findViewById(R.id.tvRunStatusBadge);
                tvTrigger = itemView.findViewById(R.id.tvRunTrigger);
                tvTime = itemView.findViewById(R.id.tvRunTime);
                tvError = itemView.findViewById(R.id.tvRunError);
                llSessionAction = itemView.findViewById(R.id.llRunSessionAction);
                btnOpenSession = itemView.findViewById(R.id.btnOpenRunSession);
            }
        }
    }
}
