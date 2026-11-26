package com.molly.agent.ui;

import android.app.Activity;
import android.app.Dialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.molly.agent.R;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.ArtifactItem;
import com.molly.agent.model.WorkspaceFileItem;

import java.util.ArrayList;
import java.util.List;

public class SaveArtifactDialogHelper {

    public interface OnSavedCallback {
        void onSaved(String savedPath);
    }

    private static class FolderEntry {
        final String name;
        final String path;
        final boolean isParent;

        FolderEntry(String name, String path, boolean isParent) {
            this.name = name;
            this.path = path;
            this.isParent = isParent;
        }
    }

    public static void show(Activity activity, MollyApiClient apiClient, ArtifactItem item, OnSavedCallback callback) {
        if (activity == null || activity.isFinishing() || apiClient == null || item == null) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_save_artifact, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvSubtitle = view.findViewById(R.id.tvSaveArtifactSubtitle);
        TextView tvBreadcrumb = view.findViewById(R.id.tvSaveArtifactBreadcrumb);
        ImageView btnClose = view.findViewById(R.id.btnCloseSaveArtifact);
        LinearLayout btnNewFolder = view.findViewById(R.id.btnSaveArtifactNewFolder);
        ImageView btnRefresh = view.findViewById(R.id.btnSaveArtifactRefresh);

        LinearLayout llCreateFolderRow = view.findViewById(R.id.llCreateFolderRow);
        EditText etNewFolderName = view.findViewById(R.id.etNewFolderName);
        TextView btnConfirmCreateFolder = view.findViewById(R.id.btnConfirmCreateFolder);
        ImageView btnCancelCreateFolder = view.findViewById(R.id.btnCancelCreateFolder);

        ProgressBar pbLoading = view.findViewById(R.id.pbLoadingFolders);
        RecyclerView rvFolderList = view.findViewById(R.id.rvFolderList);
        TextView tvEmptyFolders = view.findViewById(R.id.tvEmptyFolders);

        TextView btnCancel = view.findViewById(R.id.btnCancelSaveArtifact);
        TextView btnSave = view.findViewById(R.id.btnConfirmSaveArtifact);

        tvSubtitle.setText("Simpan \"" + item.getName() + "\" ke direktori ruang kerja pribadi");

        final String[] currentPath = {"/"};
        final List<FolderEntry> folderEntries = new ArrayList<>();

        rvFolderList.setLayoutManager(new LinearLayoutManager(activity));
        FolderAdapter adapter = new FolderAdapter(folderEntries);
        rvFolderList.setAdapter(adapter);

        Runnable updateBreadcrumb = () -> {
            String p = currentPath[0];
            if (p.equals("/")) {
                tvBreadcrumb.setText("Workspace");
            } else {
                tvBreadcrumb.setText("Workspace " + p.replace("/", " / "));
            }
        };

        Runnable[] loadFoldersHolder = new Runnable[1];
        loadFoldersHolder[0] = () -> {
            pbLoading.setVisibility(View.VISIBLE);
            tvEmptyFolders.setVisibility(View.GONE);
            updateBreadcrumb.run();

            apiClient.fetchWorkspaceFiles(currentPath[0], "", new MollyApiClient.ApiCallback<List<WorkspaceFileItem>>() {
                @Override
                public void onSuccess(List<WorkspaceFileItem> result) {
                    activity.runOnUiThread(() -> {
                        pbLoading.setVisibility(View.GONE);
                        folderEntries.clear();

                        // Tambahkan navigasi induk ".." jika bukan di root
                        if (!currentPath[0].equals("/")) {
                            String parent = getParentPath(currentPath[0]);
                            folderEntries.add(new FolderEntry(".. (Kembali ke folder induk)", parent, true));
                        }

                        if (result != null) {
                            for (WorkspaceFileItem f : result) {
                                if (f.isDirectory) {
                                    folderEntries.add(new FolderEntry(f.name, f.path, false));
                                }
                            }
                        }

                        adapter.notifyDataSetChanged();
                        if (folderEntries.isEmpty()) {
                            tvEmptyFolders.setVisibility(View.VISIBLE);
                        } else {
                            tvEmptyFolders.setVisibility(View.GONE);
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    activity.runOnUiThread(() -> {
                        pbLoading.setVisibility(View.GONE);
                        Toast.makeText(activity, "Gagal memuat direktori: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        };

        adapter.setOnFolderClickListener(entry -> {
            currentPath[0] = entry.path;
            llCreateFolderRow.setVisibility(View.GONE);
            etNewFolderName.setText("");
            loadFoldersHolder[0].run();
        });

        btnNewFolder.setOnClickListener(v -> {
            boolean isVisible = llCreateFolderRow.getVisibility() == View.VISIBLE;
            llCreateFolderRow.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            if (!isVisible) {
                etNewFolderName.requestFocus();
            }
        });

        btnCancelCreateFolder.setOnClickListener(v -> {
            llCreateFolderRow.setVisibility(View.GONE);
            etNewFolderName.setText("");
        });

        btnConfirmCreateFolder.setOnClickListener(v -> {
            String folderName = etNewFolderName.getText().toString().trim();
            if (folderName.isEmpty()) {
                Toast.makeText(activity, "Nama folder tidak boleh kosong", Toast.LENGTH_SHORT).show();
                return;
            }

            btnConfirmCreateFolder.setEnabled(false);
            apiClient.createWorkspaceFolder(currentPath[0], folderName, new MollyApiClient.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    activity.runOnUiThread(() -> {
                        btnConfirmCreateFolder.setEnabled(true);
                        llCreateFolderRow.setVisibility(View.GONE);
                        etNewFolderName.setText("");
                        Toast.makeText(activity, "Folder \"" + folderName + "\" berhasil dibuat", Toast.LENGTH_SHORT).show();
                        // Buka langsung folder yang baru dibuat
                        String newPath = currentPath[0].equals("/") ? "/" + folderName : currentPath[0] + "/" + folderName;
                        currentPath[0] = newPath;
                        loadFoldersHolder[0].run();
                    });
                }

                @Override
                public void onError(String error) {
                    activity.runOnUiThread(() -> {
                        btnConfirmCreateFolder.setEnabled(true);
                        Toast.makeText(activity, "Gagal membuat folder: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        btnRefresh.setOnClickListener(v -> loadFoldersHolder[0].run());
        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            btnSave.setEnabled(false);
            btnSave.setText("Menyimpan...");

            final String targetDir = currentPath[0];
            apiClient.saveArtifactToWorkspace(item.getPath(), targetDir, new MollyApiClient.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    activity.runOnUiThread(() -> {
                        String fullDest = targetDir.equals("/") ? "/" + item.getName() : targetDir + "/" + item.getName();
                        Toast.makeText(activity, "Tersimpan ke ruang kerja pribadi: " + fullDest, Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                        if (callback != null) {
                            callback.onSaved(fullDest);
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    activity.runOnUiThread(() -> {
                        btnSave.setEnabled(true);
                        btnSave.setText("Save");
                        Toast.makeText(activity, "Gagal menyimpan: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        // Muat folder awal
        loadFoldersHolder[0].run();
        dialog.show();
    }

    private static String getParentPath(String path) {
        if (path == null || path.equals("/") || path.isEmpty()) return "/";
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) return "/";
        return path.substring(0, lastSlash);
    }

    private static class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.ViewHolder> {
        private final List<FolderEntry> entries;
        private OnFolderClickListener listener;

        interface OnFolderClickListener {
            void onFolderClick(FolderEntry entry);
        }

        FolderAdapter(List<FolderEntry> entries) {
            this.entries = entries;
        }

        void setOnFolderClickListener(OnFolderClickListener listener) {
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder_picker, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FolderEntry entry = entries.get(position);
            holder.tvName.setText(entry.name);
            if (entry.isParent) {
                holder.ivIcon.setImageResource(R.drawable.ic_arrow_back);
                holder.ivIcon.setColorFilter(android.graphics.Color.parseColor("#38BDF8"));
                holder.ivArrow.setVisibility(View.GONE);
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_folder);
                holder.ivIcon.setColorFilter(android.graphics.Color.parseColor("#F59E0B"));
                holder.ivArrow.setVisibility(View.VISIBLE);
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFolderClick(entry);
                }
            });
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivIcon;
            final TextView tvName;
            final ImageView ivArrow;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.ivFolderIcon);
                tvName = itemView.findViewById(R.id.tvFolderName);
                ivArrow = itemView.findViewById(R.id.ivFolderArrow);
            }
        }
    }
}
