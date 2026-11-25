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
        if (viewType == ChatDisplayItem.TYPE_TOOL_GROUP) {
            View view = inflater.inflate(R.layout.item_chat_tool_group, parent, false);
            return new ToolGroupViewHolder(view);
        } else if (viewType == ChatDisplayItem.TYPE_TOOL_CALL) {
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
        } else if (holder instanceof ToolGroupViewHolder) {
            ((ToolGroupViewHolder) holder).bind(item, approvalListener);
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

        ImageView btnThumbUp;
        ImageView btnThumbDown;
        ImageView btnCopyMessage;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            llUserContainer = itemView.findViewById(R.id.llUserContainer);
            tvUserMessage = itemView.findViewById(R.id.tvUserMessage);
            tvUserTimestamp = itemView.findViewById(R.id.tvUserTimestamp);

            llAgentContainer = itemView.findViewById(R.id.llAgentContainer);
            tvAgentMessage = itemView.findViewById(R.id.tvAgentMessage);
            tvAgentTimestamp = itemView.findViewById(R.id.tvAgentTimestamp);

            btnThumbUp = itemView.findViewById(R.id.btnThumbUp);
            btnThumbDown = itemView.findViewById(R.id.btnThumbDown);
            btnCopyMessage = itemView.findViewById(R.id.btnCopyMessage);
        }

        public void bind(ChatDisplayItem item) {
            Context ctx = itemView.getContext();
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

                String time = item.getTimestamp() != null && !item.getTimestamp().isEmpty() ? item.getTimestamp() : "12:00";
                tvAgentTimestamp.setText(time);

                // Update warna status feedback (Like / Dislike)
                updateFeedbackUi(item.getFeedback());

                // Klik Thumbs Up
                if (btnThumbUp != null) {
                    btnThumbUp.setOnClickListener(v -> {
                        if (item.getFeedback() == 1) {
                            item.setFeedback(0);
                        } else {
                            item.setFeedback(1);
                            android.widget.Toast.makeText(ctx, "Terima kasih atas tanggapan Anda!", android.widget.Toast.LENGTH_SHORT).show();
                        }
                        updateFeedbackUi(item.getFeedback());
                    });
                }

                // Klik Thumbs Down
                if (btnThumbDown != null) {
                    btnThumbDown.setOnClickListener(v -> {
                        if (item.getFeedback() == -1) {
                            item.setFeedback(0);
                        } else {
                            item.setFeedback(-1);
                            android.widget.Toast.makeText(ctx, "Tanggapan dicatat untuk peningkatan sistem.", android.widget.Toast.LENGTH_SHORT).show();
                        }
                        updateFeedbackUi(item.getFeedback());
                    });
                }

                // Klik Copy / Salin
                if (btnCopyMessage != null) {
                    btnCopyMessage.setOnClickListener(v -> {
                        String textToCopy = item.getText() != null ? item.getText() : "";
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("Molly Agent Response", textToCopy);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                            android.widget.Toast.makeText(ctx, "Pesan berhasil disalin ke papan klip", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }

        private void updateFeedbackUi(int feedback) {
            if (btnThumbUp != null) {
                if (feedback == 1) {
                    btnThumbUp.setImageTintList(ColorStateList.valueOf(0xFF10B981)); // Green active
                } else {
                    btnThumbUp.setImageTintList(ColorStateList.valueOf(0xFF9CA3AF)); // Gray default
                }
            }
            if (btnThumbDown != null) {
                if (feedback == -1) {
                    btnThumbDown.setImageTintList(ColorStateList.valueOf(0xFFEF4444)); // Red active
                } else {
                    btnThumbDown.setImageTintList(ColorStateList.valueOf(0xFF9CA3AF)); // Gray default
                }
            }
        }
    }

    // ==========================================
    // View Holder: Grouped Tool Calls (Accordion Tree - Gambar 3)
    // ==========================================
    static class ToolGroupViewHolder extends RecyclerView.ViewHolder {
        View llGroupHeader;
        TextView tvGroupIcon;
        TextView tvGroupTitle;
        ImageView ivGroupChevron;
        View llGroupBody;
        LinearLayout llSubToolsContainer;

        public ToolGroupViewHolder(@NonNull View itemView) {
            super(itemView);
            llGroupHeader = itemView.findViewById(R.id.llGroupHeader);
            tvGroupIcon = itemView.findViewById(R.id.tvGroupIcon);
            tvGroupTitle = itemView.findViewById(R.id.tvGroupTitle);
            ivGroupChevron = itemView.findViewById(R.id.ivGroupChevron);
            llGroupBody = itemView.findViewById(R.id.llGroupBody);
            llSubToolsContainer = itemView.findViewById(R.id.llSubToolsContainer);
        }

        public void bind(ChatDisplayItem item, OnToolApprovalListener listener) {
            Context ctx = itemView.getContext();
            tvGroupTitle.setText(item.getGroupSummaryTitle());

            // Expand / Collapse State
            boolean expanded = item.isExpanded();
            llGroupBody.setVisibility(expanded ? View.VISIBLE : View.GONE);
            ivGroupChevron.setImageResource(expanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);

            llGroupHeader.setOnClickListener(v -> {
                boolean nextState = !item.isExpanded();
                item.setExpanded(nextState);
                llGroupBody.setVisibility(nextState ? View.VISIBLE : View.GONE);
                ivGroupChevron.setImageResource(nextState ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);
            });

            // Dynamically populate sub-tools
            llSubToolsContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(ctx);

            for (ChatDisplayItem.SubToolCall sub : item.getSubTools()) {
                View subView = inflater.inflate(R.layout.item_sub_tool_call, llSubToolsContainer, false);
                TextView tvIcon = subView.findViewById(R.id.tvSubToolIcon);
                TextView tvName = subView.findViewById(R.id.tvSubToolName);
                TextView tvArgs = subView.findViewById(R.id.tvSubToolArgs);
                TextView tvBadge = subView.findViewById(R.id.tvSubToolBadge);
                TextView tvOutput = subView.findViewById(R.id.tvSubToolOutputSummary);
                View llApproval = subView.findViewById(R.id.llSubToolApproval);
                TextView btnApprove = subView.findViewById(R.id.btnSubToolApprove);
                TextView btnReject = subView.findViewById(R.id.btnSubToolReject);

                tvIcon.setText(sub.getToolIcon());
                tvName.setText(sub.toolName);
                tvArgs.setText(sub.getSummaryArgs());

                String summaryOut = sub.getSummaryOutput();
                if ("completed".equalsIgnoreCase(sub.status)) {
                    if ("No matches found".equalsIgnoreCase(summaryOut)) {
                        tvBadge.setText("✕");
                        tvBadge.setTextColor(0xFF9CA3AF);
                    } else if (summaryOut.contains("item") || summaryOut.length() < 15) {
                        tvBadge.setText("1item");
                        tvBadge.setTextColor(0xFF9CA3AF);
                    } else {
                        tvBadge.setText("✓");
                        tvBadge.setTextColor(ctx.getColor(R.color.status_green));
                    }
                } else if ("in_progress".equalsIgnoreCase(sub.status)) {
                    tvBadge.setText("...");
                    tvBadge.setTextColor(ctx.getColor(R.color.primary_gold));
                } else if ("waiting".equalsIgnoreCase(sub.status)) {
                    tvBadge.setText("Menunggu izin");
                    tvBadge.setTextColor(0xFFF59E0B);
                } else {
                    tvBadge.setText("✕");
                    tvBadge.setTextColor(ctx.getColor(R.color.status_red));
                }

                if (!summaryOut.isEmpty()) {
                    tvOutput.setVisibility(View.VISIBLE);
                    tvOutput.setText(summaryOut);
                } else {
                    tvOutput.setVisibility(View.GONE);
                }

                if ("waiting".equalsIgnoreCase(sub.status)) {
                    llApproval.setVisibility(View.VISIBLE);
                    btnApprove.setOnClickListener(v -> {
                        if (listener != null) listener.onApprove(item);
                    });
                    btnReject.setOnClickListener(v -> {
                        if (listener != null) listener.onReject(item);
                    });
                } else {
                    llApproval.setVisibility(View.GONE);
                }

                llSubToolsContainer.addView(subView);
            }
        }
    }

    // ==========================================
    // View Holder: Tool Machine Call (Fallback)
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

            if (item.getToolArgs() != null && !item.getToolArgs().trim().isEmpty()) {
                llCommandContainer.setVisibility(View.VISIBLE);
                tvToolCommand.setText(item.getToolArgs().trim());
            } else {
                llCommandContainer.setVisibility(View.GONE);
            }

            if (item.getToolOutput() != null && !item.getToolOutput().trim().isEmpty()) {
                llOutputContainer.setVisibility(View.VISIBLE);
                tvToolOutput.setText(item.getToolOutput().trim());
            } else {
                llOutputContainer.setVisibility(View.GONE);
            }

            boolean expanded = item.isExpanded();
            llToolBody.setVisibility(expanded ? View.VISIBLE : View.GONE);
            ivToolChevron.setImageResource(expanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);

            llToolHeader.setOnClickListener(v -> {
                boolean nextState = !item.isExpanded();
                item.setExpanded(nextState);
                llToolBody.setVisibility(nextState ? View.VISIBLE : View.GONE);
                ivToolChevron.setImageResource(nextState ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);
            });

            btnApproveTool.setOnClickListener(v -> {
                if (listener != null) listener.onApprove(item);
            });

            btnRejectTool.setOnClickListener(v -> {
                if (listener != null) listener.onReject(item);
            });
        }
    }

    // ==========================================
    // View Holder: Thinking / Reasoning Box (Gambar 3)
    // ==========================================
    static class ThinkingViewHolder extends RecyclerView.ViewHolder {
        View llThinkingHeader;
        TextView tvThinkingTitle;
        ImageView ivThinkingChevron;
        TextView tvThinkingContent;

        public ThinkingViewHolder(@NonNull View itemView) {
            super(itemView);
            llThinkingHeader = itemView.findViewById(R.id.llThinkingHeader);
            tvThinkingTitle = itemView.findViewById(R.id.tvThinkingTitle);
            ivThinkingChevron = itemView.findViewById(R.id.ivThinkingChevron);
            tvThinkingContent = itemView.findViewById(R.id.tvThinkingContent);
        }

        public void bind(ChatDisplayItem item) {
            String content = item.getText() != null ? item.getText().trim() : "";
            tvThinkingContent.setText(content);

            // Tentukan label durasi / status thinking seperti di Molly Web (Gambar 3)
            if (content.isEmpty()) {
                tvThinkingTitle.setText("Sedang berpikir...");
            } else {
                int words = content.split("\\s+").length;
                int estimatedSec = Math.max(3, Math.min(60, words / 8));
                tvThinkingTitle.setText("Memakan waktu " + estimatedSec + "s");
            }

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
