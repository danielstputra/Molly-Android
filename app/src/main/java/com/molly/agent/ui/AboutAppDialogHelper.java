package com.molly.agent.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import com.molly.agent.R;

public class AboutAppDialogHelper {

    public static void show(Context context) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_about_app, null);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        Button btnClose = dialogView.findViewById(R.id.btnAboutClose);
        Button btnGitHub = dialogView.findViewById(R.id.btnAboutGitHub);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
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
