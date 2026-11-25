package com.molly.agent.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.molly.agent.R;
import com.molly.agent.model.ScheduledTaskItem;

import java.util.ArrayList;
import java.util.List;

public class ScheduledTasksAdapter extends RecyclerView.Adapter<ScheduledTasksAdapter.TaskViewHolder> {

    public interface TaskActionListener {
        void onToggleEnabled(ScheduledTaskItem task, boolean enabled);
        void onRunNow(ScheduledTaskItem task);
        void onViewHistory(ScheduledTaskItem task);
        void onEdit(ScheduledTaskItem task);
        void onDelete(ScheduledTaskItem task);
    }

    private final Context context;
    private final List<ScheduledTaskItem> items = new ArrayList<>();
    private final TaskActionListener listener;

    public ScheduledTasksAdapter(Context context, TaskActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<ScheduledTaskItem> newItems) {
        this.items.clear();
        this.items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_scheduled_task, parent, false);
        return new TaskViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        ScheduledTaskItem task = items.get(position);

        holder.tvName.setText(task.name != null ? task.name : "Tugas Terjadwal");

        // Status Switch
        holder.swEnabled.setOnCheckedChangeListener(null);
        holder.swEnabled.setChecked(task.enabled);
        holder.swEnabled.setOnCheckedChangeListener((btn, isChecked) -> {
            if (listener != null) {
                listener.onToggleEnabled(task, isChecked);
            }
        });

        // Status Badge
        if (task.enabled) {
            holder.tvStatusBadge.setText("AKTIF");
            holder.tvStatusBadge.setTextColor(context.getColor(R.color.status_green));
        } else {
            holder.tvStatusBadge.setText("DIJEDA");
            holder.tvStatusBadge.setTextColor(context.getColor(R.color.text_muted));
        }

        // Agent Name
        String agentLabel = (task.agentName != null && !task.agentName.isEmpty()) ? task.agentName : (task.agentSlug != null ? task.agentSlug : "Asisten");
        holder.tvAgent.setText("🤖 " + agentLabel);

        // Schedule
        holder.tvSchedule.setText("⏰ " + task.getScheduleDescription());

        // Prompt
        if (task.prompt != null && !task.prompt.trim().isEmpty()) {
            holder.tvPrompt.setText(task.prompt.trim());
        } else {
            holder.tvPrompt.setText("(Tidak ada prompt khusus)");
        }

        // Action Buttons
        holder.btnRunNow.setOnClickListener(v -> {
            if (listener != null) listener.onRunNow(task);
        });

        holder.btnHistory.setOnClickListener(v -> {
            if (listener != null) listener.onViewHistory(task);
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(task);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(task);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        SwitchCompat swEnabled;
        TextView tvAgent;
        TextView tvSchedule;
        TextView tvStatusBadge;
        TextView tvPrompt;
        View btnRunNow;
        View btnHistory;
        ImageView btnEdit;
        ImageView btnDelete;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvTaskName);
            swEnabled = itemView.findViewById(R.id.swTaskEnabled);
            tvAgent = itemView.findViewById(R.id.tvTaskAgent);
            tvSchedule = itemView.findViewById(R.id.tvTaskSchedule);
            tvStatusBadge = itemView.findViewById(R.id.tvTaskStatusBadge);
            tvPrompt = itemView.findViewById(R.id.tvTaskPrompt);
            btnRunNow = itemView.findViewById(R.id.btnRunNow);
            btnHistory = itemView.findViewById(R.id.btnTaskHistory);
            btnEdit = itemView.findViewById(R.id.btnEditTask);
            btnDelete = itemView.findViewById(R.id.btnDeleteTask);
        }
    }
}
