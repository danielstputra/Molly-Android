package com.molly.agent.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.molly.agent.R;
import com.molly.agent.model.ChatDisplayItem;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnToolApprovalListener {
        void onApprove(ChatDisplayItem item);
        void onReject(ChatDisplayItem item);
    }

    private final List<ChatDisplayItem> items = new ArrayList<>();
    private OnToolApprovalListener approvalListener;

    public void setApprovalListener(OnToolApprovalListener listener) {
        this.approvalListener = listener;
    }

    public void setItems(List<ChatDisplayItem> newItems) {
        this.items.clear();
        this.items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void addItem(ChatDisplayItem item) {
        this.items.add(item);
        notifyItemInserted(this.items.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getItemType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == ChatDisplayItem.TYPE_TOOL_CALL) {
            View view = inflater.inflate(R.layout.item_chat_tool_call, parent, false);
            return new ToolCallViewHolder(view);
        } else if (viewType == ChatDisplayItem.TYPE_THINKING) {
            View view = inflater.inflate(R.layout.item_chat_thinking, parent, false);
            return new ThinkingViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_chat_message, parent, false);
            return new MessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatDisplayItem item = items.get(position);

        if (holder instanceof MessageViewHolder) {
            ((MessageViewHolder) holder).bind(item);
        } else if (holder instanceof ToolCallViewHolder) {
            ((ToolCallViewHolder) holder).bind(item, approvalListener);
        } else if (holder instanceof ThinkingViewHolder) {
            ((ThinkingViewHolder) holder).bind(item);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public ChatDisplayItem getLastItem() {
        if (items.isEmpty()) return null;
        return items.get(items.size() - 1);
    }

    // ==========================================
    // View Holder: Regular User / Agent Message
    // ==========================================
    static class MessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout llUserContainer;
        TextView tvUserMessage;
        TextView tvUserTimestamp;

        LinearLayout llAgentContainer;
        TextView tvAgentMessage;
        TextView tvAgentTimestamp;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            llUserContainer = itemView.findViewById(R.id.llUserContainer);
            tvUserMessage = itemView.findViewById(R.id.tvUserMessage);
            tvUserTimestamp = itemView.findViewById(R.id.tvUserTimestamp);

            llAgentContainer = itemView.findViewById(R.id.llAgentContainer);
            tvAgentMessage = itemView.findViewById(R.id.tvAgentMessage);
            tvAgentTimestamp = itemView.findViewById(R.id.tvAgentTimestamp);
        }

        public void bind(ChatDisplayItem item) {
            boolean isUser = (item.getItemType() == ChatDisplayItem.TYPE_USER);

            if (isUser) {
                llUserContainer.setVisibility(View.VISIBLE);
                llAgentContainer.setVisibility(View.GONE);
                tvUserMessage.setText(item.getText());
                tvUserTimestamp.setText(item.getTimestamp());
            } else {
                llUserContainer.setVisibility(View.GONE);
                llAgentContainer.setVisibility(View.VISIBLE);
                tvAgentMessage.setText(item.getText());
                tvAgentTimestamp.setText(item.getTimestamp());
            }
        }
    }

    // ==========================================
    // View Holder: Tool Machine Call (BaseToolCall)
    // ==========================================
    static class ToolCallViewHolder extends RecyclerView.ViewHolder {
        View llToolHeader;
        ImageView ivToolStatusIcon;
        ProgressBar pbToolLoading;
        TextView tvToolHeaderTitle;
        TextView tvToolStatusSummary;
        TextView tvToolBadge;
        ImageView ivToolChevron;

        View llToolBody;
        View llCommandContainer;
        TextView tvToolCommand;
        View llOutputContainer;
        TextView tvToolOutput;

        View llApprovalContainer;
        TextView btnApproveTool;
        TextView btnRejectTool;

        public ToolCallViewHolder(@NonNull View itemView) {
            super(itemView);
            llToolHeader = itemView.findViewById(R.id.llToolHeader);
            ivToolStatusIcon = itemView.findViewById(R.id.ivToolStatusIcon);
            pbToolLoading = itemView.findViewById(R.id.pbToolLoading);
            tvToolHeaderTitle = itemView.findViewById(R.id.tvToolHeaderTitle);
            tvToolStatusSummary = itemView.findViewById(R.id.tvToolStatusSummary);
            tvToolBadge = itemView.findViewById(R.id.tvToolBadge);
            ivToolChevron = itemView.findViewById(R.id.ivToolChevron);

            llToolBody = itemView.findViewById(R.id.llToolBody);
            llCommandContainer = itemView.findViewById(R.id.llCommandContainer);
            tvToolCommand = itemView.findViewById(R.id.tvToolCommand);
            llOutputContainer = itemView.findViewById(R.id.llOutputContainer);
            tvToolOutput = itemView.findViewById(R.id.tvToolOutput);

            llApprovalContainer = itemView.findViewById(R.id.llApprovalContainer);
            btnApproveTool = itemView.findViewById(R.id.btnApproveTool);
            btnRejectTool = itemView.findViewById(R.id.btnRejectTool);
        }

        public void bind(ChatDisplayItem item, OnToolApprovalListener listener) {
            Context ctx = itemView.getContext();
            tvToolHeaderTitle.setText(item.getToolName());

            String status = item.getToolStatus() != null ? item.getToolStatus().toLowerCase() : "completed";

            // Atur status icon, badge, dan loading
            if ("in_progress".equals(status) || "running".equals(status) || "calling".equals(status)) {
                pbToolLoading.setVisibility(View.VISIBLE);
                ivToolStatusIcon.setVisibility(View.GONE);
                tvToolBadge.setText("MEMPROSES");
                tvToolBadge.setBackgroundTintList(ColorStateList.valueOf(0x33F3BA32));
                tvToolBadge.setTextColor(ctx.getColor(R.color.primary_gold));
                tvToolStatusSummary.setText("Mengeksekusi alat mesin secara realtime...");
                tvToolStatusSummary.setTextColor(ctx.getColor(R.color.primary_gold));
                llApprovalContainer.setVisibility(View.GONE);
            } else if ("waiting".equals(status)) {
                pbToolLoading.setVisibility(View.GONE);
                ivToolStatusIcon.setVisibility(View.VISIBLE);
                ivToolStatusIcon.setImageResource(R.drawable.ic_tool_waiting);
                tvToolBadge.setText("IZIN DIPERLUKAN");
                tvToolBadge.setBackgroundTintList(ColorStateList.valueOf(0x33F59E0B));
                tvToolBadge.setTextColor(0xFFF59E0B);
                tvToolStatusSummary.setText("Menunggu konfirmasi persetujuan pengguna");
                tvToolStatusSummary.setTextColor(0xFFF59E0B);
                llApprovalContainer.setVisibility(View.VISIBLE);
            } else if ("error".equals(status) || "failed".equals(status)) {
                pbToolLoading.setVisibility(View.GONE);
                ivToolStatusIcon.setVisibility(View.VISIBLE);
                ivToolStatusIcon.setImageResource(R.drawable.ic_tool_error);
                tvToolBadge.setText("GAGAL");
                tvToolBadge.setBackgroundTintList(ColorStateList.valueOf(0x33EF4444));
                tvToolBadge.setTextColor(ctx.getColor(R.color.status_red));
                tvToolStatusSummary.setText("Eksekusi alat menghasilkan kesalahan");
                tvToolStatusSummary.setTextColor(ctx.getColor(R.color.status_red));
                llApprovalContainer.setVisibility(View.GONE);
            } else {
                // Completed / Success
                pbToolLoading.setVisibility(View.GONE);
                ivToolStatusIcon.setVisibility(View.VISIBLE);
                ivToolStatusIcon.setImageResource(R.drawable.ic_tool_check);
                tvToolBadge.setText("SELESAI");
                tvToolBadge.setBackgroundTintList(ColorStateList.valueOf(0x3310B981));
                tvToolBadge.setTextColor(ctx.getColor(R.color.status_green));
                tvToolStatusSummary.setText("Eksekusi alat berhasil diselesaikan");
                tvToolStatusSummary.setTextColor(ctx.getColor(R.color.status_green));
                llApprovalContainer.setVisibility(View.GONE);
            }

            // Command panel
            if (item.getToolArgs() != null && !item.getToolArgs().trim().isEmpty()) {
                llCommandContainer.setVisibility(View.VISIBLE);
                tvToolCommand.setText(item.getToolArgs().trim());
            } else {
                llCommandContainer.setVisibility(View.GONE);
            }

            // Output panel
            if (item.getToolOutput() != null && !item.getToolOutput().trim().isEmpty()) {
                llOutputContainer.setVisibility(View.VISIBLE);
                tvToolOutput.setText(item.getToolOutput().trim());
            } else {
                llOutputContainer.setVisibility(View.GONE);
            }

            // Accordion expand/collapse
            boolean expanded = item.isExpanded();
            llToolBody.setVisibility(expanded ? View.VISIBLE : View.GONE);
            ivToolChevron.setImageResource(expanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);

            llToolHeader.setOnClickListener(v -> {
                boolean nextState = !item.isExpanded();
                item.setExpanded(nextState);
                llToolBody.setVisibility(nextState ? View.VISIBLE : View.GONE);
                ivToolChevron.setImageResource(nextState ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);
            });

            // Action Approval
            btnApproveTool.setOnClickListener(v -> {
                if (listener != null) listener.onApprove(item);
            });

            btnRejectTool.setOnClickListener(v -> {
                if (listener != null) listener.onReject(item);
            });
        }
    }

    // ==========================================
    // View Holder: Thinking / Reasoning Box (CoT)
    // ==========================================
    static class ThinkingViewHolder extends RecyclerView.ViewHolder {
        View llThinkingHeader;
        ImageView ivThinkingChevron;
        TextView tvThinkingContent;

        public ThinkingViewHolder(@NonNull View itemView) {
            super(itemView);
            llThinkingHeader = itemView.findViewById(R.id.llThinkingHeader);
            ivThinkingChevron = itemView.findViewById(R.id.ivThinkingChevron);
            tvThinkingContent = itemView.findViewById(R.id.tvThinkingContent);
        }

        public void bind(ChatDisplayItem item) {
            tvThinkingContent.setText(item.getText());

            boolean expanded = item.isExpanded();
            tvThinkingContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
            ivThinkingChevron.setImageResource(expanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);

            llThinkingHeader.setOnClickListener(v -> {
                boolean next = !item.isExpanded();
                item.setExpanded(next);
                tvThinkingContent.setVisibility(next ? View.VISIBLE : View.GONE);
                ivThinkingChevron.setImageResource(next ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);
            });
        }
    }
}
