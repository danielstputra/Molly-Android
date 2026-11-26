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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.molly.agent.R;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.WorkspaceFileItem;

import java.util.ArrayList;
import java.util.List;

public class WorkspaceFilesDialogHelper {

    public interface OnFileSelectedListener {
        void onFileSelected(WorkspaceFileItem item);
    }

    public static void show(Context context, MollyApiClient apiClient, OnFileSelectedListener listener) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_workspace_files, null);

        ImageView btnClose = dialogView.findViewById(R.id.btnCloseWorkspaceDialog);
        EditText etSearch = dialogView.findViewById(R.id.etSearchWorkspaceFiles);
        ProgressBar pbLoading = dialogView.findViewById(R.id.pbLoadingWorkspaceFiles);
        RecyclerView rvFiles = dialogView.findViewById(R.id.rvWorkspaceFiles);
        TextView tvEmpty = dialogView.findViewById(R.id.tvEmptyWorkspaceFiles);

        rvFiles.setLayoutManager(new LinearLayoutManager(context));
        WorkspaceFileAdapter adapter = new WorkspaceFileAdapter();
        rvFiles.setAdapter(adapter);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        // State folder saat ini
        final String[] currentPath = {"/"};

        Runnable loadFiles = () -> {
            pbLoading.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);

            String query = etSearch.getText() != null ? etSearch.getText().toString().trim() : "";
            apiClient.fetchWorkspaceFiles(currentPath[0], query, new MollyApiClient.ApiCallback<List<WorkspaceFileItem>>() {
                @Override
                public void onSuccess(List<WorkspaceFileItem> result) {
                    pbLoading.setVisibility(View.GONE);
                    if (result == null || result.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        adapter.setItems(new ArrayList<>());
                    } else {
                        tvEmpty.setVisibility(View.GONE);
                        adapter.setItems(result);
                    }
                }

                @Override
                public void onError(String error) {
                    pbLoading.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("Gagal memuat berkas: " + error);
                }
            });
        };

        adapter.setOnItemClickListener(item -> {
            if (item.isDirectory) {
                currentPath[0] = item.path;
                etSearch.setText("");
                loadFiles.run();
            } else {
                if (listener != null) {
                    listener.onFileSelected(item);
                }
                dialog.dismiss();
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadFiles.run();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Load awal
        loadFiles.run();

        dialog.show();
    }

    // ==========================================
    // Adapter untuk RecyclerView Berkas Ruang Kerja
    // ==========================================
    private static class WorkspaceFileAdapter extends RecyclerView.Adapter<WorkspaceFileAdapter.ViewHolder> {
        private final List<WorkspaceFileItem> items = new ArrayList<>();
        private OnItemClickListener listener;

        public interface OnItemClickListener {
            void onItemClick(WorkspaceFileItem item);
        }

        public void setOnItemClickListener(OnItemClickListener listener) {
            this.listener = listener;
        }

        public void setItems(List<WorkspaceFileItem> newItems) {
            this.items.clear();
            if (newItems != null) {
                this.items.addAll(newItems);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_workspace_file_row, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WorkspaceFileItem item = items.get(position);
            holder.tvIcon.setText(item.getIcon());
            holder.tvName.setText(item.name);
            holder.tvPath.setText(item.path);
            holder.tvSize.setText(item.getFormattedSize());

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvIcon;
            TextView tvName;
            TextView tvPath;
            TextView tvSize;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvIcon = itemView.findViewById(R.id.tvFileIcon);
                tvName = itemView.findViewById(R.id.tvFileName);
                tvPath = itemView.findViewById(R.id.tvFilePath);
                tvSize = itemView.findViewById(R.id.tvFileSize);
            }
        }
    }
}
