package com.molly.agent.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import com.molly.agent.R;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.update.AppUpdateManager;

public class AboutAppDialogHelper {

    public static void show(Context context) {
        show(context, null);
    }

    public static void show(Context context, MollyApiClient apiClient) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_about_app, null);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvAboutVersion = dialogView.findViewById(R.id.tvAboutVersion);
        if (tvAboutVersion != null) {
            tvAboutVersion.setText("Mobile Companion Client • " + AppUpdateManager.getCurrentVersionName(context));
        }

        Button btnClose = dialogView.findViewById(R.id.btnAboutClose);
        Button btnGitHub = dialogView.findViewById(R.id.btnAboutGitHub);
        Button btnChangelog = dialogView.findViewById(R.id.btnAboutChangelog);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnChangelog != null) {
            if (context instanceof Activity && apiClient != null) {
                btnChangelog.setOnClickListener(v -> {
                    dialog.dismiss();
                    ChangelogDialogHelper.show((Activity) context, apiClient);
                });
            } else {
                btnChangelog.setVisibility(View.GONE);
            }
        }

        if (btnGitHub != null) {
            btnGitHub.setOnClickListener(v -> {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/danielstputra/Molly-Android"));
                    context.startActivity(browserIntent);
                } catch (Exception ignored) {
                }
            });
        }

        dialog.show();
    }
}
