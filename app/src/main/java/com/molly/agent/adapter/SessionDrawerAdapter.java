package com.molly.agent.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
        void onSessionTogglePin(ChatSessionItem session);
        void onSessionRename(ChatSessionItem session);
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

        if (holder.ivPinned != null) {
            holder.ivPinned.setVisibility(item.isPinned() ? View.VISIBLE : View.GONE);
        }

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

        // Long press opens context popup menu
        holder.itemView.setOnLongClickListener(v -> {
            showSessionOptionsMenu(holder.btnSessionOptions, item);
            return true;
        });

        // Click options button opens context popup menu
        holder.btnSessionOptions.setOnClickListener(v -> {
            showSessionOptionsMenu(holder.btnSessionOptions, item);
        });
    }

    private void showSessionOptionsMenu(View anchorView, ChatSessionItem item) {
        View popupView = LayoutInflater.from(context).inflate(R.layout.popup_session_options_menu, null);
        PopupWindow popup = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popup.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, context.getResources().getDisplayMetrics()));
        }

        // 1. Pin to top / Unpin from top
        TextView tvPin = popupView.findViewById(R.id.tvPopupPinText);
        if (item.isPinned()) {
            tvPin.setText("Unpin from top");
        } else {
            tvPin.setText("pin to top");
        }
        popupView.findViewById(R.id.llPopupPin).setOnClickListener(v -> {
            popup.dismiss();
            if (listener != null) listener.onSessionTogglePin(item);
        });

        // 2. Rename
        popupView.findViewById(R.id.llPopupRename).setOnClickListener(v -> {
            popup.dismiss();
            if (listener != null) listener.onSessionRename(item);
        });

        // 3. Archive
        TextView tvArchive = popupView.findViewById(R.id.tvPopupArchiveText);
        if (item.isArchived()) {
            tvArchive.setText("Batalkan Arsip");
        } else {
            tvArchive.setText("Archive");
        }
        popupView.findViewById(R.id.llPopupArchive).setOnClickListener(v -> {
            popup.dismiss();
            if (listener != null) listener.onSessionArchive(item);
        });

        // 4. Delete
        popupView.findViewById(R.id.llPopupDelete).setOnClickListener(v -> {
            popup.dismiss();
            if (listener != null) listener.onSessionDelete(item);
        });

        // Tampilkan dropdown sejajar tombol opsi ke arah kiri
        int xOffset = -(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 130, context.getResources().getDisplayMetrics());
        popup.showAsDropDown(anchorView, xOffset, 0);
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvDate;
        ImageView ivPinned;
        ImageView btnSessionOptions;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSessionTitle);
            tvDate = itemView.findViewById(R.id.tvSessionDate);
            ivPinned = itemView.findViewById(R.id.ivPinned);
            btnSessionOptions = itemView.findViewById(R.id.btnSessionOptions);
        }
    }
}
