package com.molly.agent.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.molly.agent.R;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.ArtifactItem;

public class ArtifactPreviewDialogHelper {

    public interface OnArtifactActionListener {
        void onDownload(ArtifactItem item);
        void onSave(ArtifactItem item);
    }

    public static void show(Activity activity, MollyApiClient apiClient, ArtifactItem item, OnArtifactActionListener listener) {
        if (activity == null || activity.isFinishing()) return;

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_artifact_preview, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#0F172A")));
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        }

        TextView tvPreviewPathTitle = view.findViewById(R.id.tvPreviewPathTitle);
        ImageView btnModeRender = view.findViewById(R.id.btnModeRender);
        ImageView btnModeSource = view.findViewById(R.id.btnModeSource);
        ImageView btnToggleDesktopMode = view.findViewById(R.id.btnToggleDesktopMode);
        LinearLayout llZoomControls = view.findViewById(R.id.llZoomControls);
        ImageView btnZoomOut = view.findViewById(R.id.btnZoomOut);
        ImageView btnZoomIn = view.findViewById(R.id.btnZoomIn);
        TextView tvZoomPercent = view.findViewById(R.id.tvZoomPercent);
        ImageView btnPreviewDownload = view.findViewById(R.id.btnPreviewDownload);
        ImageView btnPreviewSave = view.findViewById(R.id.btnPreviewSave);
        ImageView btnPreviewClose = view.findViewById(R.id.btnPreviewClose);
        ProgressBar pbPreviewLoading = view.findViewById(R.id.pbPreviewLoading);
        WebView wvPreview = view.findViewById(R.id.wvPreview);
        ScrollView svSourceCode = view.findViewById(R.id.svSourceCode);
        TextView tvSourceCode = view.findViewById(R.id.tvSourceCode);
        TextView tvPreviewError = view.findViewById(R.id.tvPreviewError);

        String displayTitle = (item.getPath() != null && !item.getPath().trim().isEmpty())
                ? item.getPath().trim()
                : item.getName();
        if (!displayTitle.startsWith("/")) {
            displayTitle = "/" + displayTitle;
        }
        tvPreviewPathTitle.setText(displayTitle);

        // Tentukan baseUrl dinamis dari konfigurasi server yang sedang terhubung (LAN, Domain, VPS, Tunnel)
        String rawBaseUrl = (apiClient != null && apiClient.getServerUrl() != null && !apiClient.getServerUrl().trim().isEmpty())
                ? apiClient.getServerUrl().trim()
                : "http://localhost:5050/";
        if (!rawBaseUrl.endsWith("/")) {
            rawBaseUrl += "/";
        }
        final String effectiveBaseUrl = rawBaseUrl;

