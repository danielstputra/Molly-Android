package com.molly.agent.update;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.molly.agent.R;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.AppUpdateInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AppUpdateManager {

    private static boolean isChecking = false;

    public static int getCurrentVersionCode(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) pInfo.getLongVersionCode();
            } else {
                return pInfo.versionCode;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return 1;
        }
    }

    public static String getCurrentVersionName(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0.0";
        }
    }

    public static void checkForUpdate(Activity activity, MollyApiClient apiClient, boolean isManualCheck) {
        if (!apiClient.isConfigured() || isChecking) return;
        isChecking = true;

        int currentCode = getCurrentVersionCode(activity);
        String endpoint = apiClient.getServerUrl() + "/api/system/mobile/check-update?version_code=" + currentCode;

        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .get()
                .build();

        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                isChecking = false;
                if (isManualCheck) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, "Gagal memeriksa pembaruan: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws java.io.IOException {
                isChecking = false;
                if (!response.isSuccessful()) {
                    if (isManualCheck) {
                        activity.runOnUiThread(() -> Toast.makeText(activity, "Server tidak merespon: HTTP " + response.code(), Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                String bodyStr = response.body() != null ? response.body().string() : "{}";
                activity.runOnUiThread(() -> {
                    try {
                        JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                        boolean hasUpdate = obj.has("has_update") && obj.get("has_update").getAsBoolean();
                        int latestCode = obj.has("latest_version_code") ? obj.get("latest_version_code").getAsInt() : currentCode;
                        String latestName = obj.has("latest_version_name") ? obj.get("latest_version_name").getAsString() : "";
                        String changelog = obj.has("changelog") ? obj.get("changelog").getAsString() : "";
                        String downloadPath = obj.has("download_url") ? obj.get("download_url").getAsString() : "/api/system/download-apk";
                        long size = obj.has("file_size") ? obj.get("file_size").getAsLong() : 0;
                        boolean force = obj.has("force_update") && obj.get("force_update").getAsBoolean();

                        AppUpdateInfo info = new AppUpdateInfo(hasUpdate, latestCode, latestName, downloadPath, changelog, force, size);

                        if (info.hasUpdate) {
                            showUpdateDialog(activity, apiClient, info);
                        } else if (isManualCheck) {
                            Toast.makeText(activity, "Aplikasi sudah dalam versi terbaru (" + getCurrentVersionName(activity) + ")", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception ex) {
                        if (isManualCheck) {
                            Toast.makeText(activity, "Gagal memproses data update: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    private static void showUpdateDialog(Activity activity, MollyApiClient apiClient, AppUpdateInfo info) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_app_update, null);

        TextView tvUpdateVersion = dialogView.findViewById(R.id.tvUpdateVersion);
        TextView tvUpdateChangelog = dialogView.findViewById(R.id.tvUpdateChangelog);
        TextView tvViewFullChangelog = dialogView.findViewById(R.id.tvViewFullChangelog);
        View llDownloadProgress = dialogView.findViewById(R.id.llDownloadProgress);
        TextView tvDownloadStatus = dialogView.findViewById(R.id.tvDownloadStatus);
        TextView tvDownloadPercent = dialogView.findViewById(R.id.tvDownloadPercent);
        ProgressBar pbUpdateDownload = dialogView.findViewById(R.id.pbUpdateDownload);

        View llUpdateActions = dialogView.findViewById(R.id.llUpdateActions);
        View btnUpdateLater = dialogView.findViewById(R.id.btnUpdateLater);
        View btnUpdateNow = dialogView.findViewById(R.id.btnUpdateNow);

        tvUpdateVersion.setText("Versi Terbaru: " + info.latestVersionName + " (Versi saat ini: " + getCurrentVersionName(activity) + ")");
        tvUpdateChangelog.setText(info.changelog);

        if (tvViewFullChangelog != null) {
            tvViewFullChangelog.setOnClickListener(v -> {
                com.molly.agent.ui.ChangelogDialogHelper.show(activity, apiClient);
            });
        }

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);
        dialog.setCancelable(!info.forceUpdate);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        if (info.forceUpdate) {
            btnUpdateLater.setVisibility(View.GONE);
        } else {
            btnUpdateLater.setOnClickListener(v -> dialog.dismiss());
        }

        btnUpdateNow.setOnClickListener(v -> {
            llUpdateActions.setVisibility(View.GONE);
            llDownloadProgress.setVisibility(View.VISIBLE);

            String fullDownloadUrl = apiClient.getServerUrl() + (info.downloadUrl.startsWith("/") ? info.downloadUrl : "/" + info.downloadUrl);
            downloadAndInstallApk(activity, fullDownloadUrl, new DownloadListener() {
                @Override
                public void onProgress(int progress, String status) {
                    activity.runOnUiThread(() -> {
                        pbUpdateDownload.setProgress(progress);
                        tvDownloadPercent.setText(progress + "%");
                        tvDownloadStatus.setText(status);
                    });
                }

                @Override
                public void onComplete(File apkFile) {
                    activity.runOnUiThread(() -> {
                        dialog.dismiss();
                        triggerInstall(activity, apkFile);
                    });
                }

                @Override
                public void onError(String error) {
                    activity.runOnUiThread(() -> {
                        llUpdateActions.setVisibility(View.VISIBLE);
                        llDownloadProgress.setVisibility(View.GONE);
                        Toast.makeText(activity, "Gagal mengunduh pembaruan: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        dialog.show();
    }

    public interface DownloadListener {
        void onProgress(int progress, String status);
        void onComplete(File apkFile);
        void onError(String error);
    }

    private static void downloadAndInstallApk(Activity activity, String downloadUrl, DownloadListener listener) {
        File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = activity.getCacheDir();
        File apkFile = new File(dir, "molly-agent-latest.apk");
        if (apkFile.exists()) {
            try {
                apkFile.delete();
            } catch (Exception ignored) {}
        }

        Request request = new Request.Builder()
                .url(downloadUrl)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .get()
                .build();

        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                listener.onError(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws java.io.IOException {
                if (!response.isSuccessful()) {
                    listener.onError("HTTP " + response.code());
                    return;
                }

                long totalBytes = response.body() != null ? response.body().contentLength() : -1;
                try (InputStream is = response.body().byteStream();
                     OutputStream os = new FileOutputStream(apkFile)) {

                    byte[] buffer = new byte[8192];
                    long downloadedBytes = 0;
                    int read;

                    while ((read = is.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                        downloadedBytes += read;
                        if (totalBytes > 0) {
                            int progress = (int) ((downloadedBytes * 100) / totalBytes);
                            String mbStatus = String.format("%.1f MB / %.1f MB", downloadedBytes / (1024.0 * 1024.0), totalBytes / (1024.0 * 1024.0));
                            listener.onProgress(progress, mbStatus);
                        } else {
                            listener.onProgress(50, String.format("%.1f MB terunduh", downloadedBytes / (1024.0 * 1024.0)));
                        }
                    }
                    os.flush();
                    listener.onComplete(apkFile);
                } catch (Exception ex) {
                    listener.onError("Gagal simpan APK: " + ex.getMessage());
                }
            }
        });
    }

    public static void triggerInstall(Activity activity, File apkFile) {
        if (!apkFile.exists()) {
            Toast.makeText(activity, "Berkas instalasi tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }

        // Cek izin instalasi unknown apps untuk Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(activity, "Izinkan penginstalan aplikasi untuk memasang pembaruan", Toast.LENGTH_LONG).show();
                Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(permissionIntent);
                return;
            }
        }

        try {
            Uri apkUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    apkFile
            );

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            activity.startActivity(installIntent);
        } catch (Exception e) {
            Toast.makeText(activity, "Gagal meluncurkan installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
