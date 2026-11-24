package com.molly.agent.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import com.molly.agent.R;
import com.molly.agent.model.ChatSessionItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SessionDrawerAdapter extends RecyclerView.Adapter<SessionDrawerAdapter.ViewHolder> {

    public interface OnSessionClickListener {
        void onSessionSelect(ChatSessionItem session);
        void onSessionArchive(ChatSessionItem session);
        void onSessionDelete(ChatSessionItem session);
    }

    private final Context context;
    private final List<ChatSessionItem> sessions;
    private final String currentSessionId;
    private final OnSessionClickListener listener;
    private final SimpleDateFormat dateFormat;

    public SessionDrawerAdapter(Context context, List<ChatSessionItem> sessions, String currentSessionId, OnSessionClickListener listener) {
        this.context = context;
        this.sessions = sessions;
        this.currentSessionId = currentSessionId;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session_drawer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatSessionItem item = sessions.get(position);
        holder.tvTitle.setText(item.getTitle());
        holder.tvDate.setText(dateFormat.format(new Date(item.getUpdatedAt())));

        boolean isCurrent = item.getId().equals(currentSessionId);
        if (isCurrent) {
            holder.itemView.setBackgroundResource(R.drawable.bg_action_pill);
            holder.itemView.setBackgroundTintList(context.getColorStateList(R.color.user_bubble_bg));
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_action_pill);
            holder.itemView.setBackgroundTintList(context.getColorStateList(R.color.card_dark));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSessionSelect(item);
        });

        holder.btnOptions.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.btnOptions);
            if (item.isArchived()) {
                popup.getMenu().add(0, 1, 0, "Batalkan Arsip");
            } else {
                popup.getMenu().add(0, 1, 0, "Arsipkan Sesi");
            }
            popup.getMenu().add(0, 2, 1, "Hapus Sesi");

            popup.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == 1) {
                    if (listener != null) listener.onSessionArchive(item);
                    return true;
                } else if (menuItem.getItemId() == 2) {
                    if (listener != null) listener.onSessionDelete(item);
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvDate;
        ImageView btnOptions;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSessionTitle);
            tvDate = itemView.findViewById(R.id.tvSessionDate);
            btnOptions = itemView.findViewById(R.id.btnSessionOptions);
        }
    }
}