        // Setup WebView untuk Interactive Web App
        WebSettings webSettings = wvPreview.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(false);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false); // Sembunyikan tombol zoom bawaan Android yang kaku
        wvPreview.setBackgroundColor(Color.parseColor("#0F172A"));

        // Dukung fokus sentuh & input keyboard untuk form HTML
        wvPreview.setFocusable(true);
        wvPreview.setFocusableInTouchMode(true);
        wvPreview.requestFocus(View.FOCUS_DOWN);
        wvPreview.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_UP:
                    if (!v.hasFocus()) {
                        v.requestFocus();
                    }
                    break;
            }
            return false;
        });

        wvPreview.setWebViewClient(new WebViewClient());
        wvPreview.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(activity)
                        .setTitle("Molly Web Preview")
                        .setMessage(message)
                        .setPositiveButton("OK", (d, w) -> result.confirm())
                        .setCancelable(false)
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(activity)
                        .setTitle("Molly Web Preview")
                        .setMessage(message)
                        .setPositiveButton("OK", (d, w) -> result.confirm())
                        .setNegativeButton("Batal", (d, w) -> result.cancel())
                        .setCancelable(false)
                        .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                final EditText input = new EditText(activity);
                input.setText(defaultValue);
                new AlertDialog.Builder(activity)
                        .setTitle("Molly Web Preview")
                        .setMessage(message)
                        .setView(input)
                        .setPositiveButton("OK", (d, w) -> result.confirm(input.getText().toString()))
                        .setNegativeButton("Batal", (d, w) -> result.cancel())
                        .setCancelable(false)
                        .show();
                return true;
            }
        });

        final boolean[] isRenderMode = {true};
        final boolean[] isDesktopMode = {false};
        final int[] currentZoomPercent = {100};
        final String[] rawCleanContent = {""};
        final boolean[] isHtml = {false};

        tvZoomPercent.setText("100%");
        updateModeUI(activity, isRenderMode[0], btnModeRender, btnModeSource, wvPreview, svSourceCode, llZoomControls, btnToggleDesktopMode);

        btnModeRender.setOnClickListener(v -> {
            isRenderMode[0] = true;
            updateModeUI(activity, isRenderMode[0], btnModeRender, btnModeSource, wvPreview, svSourceCode, llZoomControls, btnToggleDesktopMode);
        });

        btnModeSource.setOnClickListener(v -> {
            isRenderMode[0] = false;
            updateModeUI(activity, isRenderMode[0], btnModeRender, btnModeSource, wvPreview, svSourceCode, llZoomControls, btnToggleDesktopMode);
        });

        // Toggle Desktop View vs Mobile Responsive View
        btnToggleDesktopMode.setOnClickListener(v -> {
            if (!isHtml[0]) {
                Toast.makeText(activity, "Mode desktop hanya berlaku untuk berkas HTML/Web", Toast.LENGTH_SHORT).show();
                return;
            }
            isDesktopMode[0] = !isDesktopMode[0];
            currentZoomPercent[0] = 100;
            tvZoomPercent.setText("100%");

            if (isDesktopMode[0]) {
                btnToggleDesktopMode.setImageResource(R.drawable.ic_smartphone);
                btnToggleDesktopMode.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3B82F6")));
                Toast.makeText(activity, "Mode Desktop (1280px) Aktif - Tampilan Layar Lebar", Toast.LENGTH_SHORT).show();
            } else {
                btnToggleDesktopMode.setImageResource(R.drawable.ic_desktop);
                btnToggleDesktopMode.setBackgroundTintList(null);
                Toast.makeText(activity, "Mode Ponsel (Responsif) Aktif", Toast.LENGTH_SHORT).show();
            }

            if (!rawCleanContent[0].isEmpty()) {
                loadHtmlInWebView(wvPreview, rawCleanContent[0], isDesktopMode[0], effectiveBaseUrl);
            }
        });

        btnZoomIn.setOnClickListener(v -> {
            if (currentZoomPercent[0] < 200) {
                currentZoomPercent[0] += 10;
                applyZoom(wvPreview, tvZoomPercent, currentZoomPercent[0]);
            }
        });

        btnZoomOut.setOnClickListener(v -> {
            if (currentZoomPercent[0] > 50) {
                currentZoomPercent[0] -= 10;
                applyZoom(wvPreview, tvZoomPercent, currentZoomPercent[0]);
            }
        });

        tvZoomPercent.setOnClickListener(v -> {
            currentZoomPercent[0] = 100;
            applyZoom(wvPreview, tvZoomPercent, 100);
            Toast.makeText(activity, "Zoom direset ke 100%", Toast.LENGTH_SHORT).show();
        });

        btnPreviewClose.setOnClickListener(v -> dialog.dismiss());

        btnPreviewDownload.setOnClickListener(v -> {
            if (listener != null) listener.onDownload(item);
        });

        btnPreviewSave.setOnClickListener(v -> {
            if (listener != null) listener.onSave(item);
        });

        // Ambil Konten Berkas dari Server Molly
        pbPreviewLoading.setVisibility(View.VISIBLE);
        tvPreviewError.setVisibility(View.GONE);

        apiClient.fetchArtifactRaw(item.getPath(), new MollyApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String content) {
                activity.runOnUiThread(() -> {
                    pbPreviewLoading.setVisibility(View.GONE);
                    if (content == null || content.trim().isEmpty()) {
                        tvPreviewError.setText("Berkas kosong.");
                        tvPreviewError.setVisibility(View.VISIBLE);
                        return;
                    }
                    String cleanContent = content;
                    if (cleanContent.trim().startsWith("{") && cleanContent.contains("\"content\"")) {
                        try {
                            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(cleanContent.trim());
                            if (el.isJsonObject()) {
                                com.google.gson.JsonObject j = el.getAsJsonObject();
                                if (j.has("content") && !j.get("content").isJsonNull()) {
                                    cleanContent = j.get("content").getAsString();
                                } else if (j.has("data") && j.get("data").isJsonObject() && j.getAsJsonObject("data").has("content")) {
                                    cleanContent = j.getAsJsonObject("data").get("content").getAsString();
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    rawCleanContent[0] = cleanContent;
                    tvSourceCode.setText(cleanContent);
                    boolean isHtmlFile = item.isHtml() 
                            || (item.getPath() != null && (item.getPath().toLowerCase().endsWith(".html") || item.getPath().toLowerCase().endsWith(".htm")))
                            || cleanContent.trim().toLowerCase().startsWith("<!doctype html") 
                            || cleanContent.trim().toLowerCase().startsWith("<html");

                    isHtml[0] = isHtmlFile;

                    if (isHtmlFile) {
                        loadHtmlInWebView(wvPreview, cleanContent, isDesktopMode[0], effectiveBaseUrl);
                    } else {
                        // Jika bukan HTML, render sebagai teks polos di webview
                        String wrappedHtml = "<html><body style='color:#E2E8F0;background:#0F172A;font-family:monospace;padding:12px;white-space:pre-wrap;margin:0;'>"
                                + android.text.TextUtils.htmlEncode(cleanContent) + "</body></html>";
                        wvPreview.loadDataWithBaseURL(effectiveBaseUrl, wrappedHtml, "text/html", "UTF-8", null);
                    }
                });
            }

            @Override
            public void onError(String error) {
                activity.runOnUiThread(() -> {
                    pbPreviewLoading.setVisibility(View.GONE);
                    tvPreviewError.setText("Gagal memuat pratinjau: " + error);
                    tvPreviewError.setVisibility(View.VISIBLE);
                });
            }
        });

        dialog.show();
    }

    private static void loadHtmlInWebView(WebView wv, String rawHtml, boolean isDesktopMode, String baseUrl) {
        WebSettings webSettings = wv.getSettings();
        if (isDesktopMode) {
            webSettings.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            webSettings.setUseWideViewPort(true);
            webSettings.setLoadWithOverviewMode(true);
        } else {
            webSettings.setUserAgentString(null);
            webSettings.setUseWideViewPort(true);
            webSettings.setLoadWithOverviewMode(false);
        }

        String htmlToLoad = rawHtml;

        if (isDesktopMode) {
            // Viewport virtual width 1280px untuk merender layout kartu berdampingan seperti di PC Web Molly
            htmlToLoad = htmlToLoad.replaceAll("(?i)<meta\\s+name=[\"']viewport[\"'][^>]*>", "");
            String desktopMeta = "<meta name=\"viewport\" content=\"width=1280, initial-scale=0.32, minimum-scale=0.2, maximum-scale=3.0, user-scalable=yes\">";
            if (htmlToLoad.toLowerCase().contains("<head>")) {
                htmlToLoad = htmlToLoad.replaceFirst("(?i)<head>", "<head>" + desktopMeta);
            } else {
                htmlToLoad = desktopMeta + htmlToLoad;
            }
        } else {
            // Di mode mobile ponsel: Pertahankan HTML asli pengembang secara 100% utuh tanpa modifikasi destruktif
            // Hanya tambahkan viewport jika berkas belum memilikinya
            if (!htmlToLoad.toLowerCase().contains("name=\"viewport\"") && !htmlToLoad.toLowerCase().contains("name='viewport'")) {
                String mobileMeta = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\">";
                if (htmlToLoad.toLowerCase().contains("<head>")) {
                    htmlToLoad = htmlToLoad.replaceFirst("(?i)<head>", "<head>" + mobileMeta);
                } else {
                    htmlToLoad = mobileMeta + htmlToLoad;
                }
            }
        }

        String safeBaseUrl = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : "http://localhost:5050/";
        if (!safeBaseUrl.endsWith("/")) safeBaseUrl += "/";
        wv.loadDataWithBaseURL(safeBaseUrl, htmlToLoad, "text/html", "UTF-8", null);
    }

    private static void applyZoom(WebView wv, TextView tvPercent, int percent) {
        tvPercent.setText(percent + "%");
        float zoomFactor = percent / 100.0f;
        // Menggunakan CSS zoom agar reflow rapi dan tidak menyisakan ruang kosong hitam di sebelah kanan
        wv.evaluateJavascript(
            "(function() {" +
            "   document.body.style.zoom = '" + zoomFactor + "';" +
            "})();", null
        );
    }

    private static void updateModeUI(Activity activity, boolean isRenderMode, ImageView btnRender, ImageView btnSource, WebView wv, View sv, View zoomControls, View btnToggleDesktop) {
        if (isRenderMode) {
            btnRender.setBackgroundResource(R.drawable.bg_action_pill);
            btnRender.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3B82F6")));
            btnSource.setBackground(null);
            wv.setVisibility(View.VISIBLE);
            sv.setVisibility(View.GONE);
            zoomControls.setVisibility(View.VISIBLE);
            btnToggleDesktop.setVisibility(View.VISIBLE);
        } else {
            btnSource.setBackgroundResource(R.drawable.bg_action_pill);
            btnSource.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3B82F6")));
            btnRender.setBackground(null);
            wv.setVisibility(View.GONE);
            sv.setVisibility(View.VISIBLE);
            zoomControls.setVisibility(View.GONE);
            btnToggleDesktop.setVisibility(View.GONE);
        }
    }
}
