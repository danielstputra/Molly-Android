package com.molly.agent.ui;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.molly.agent.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper untuk merender Rich Widgets interaktif di chat Android Molly:
 * 1. Gambar 1: Widget Text / Code Block dengan tombol [menyalin]
 * 2. Gambar 2: Widget Prompt dengan aksen garis kiri teal (#06B6D4) dan tombol [menyalin]
 * 3. Gambar 3: Full Chat-Widgets (Card, Title, Text, Form, RadioGroup pills, Input, DatePicker, Submit Button, Image)
 */
public class ChatWidgetViewHelper {

    public interface OnWidgetActionListener {
        void onSendMessage(String actionMessage);
    }

    private static final Pattern FENCED_BLOCK_PATTERN = Pattern.compile("```([a-zA-Z0-9_-]*)[\\r\\n]([\\s\\S]*?)```");
    private static final Pattern PROMPT_PREFIX_PATTERN = Pattern.compile("(?i)^(?:>\\s*)?(?:prompt|prompt siap pakai[^:]*):\\s*", Pattern.MULTILINE);

    /**
     * Memproses teks pesan agen dan menyusun antarmuka widget di dalam bubble obrolan
     */
    public static void render(
            Context context,
            String rawText,
            TextView tvMessage,
            LinearLayout widgetContainer,
            OnWidgetActionListener actionListener
    ) {
        if (widgetContainer == null) return;
        widgetContainer.removeAllViews();

        if (rawText == null || rawText.trim().isEmpty()) {
            if (tvMessage != null) {
                tvMessage.setText("");
                tvMessage.setVisibility(View.GONE);
            }
            widgetContainer.setVisibility(View.GONE);
            return;
        }

        // Cek apakah pesan memuat blok widget, blok kode, prompt, tabel terstruktur, atau gambar markdown
        Matcher matcher = FENCED_BLOCK_PATTERN.matcher(rawText);
        boolean hasFenced = matcher.find();
        boolean hasTable = checkHasTable(rawText);
        boolean hasMdImage = Pattern.compile("(?m)^\\s*!\\[.*?\\]\\(.*?\\)").matcher(rawText).find();

        if (!hasFenced && !hasTable && !hasMdImage) {
            // Teks markdown murni biasa
            if (tvMessage != null) {
                tvMessage.setVisibility(View.VISIBLE);
                tvMessage.setText(MarkdownHelper.render(context, rawText));
            }
            widgetContainer.setVisibility(View.GONE);
            return;
        }

        List<View> generatedViews = new ArrayList<>();

        if (!hasFenced && (hasTable || hasMdImage)) {
            // Teks memuat tabel atau gambar markdown tanpa fenced block
            appendSegmentViews(context, rawText, generatedViews);
        } else {
            // Terdapat blok fenced (```widget, ```prompt, ```code, dll): lakukan parsing terstruktur
            matcher.reset();
            int lastEnd = 0;

            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();

                // Teks sebelum blok
                String leadingPromptTitle = "Prompt:";
                boolean hasLeadingPromptHeader = false;

                if (start > lastEnd) {
                    String leadingText = rawText.substring(lastEnd, start).trim();
                    if (!leadingText.isEmpty()) {
                        // Jika leadingText diakhiri kata "Prompt:" atau "Prompt :", bersihkan agar tidak duplikat
                        Matcher pm = Pattern.compile("(?is)(.*?)(?:^|\\n)\\s*(?:>\\s*)?(prompt|prompt siap pakai[^:]*):\\s*$").matcher(leadingText);
                        if (pm.find()) {
                            hasLeadingPromptHeader = true;
                            leadingPromptTitle = pm.group(2) != null && !pm.group(2).trim().isEmpty() ? pm.group(2).trim() + ":" : "Prompt:";
                            String proseBeforePrompt = pm.group(1) != null ? pm.group(1).trim() : "";
                            if (!proseBeforePrompt.isEmpty()) {
                                appendSegmentViews(context, proseBeforePrompt, generatedViews);
                            }
                        } else {
                            appendSegmentViews(context, leadingText, generatedViews);
                        }
                    }
                }

                String lang = matcher.group(1) != null ? matcher.group(1).trim().toLowerCase() : "";
                String blockContent = matcher.group(2) != null ? matcher.group(2) : "";

                if ("widget".equals(lang)) {
                    // Gambar 3: Interactive Chat Widget JSON
                    View widgetView = renderWidgetJson(context, blockContent.trim(), actionListener);
                    if (widgetView != null) {
                        generatedViews.add(widgetView);
                    }
                } else if ("prompt".equals(lang) || hasLeadingPromptHeader || blockContent.toLowerCase().contains("--ar") || blockContent.toLowerCase().contains("cinematic")) {
                    // Gambar 2: Widget Prompt Khusus dengan aksen garis kiri & tombol [menyalin]
                    View promptView = createPromptWidgetView(context, leadingPromptTitle, blockContent.trim());
                    generatedViews.add(promptView);
                } else {
                    // Gambar 1: Widget Text / Code Block dengan tombol copy ("menyalin") dan scroll horizontal
                    View codeView = createCodeBlockWidgetView(context, lang, blockContent.trim());
                    generatedViews.add(codeView);
                }

                lastEnd = end;
            }

            // Teks setelah blok terakhir
            if (lastEnd < rawText.length()) {
                String trailingText = rawText.substring(lastEnd).trim();
                if (!trailingText.isEmpty()) {
                    appendSegmentViews(context, trailingText, generatedViews);
                }
            }
        }

        if (generatedViews.isEmpty()) {
            if (tvMessage != null) {
                tvMessage.setVisibility(View.VISIBLE);
                tvMessage.setText(MarkdownHelper.render(context, rawText));
            }
            widgetContainer.setVisibility(View.GONE);
        } else {
            if (tvMessage != null) {
                tvMessage.setText("");
                tvMessage.setVisibility(View.GONE);
            }
            widgetContainer.setVisibility(View.VISIBLE);
            for (View v : generatedViews) {
                widgetContainer.addView(v);
            }
        }
    }

    // =========================================================================
    // GAMBAR 1 & 2: Widget Text / Code Block dengan tombol [menyalin]
    // =========================================================================
    private static View createCodeBlockWidgetView(Context context, String language, String codeContent) {
        FrameLayout card = new FrameLayout(context);
        card.setBackgroundResource(R.drawable.bg_code_block);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dpToPx(context, 8);
        cardLp.bottomMargin = dpToPx(context, 8);
        card.setLayoutParams(cardLp);

        // Scroll horizontal untuk isi teks / kode (Gambar 1 & Gambar 2)
        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setHorizontalScrollBarEnabled(true);
        hsv.setScrollbarFadingEnabled(false);
        FrameLayout.LayoutParams hsvLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hsv.setLayoutParams(hsvLp);

        TextView tvCode = new TextView(context);
        tvCode.setText(codeContent);
        tvCode.setTextColor(0xFFE2E8F0);
        tvCode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tvCode.setTypeface(Typeface.MONOSPACE);
        tvCode.setTextIsSelectable(true);
        tvCode.setLineSpacing(dpToPx(context, 2), 1.0f);
        int pad = dpToPx(context, 12);
        int rightPadForButton = dpToPx(context, 76);
        tvCode.setPadding(pad, pad, rightPadForButton, pad);
        hsv.addView(tvCode);
        card.addView(hsv);

        // Tombol Menyalin di pojok kanan atas di dalam box (Gambar 1 & Gambar 2)
        TextView btnCopy = createCopyPillButton(context, "menyalin", codeContent);
        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.gravity = Gravity.TOP | Gravity.END;
        btnLp.topMargin = dpToPx(context, 8);
        btnLp.rightMargin = dpToPx(context, 8);
        btnCopy.setLayoutParams(btnLp);
        card.addView(btnCopy);

        return card;
    }

    // =========================================================================
    // GAMBAR 2: Widget Prompt dengan aksen garis kiri teal (#06B6D4) & copy
    // =========================================================================
    private static View createPromptWidgetView(Context context, String title, String promptContent) {
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);

        // Background dark dengan left border cyan / teal
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xFF13171F);
        gd.setCornerRadius(dpToPx(context, 8));
        gd.setStroke(dpToPx(context, 1.2f), 0xFF262D35);
        outer.setBackground(gd);

        LinearLayout.LayoutParams outerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        outerLp.topMargin = dpToPx(context, 8);
        outerLp.bottomMargin = dpToPx(context, 8);
        outer.setLayoutParams(outerLp);

        // Inner container dengan aksen garis kiri (#06B6D4)
        LinearLayout contentRow = new LinearLayout(context);
        contentRow.setOrientation(LinearLayout.HORIZONTAL);
        int pad = dpToPx(context, 10);
        contentRow.setPadding(pad, pad, pad, pad);

        // Garis aksen kiri teal
        View leftStripe = new View(context);
        GradientDrawable stripeGd = new GradientDrawable();
        stripeGd.setColor(0xFF06B6D4);
        stripeGd.setCornerRadius(dpToPx(context, 2));
        leftStripe.setBackground(stripeGd);
        LinearLayout.LayoutParams stripeLp = new LinearLayout.LayoutParams(
                dpToPx(context, 3.5f), ViewGroup.LayoutParams.MATCH_PARENT);
        stripeLp.rightMargin = dpToPx(context, 10);
        leftStripe.setLayoutParams(stripeLp);
        contentRow.addView(leftStripe);

        // Main body
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        body.setLayoutParams(bodyLp);

        // Header prompt + tombol copy
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerLp.bottomMargin = dpToPx(context, 6);
        header.setLayoutParams(headerLp);

        TextView tvTitle = new TextView(context);
        tvTitle.setText(title != null ? title : "Prompt:");
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitle.setLayoutParams(titleLp);
        header.addView(tvTitle);

        TextView btnCopy = createCopyPillButton(context, "menyalin", promptContent);
        header.addView(btnCopy);
        body.addView(header);

        // Prompt text box
        TextView tvPrompt = new TextView(context);
        tvPrompt.setText(MarkdownHelper.render(context, promptContent));
        tvPrompt.setTextColor(0xFFE2E8F0);
        tvPrompt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvPrompt.setTypeface(Typeface.MONOSPACE);
        tvPrompt.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        tvPrompt.setLinkTextColor(0xFF38BDF8);
        tvPrompt.setBackgroundResource(R.drawable.bg_prompt_widget);
        int innerPad = dpToPx(context, 8);
        tvPrompt.setPadding(innerPad, innerPad, innerPad, innerPad);
        body.addView(tvPrompt);

        contentRow.addView(body);
        outer.addView(contentRow);
        return outer;
    }

    // =========================================================================
    // GAMBAR 3: Full Chat-Widgets (Form, RadioGroup, Input, Button, Image)
    // =========================================================================
    private static View renderWidgetJson(Context context, String jsonSource, OnWidgetActionListener actionListener) {
        try {
            JsonElement rootElement = JsonParser.parseString(jsonSource);
            if (!rootElement.isJsonObject()) return null;
            JsonObject root = rootElement.getAsJsonObject();

            String type = root.has("type") ? root.get("type").getAsString() : "Card";
            return buildWidgetNode(context, root, actionListener, new HashMap<>());
        } catch (Exception e) {
            // Jika parsing JSON gagal, tampilkan fallback teks kode
            return createCodeBlockWidgetView(context, "JSON", jsonSource);
        }
    }

    private static View buildWidgetNode(
            Context context,
            JsonObject node,
            OnWidgetActionListener actionListener,
            Map<String, String> formValues
    ) {
        if (node == null) return null;
        String type = node.has("type") ? node.get("type").getAsString() : "Card";

        switch (type) {
            case "Card": {
                LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundResource(R.drawable.bg_widget_card);
                int p = dpToPx(context, 14);
                card.setPadding(p, p, p, p);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dpToPx(context, 8);
                lp.bottomMargin = dpToPx(context, 8);
                card.setLayoutParams(lp);

                if (node.has("children") && node.get("children").isJsonArray()) {
                    JsonArray children = node.getAsJsonArray("children");
                    for (int i = 0; i < children.size(); i++) {
                        if (children.get(i).isJsonObject()) {
                            View childView = buildWidgetNode(context, children.get(i).getAsJsonObject(), actionListener, formValues);
                            if (childView != null) card.addView(childView);
                        }
                    }
                }
                return card;
            }

            case "Title": {
                TextView tv = new TextView(context);
                tv.setText(node.has("value") ? node.get("value").getAsString() : "");
                tv.setTextColor(0xFFFFFFFF);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dpToPx(context, 4);
                tv.setLayoutParams(lp);
                return tv;
            }

            case "Text": {
                TextView tv = new TextView(context);
                tv.setText(node.has("value") ? node.get("value").getAsString() : "");
                tv.setTextColor(0xFF9CA3AF);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tv.setLineSpacing(dpToPx(context, 2), 1.0f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dpToPx(context, 10);
                tv.setLayoutParams(lp);
                return tv;
            }

            case "Caption": {
                TextView tv = new TextView(context);
                tv.setText(node.has("value") ? node.get("value").getAsString() : "");
                tv.setTextColor(0xFF6B7280);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dpToPx(context, 6);
                tv.setLayoutParams(lp);
                return tv;
            }

            case "Form": {
                LinearLayout formContainer = new LinearLayout(context);
                formContainer.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                formContainer.setLayoutParams(lp);

                final Map<String, String> localFormValues = new HashMap<>();
                final List<JsonObject> requiredFields = new ArrayList<>();
                final JsonObject onSubmitAction = node.has("onSubmitAction") && node.get("onSubmitAction").isJsonObject()
                        ? node.getAsJsonObject("onSubmitAction") : null;

                if (node.has("children") && node.get("children").isJsonArray()) {
                    JsonArray children = node.getAsJsonArray("children");
                    for (int i = 0; i < children.size(); i++) {
                        if (children.get(i).isJsonObject()) {
                            JsonObject childNode = children.get(i).getAsJsonObject();
                            String childType = childNode.has("type") ? childNode.get("type").getAsString() : "";
                            boolean isReq = childNode.has("required") && childNode.get("required").getAsBoolean();
                            if (isReq) requiredFields.add(childNode);

                            if ("Button".equals(childType)) {
                                View submitBtn = buildSubmitButton(context, childNode, localFormValues, requiredFields, onSubmitAction, actionListener);
                                formContainer.addView(submitBtn);
                            } else {
                                View childView = buildWidgetNode(context, childNode, actionListener, localFormValues);
                                if (childView != null) formContainer.addView(childView);
                            }
                        }
                    }
                }
                return formContainer;
            }

            case "RadioGroup": {
                return buildRadioGroupView(context, node, formValues);
            }

            case "Input":
            case "Textarea": {
                return buildInputFieldView(context, node, formValues, "Textarea".equals(type));
            }

            case "DatePicker": {
                return buildDatePickerView(context, node, formValues);
            }

            case "Button": {
                return buildNormalButton(context, node, actionListener);
            }

            case "Image": {
                return buildImageView(context, node);
            }

            case "Video": {
                return buildVideoView(context, node);
            }

            case "ImageGroup": {
                return buildImageGroupView(context, node);
            }

            case "Divider": {
                View divider = new View(context);
                divider.setBackgroundColor(0xFF2D333B);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 1));
                lp.topMargin = dpToPx(context, 8);
                lp.bottomMargin = dpToPx(context, 8);
                divider.setLayoutParams(lp);
                return divider;
            }

            case "Row": {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                row.setLayoutParams(lp);
                if (node.has("children") && node.get("children").isJsonArray()) {
                    JsonArray children = node.getAsJsonArray("children");
                    for (int i = 0; i < children.size(); i++) {
                        if (children.get(i).isJsonObject()) {
                            View cv = buildWidgetNode(context, children.get(i).getAsJsonObject(), actionListener, formValues);
                            if (cv != null) {
                                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                                clp.setMarginEnd(dpToPx(context, 6));
                                clp.rightMargin = dpToPx(context, 6);
                                cv.setLayoutParams(clp);
                                row.addView(cv);
                            }
                        }
                    }
                }
                return row;
            }

            case "Col": {
                LinearLayout col = new LinearLayout(context);
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                col.setLayoutParams(lp);
                if (node.has("children") && node.get("children").isJsonArray()) {
                    JsonArray children = node.getAsJsonArray("children");
                    for (int i = 0; i < children.size(); i++) {
                        if (children.get(i).isJsonObject()) {
                            View cv = buildWidgetNode(context, children.get(i).getAsJsonObject(), actionListener, formValues);
                            if (cv != null) col.addView(cv);
                        }
                    }
                }
                return col;
            }

            case "ListView": {
                LinearLayout lv = new LinearLayout(context);
                lv.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lv.setLayoutParams(lp);
                if (node.has("children") && node.get("children").isJsonArray()) {
                    JsonArray children = node.getAsJsonArray("children");
                    for (int i = 0; i < children.size(); i++) {
                        if (children.get(i).isJsonObject()) {
                            View cv = buildWidgetNode(context, children.get(i).getAsJsonObject(), actionListener, formValues);
                            if (cv != null) lv.addView(cv);
                        }
                    }
                }
                return lv;
            }

            case "ListViewItem": {
                LinearLayout item = new LinearLayout(context);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setBackgroundResource(R.drawable.bg_widget_radio_item);
                int p = dpToPx(context, 10);
                item.setPadding(p, p, p, p);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dpToPx(context, 6);
                item.setLayoutParams(lp);

                if (node.has("onClickAction") && node.get("onClickAction").isJsonObject()) {
                    JsonObject act = node.getAsJsonObject("onClickAction");
                    item.setClickable(true);
                    item.setOnClickListener(v -> {
                        String textToSend = "";
                        if (act.has("text")) {
                            textToSend = act.get("text").getAsString();
                        } else if (act.has("payload") && act.getAsJsonObject("payload").has("text")) {
                            textToSend = act.getAsJsonObject("payload").get("text").getAsString();
                        }
                        if (actionListener != null && !textToSend.isEmpty()) {
                            actionListener.onSendMessage(textToSend);
                        }
                    });
                }

                if (node.has("children") && node.get("children").isJsonArray()) {
                    JsonArray children = node.getAsJsonArray("children");
                    for (int i = 0; i < children.size(); i++) {
                        if (children.get(i).isJsonObject()) {
                            View cv = buildWidgetNode(context, children.get(i).getAsJsonObject(), actionListener, formValues);
                            if (cv != null) item.addView(cv);
                        }
                    }
                }
                return item;
            }

            case "Badge": {
                TextView badge = new TextView(context);
                String label = node.has("label") ? node.get("label").getAsString() : "";
                badge.setText(label);
                badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                String colorName = node.has("color") ? node.get("color").getAsString() : "info";
                int color = 0xFF38BDF8; // cyan
                int bgColor = 0x2238BDF8;
                if ("success".equalsIgnoreCase(colorName)) {
                    color = 0xFF10B981;
                    bgColor = 0x2210B981;
                } else if ("warning".equalsIgnoreCase(colorName)) {
                    color = 0xFFF59E0B;
                    bgColor = 0x22F59E0B;
                } else if ("danger".equalsIgnoreCase(colorName)) {
                    color = 0xFFEF4444;
                    bgColor = 0x22EF4444;
                }
                badge.setTextColor(color);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(bgColor);
                bg.setCornerRadius(dpToPx(context, 12));
                bg.setStroke(dpToPx(context, 1), color);
                badge.setBackground(bg);
                int hp = dpToPx(context, 8);
                int vp = dpToPx(context, 3);
                badge.setPadding(hp, vp, hp, vp);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                badge.setLayoutParams(lp);
                return badge;
            }

            case "Icon": {
                TextView iconTv = new TextView(context);
                String name = node.has("name") ? node.get("name").getAsString() : "";
                String emoji = "✦";
                if ("book-open".equals(name)) emoji = "📖";
                else if ("document".equals(name)) emoji = "📄";
                else if ("lightbulb".equals(name)) emoji = "💡";
                else if ("info".equals(name)) emoji = "ℹ️";
                else if ("check".equals(name)) emoji = "✓";
                else if ("plus".equals(name)) emoji = "➕";
                else if ("search".equals(name)) emoji = "🔍";
                else if ("chevron-right".equals(name)) emoji = "›";
                iconTv.setText(emoji);
                iconTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd(dpToPx(context, 6));
                lp.rightMargin = dpToPx(context, 6);
                iconTv.setLayoutParams(lp);
                return iconTv;
            }

            case "Spacer": {
                View spacer = new View(context);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 10));
                spacer.setLayoutParams(lp);
                return spacer;
            }

            case "Chart": {
                return buildChartSummaryView(context, node);
            }

            default:
                return null;
        }
    }

    // =========================================================================
    // FORM BUILDERS: RadioGroup (Gambar 4), Input, Submit Button
    // =========================================================================
    private static View buildRadioGroupView(Context context, JsonObject node, Map<String, String> formValues) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        containerLp.bottomMargin = dpToPx(context, 12);
        container.setLayoutParams(containerLp);

        String name = node.has("name") ? node.get("name").getAsString() : "field";
        String label = node.has("label") ? node.get("label").getAsString() : "";
        boolean required = node.has("required") && node.get("required").getAsBoolean();

        // Label field
        TextView tvLabel = new TextView(context);
        tvLabel.setText(label + (required ? " *" : ""));
        tvLabel.setTextColor(0xFFF3F4F6);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.bottomMargin = dpToPx(context, 6);
        tvLabel.setLayoutParams(labelLp);
        container.addView(tvLabel);

        // List opsi disusun dalam baris-baris horizontal (pills seperti Gambar 4)
        LinearLayout optionsLayout = new LinearLayout(context);
        optionsLayout.setOrientation(LinearLayout.VERTICAL);
        optionsLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        List<View> optionViews = new ArrayList<>();
        if (node.has("options") && node.get("options").isJsonArray()) {
            JsonArray options = node.getAsJsonArray("options");
            LinearLayout currentRow = null;
            int countInRow = 0;
            int maxPerRow = options.size() <= 4 ? 2 : 3;

            for (int i = 0; i < options.size(); i++) {
                if (!options.get(i).isJsonObject()) continue;
                JsonObject opt = options.get(i).getAsJsonObject();
                String optLabel = opt.has("label") ? opt.get("label").getAsString() : "";
                String optVal = opt.has("value") ? opt.get("value").getAsString() : optLabel;

                if (currentRow == null || countInRow >= maxPerRow) {
                    currentRow = new LinearLayout(context);
                    currentRow.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rowLp.bottomMargin = dpToPx(context, 6);
                    currentRow.setLayoutParams(rowLp);
                    optionsLayout.addView(currentRow);
                    countInRow = 0;
                }

                LinearLayout optCard = new LinearLayout(context);
                optCard.setOrientation(LinearLayout.HORIZONTAL);
                optCard.setGravity(Gravity.CENTER_VERTICAL);
                optCard.setBackgroundResource(R.drawable.bg_widget_radio_item);
                int hPad = dpToPx(context, 10);
                int vPad = dpToPx(context, 8);
                optCard.setPadding(hPad, vPad, hPad, vPad);
                LinearLayout.LayoutParams optLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                optLp.setMarginEnd(dpToPx(context, 6));
                optLp.rightMargin = dpToPx(context, 6);
                optCard.setLayoutParams(optLp);

                // Radio circle symbol (○ atau ◉)
                TextView tvDot = new TextView(context);
                tvDot.setText("○");
                tvDot.setTextColor(0xFF9CA3AF);
                tvDot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                tvDot.setPadding(0, 0, dpToPx(context, 6), 0);
                optCard.addView(tvDot);

                TextView tvText = new TextView(context);
                tvText.setText(optLabel);
                tvText.setTextColor(0xFFE5E7EB);
                tvText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                optCard.addView(tvText);

                optCard.setOnClickListener(v -> {
                    formValues.put(name, optVal);
                    for (View ov : optionViews) {
                        ov.setBackgroundResource(R.drawable.bg_widget_radio_item);
                        TextView dot = (TextView) ((LinearLayout) ov).getChildAt(0);
                        TextView text = (TextView) ((LinearLayout) ov).getChildAt(1);
                        dot.setText("○");
                        dot.setTextColor(0xFF9CA3AF);
                        text.setTextColor(0xFFE5E7EB);
                    }
                    optCard.setBackgroundResource(R.drawable.bg_widget_radio_item_selected);
                    tvDot.setText("◉");
                    tvDot.setTextColor(0xFF06B6D4);
                    tvText.setTextColor(0xFFFFFFFF);
                });

                optionViews.add(optCard);
                currentRow.addView(optCard);
                countInRow++;
            }
        }

        container.addView(optionsLayout);
        return container;
    }

    private static View buildInputFieldView(Context context, JsonObject node, Map<String, String> formValues, boolean isTextarea) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        containerLp.bottomMargin = dpToPx(context, 12);
        container.setLayoutParams(containerLp);

        String name = node.has("name") ? node.get("name").getAsString() : "field";
        String label = node.has("label") ? node.get("label").getAsString() : "";
        String placeholder = node.has("placeholder") ? node.get("placeholder").getAsString() : "";
        boolean required = node.has("required") && node.get("required").getAsBoolean();

        TextView tvLabel = new TextView(context);
        tvLabel.setText(label + (required ? " *" : ""));
        tvLabel.setTextColor(0xFFF3F4F6);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.bottomMargin = dpToPx(context, 6);
        tvLabel.setLayoutParams(labelLp);
        container.addView(tvLabel);

        EditText et = new EditText(context);
        et.setHint(placeholder);
        et.setHintTextColor(0xFF6B7280);
        et.setTextColor(0xFFFFFFFF);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xFF14171E);
        gd.setCornerRadius(dpToPx(context, 8));
        gd.setStroke(dpToPx(context, 1), 0xFF30363D);
        et.setBackground(gd);
        int p = dpToPx(context, 10);
        et.setPadding(p, p, p, p);

        if (isTextarea) {
            et.setMinLines(3);
            et.setMaxLines(6);
            et.setGravity(Gravity.TOP | Gravity.START);
        } else {
            et.setSingleLine(true);
        }

        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                formValues.put(name, s != null ? s.toString() : "");
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        container.addView(et);
        return container;
    }

    private static View buildDatePickerView(Context context, JsonObject node, Map<String, String> formValues) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        containerLp.bottomMargin = dpToPx(context, 12);
        container.setLayoutParams(containerLp);

        String name = node.has("name") ? node.get("name").getAsString() : "date";
        String label = node.has("label") ? node.get("label").getAsString() : "Tanggal";
        boolean required = node.has("required") && node.get("required").getAsBoolean();

        TextView tvLabel = new TextView(context);
        tvLabel.setText(label + (required ? " *" : ""));
        tvLabel.setTextColor(0xFFF3F4F6);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        container.addView(tvLabel);

        TextView tvDateBtn = new TextView(context);
        tvDateBtn.setText("Pilih tanggal (YYYY-MM-DD)");
        tvDateBtn.setTextColor(0xFF9CA3AF);
        tvDateBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvDateBtn.setBackgroundResource(R.drawable.bg_widget_radio_item);
        int p = dpToPx(context, 10);
        tvDateBtn.setPadding(p, p, p, p);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = dpToPx(context, 6);
        tvDateBtn.setLayoutParams(btnLp);

        tvDateBtn.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(context, (view, year, month, dayOfMonth) -> {
                String chosen = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                tvDateBtn.setText(chosen);
                tvDateBtn.setTextColor(0xFFFFFFFF);
                formValues.put(name, chosen);
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        container.addView(tvDateBtn);
        return container;
    }

    private static View buildSubmitButton(
            Context context,
            JsonObject buttonNode,
            Map<String, String> formValues,
            List<JsonObject> requiredFields,
            JsonObject onSubmitAction,
            OnWidgetActionListener actionListener
    ) {
        String label = buttonNode.has("label") ? buttonNode.get("label").getAsString() : "Kirim";

        TextView btn = new TextView(context);
        btn.setText(label);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setBackgroundResource(R.drawable.bg_widget_submit_btn);
        int hPad = dpToPx(context, 16);
        int vPad = dpToPx(context, 10);
        btn.setPadding(hPad, vPad, hPad, vPad);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.END;
        lp.topMargin = dpToPx(context, 8);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            // Validasi field required
            for (JsonObject reqNode : requiredFields) {
                String fName = reqNode.has("name") ? reqNode.get("name").getAsString() : "";
                String fLabel = reqNode.has("label") ? reqNode.get("label").getAsString() : fName;
                String val = formValues.get(fName);
                if (val == null || val.trim().isEmpty()) {
                    Toast.makeText(context, "Mohon lengkapi: " + fLabel, Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // Kumpulkan isi payload submit
            StringBuilder sb = new StringBuilder();
            if (onSubmitAction != null && onSubmitAction.has("payload")) {
                JsonObject payload = onSubmitAction.getAsJsonObject("payload");
                if (payload.has("text")) {
                    sb.append(payload.get("text").getAsString()).append("\n");
                }
            } else {
                sb.append("Tolong proses data formulir berikut:\n");
            }

            for (Map.Entry<String, String> entry : formValues.entrySet()) {
                sb.append("\n- ").append(entry.getKey()).append(": ").append(entry.getValue());
            }

            if (actionListener != null) {
                actionListener.onSendMessage(sb.toString());
            }
        });

        return btn;
    }

    private static View buildNormalButton(Context context, JsonObject buttonNode, OnWidgetActionListener actionListener) {
        String label = buttonNode.has("label") ? buttonNode.get("label").getAsString() : "Klik";

        TextView btn = new TextView(context);
        btn.setText(label);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setGravity(Gravity.CENTER);
        btn.setBackgroundResource(R.drawable.bg_widget_submit_btn);
        int p = dpToPx(context, 10);
        btn.setPadding(p, p, p, p);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dpToPx(context, 6);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            if (buttonNode.has("onClickAction")) {
                JsonObject act = buttonNode.getAsJsonObject("onClickAction");
                if (act.has("payload") && act.getAsJsonObject("payload").has("text")) {
                    String msg = act.getAsJsonObject("payload").get("text").getAsString();
                    if (actionListener != null) actionListener.onSendMessage(msg);
                }
            }
        });

        return btn;
    }

    private static View buildImageView(Context context, JsonObject node) {
        String src = node.has("src") ? node.get("src").getAsString() : "";
        String path = node.has("path") ? node.get("path").getAsString() : "";
        String alt = node.has("alt") ? node.get("alt").getAsString() : "Gambar";
        String caption = node.has("caption") ? node.get("caption").getAsString() : "";

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dpToPx(context, 8);
        lp.bottomMargin = dpToPx(context, 8);
        layout.setLayoutParams(lp);

        // Container kartu gambar dengan background elegan dan rounded corners
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setColor(0xFF14171E);
        cardBg.setCornerRadius(dpToPx(context, 12));
        cardBg.setStroke(dpToPx(context, 1), 0xFF2A2E37);
        layout.setBackground(cardBg);
        layout.setPadding(dpToPx(context, 8), dpToPx(context, 8), dpToPx(context, 8), dpToPx(context, 8));

        ImageView iv = new ImageView(context);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setAdjustViewBounds(true);
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iv.setLayoutParams(ivLp);

        final String displayTitle = !caption.isEmpty() ? caption : alt;

        // 1. Jika gambar berupa data:image Base64
        if (src.startsWith("data:image/") && src.contains(";base64,")) {
            try {
                String base64Data = src.substring(src.indexOf(";base64,") + 8);
                byte[] decoded = Base64.decode(base64Data, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                if (bitmap != null) {
                    iv.setImageBitmap(bitmap);
                    iv.setOnClickListener(v -> showFullScreenImageDialog(context, bitmap, displayTitle));
                }
            } catch (Exception ignored) {}
        } else if (!src.isEmpty() || !path.isEmpty()) {
            // 2. Muat gambar secara asinkron dari URL atau berkas server/workspace
            loadImageAsync(context, iv, src, path, displayTitle);
        } else {
            // Placeholder visual default
            iv.setImageResource(R.drawable.ic_intro_agent);
            iv.setColorFilter(0xFF38BDF8);
        }

        layout.addView(iv);

        if (!displayTitle.isEmpty()) {
            TextView tvCap = new TextView(context);
            tvCap.setText(displayTitle);
            tvCap.setTextColor(0xFF94A3B8);
            tvCap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvCap.setGravity(Gravity.CENTER);
            tvCap.setPadding(dpToPx(context, 6), dpToPx(context, 6), dpToPx(context, 6), dpToPx(context, 2));
            layout.addView(tvCap);
        }

        return layout;
    }

    private static void loadImageAsync(Context context, ImageView iv, String rawUrl, String path, String caption) {
        iv.setImageResource(R.drawable.ic_intro_agent);
        iv.setColorFilter(0x6638BDF8);

        com.molly.agent.api.MollyApiClient apiClient = com.molly.agent.api.MollyApiClient.getInstance(context);
        String serverUrl = apiClient != null ? apiClient.getServerUrl() : "";
        String targetUrl = "";

        if (rawUrl != null && !rawUrl.trim().isEmpty()) {
            targetUrl = rawUrl.trim();
            if (targetUrl.startsWith("/")) {
                if (serverUrl.endsWith("/")) {
                    targetUrl = serverUrl + targetUrl.substring(1);
                } else {
                    targetUrl = serverUrl + targetUrl;
                }
            }
        } else if (path != null && !path.trim().isEmpty()) {
            String cleanPath = path.trim();
            try {
                targetUrl = serverUrl + "/api/system/mobile/workspace/download?path=" + java.net.URLEncoder.encode(cleanPath, "UTF-8");
            } catch (Exception ignored) {
                targetUrl = serverUrl + "/api/system/mobile/workspace/download?path=" + cleanPath;
            }
        }

        if (targetUrl.isEmpty()) {
            iv.setColorFilter(0xFF38BDF8);
            return;
        }

        final String finalUrl = targetUrl;
        final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

        new Thread(() -> {
            try {
                okhttp3.OkHttpClient client = apiClient != null && apiClient.getOkHttpClient() != null
                        ? apiClient.getOkHttpClient()
                        : new okhttp3.OkHttpClient.Builder()
                                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                                .build();

                okhttp3.Request.Builder reqBuilder = new okhttp3.Request.Builder()
                        .url(finalUrl)
                        .addHeader("User-Agent", "Molly-Android")
                        .addHeader("Bypass-Tunnel-Reminder", "true")
                        .addHeader("ngrok-skip-browser-warning", "true");

                if (apiClient != null && apiClient.getToken() != null && !apiClient.getToken().isEmpty()) {
                    reqBuilder.addHeader("Authorization", "Bearer " + apiClient.getToken());
                }

                okhttp3.Response response = client.newCall(reqBuilder.build()).execute();
                if (response.isSuccessful() && response.body() != null) {
                    byte[] bytes = response.body().bytes();
                    if (bytes != null && bytes.length > 0) {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bitmap != null) {
                            mainHandler.post(() -> {
                                iv.clearColorFilter();
                                iv.setImageBitmap(bitmap);
                                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                iv.setAdjustViewBounds(true);
                                iv.setOnClickListener(v -> showFullScreenImageDialog(context, bitmap, caption));
                            });
                            return;
                        }
                    }
                }
            } catch (Exception ignored) {}

            mainHandler.post(() -> {
                iv.setImageResource(R.drawable.ic_intro_agent);
                iv.setColorFilter(0xFF38BDF8);
            });
        }).start();
    }

    public static void showFullScreenImageDialog(Context context, Bitmap bitmap, String title) {
        if (context == null || bitmap == null) return;
        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0xF00F1115);

        ImageView fullIv = new ImageView(context);
        fullIv.setImageBitmap(bitmap);
        fullIv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(fullIv, ivLp);

        if (title != null && !title.trim().isEmpty()) {
            TextView tvTitle = new TextView(context);
            tvTitle.setText(title);
            tvTitle.setTextColor(0xFFE2E8F0);
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tvTitle.setPadding(dpToPx(context, 16), dpToPx(context, 20), dpToPx(context, 60), dpToPx(context, 12));
            tvTitle.setBackgroundColor(0xAA000000);
            FrameLayout.LayoutParams tLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
            root.addView(tvTitle, tLp);
        }

        ImageView btnClose = new ImageView(context);
        btnClose.setImageResource(R.drawable.ic_close);
        btnClose.setColorFilter(0xFFFFFFFF);
        btnClose.setPadding(dpToPx(context, 12), dpToPx(context, 12), dpToPx(context, 12), dpToPx(context, 12));
        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(
                dpToPx(context, 48), dpToPx(context, 48), Gravity.TOP | Gravity.END);
        cLp.topMargin = dpToPx(context, 10);
        cLp.rightMargin = dpToPx(context, 10);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        root.addView(btnClose, cLp);

        dialog.setContentView(root);
        dialog.show();
    }

    private static View buildVideoView(Context context, JsonObject node) {
        String src = node.has("src") ? node.get("src").getAsString() : "";
        String poster = node.has("poster") ? node.get("poster").getAsString() : "";
        String caption = node.has("caption") ? node.get("caption").getAsString() : "Video Sinematik AI";

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dpToPx(context, 8);
        lp.bottomMargin = dpToPx(context, 8);
        layout.setLayoutParams(lp);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setColor(0xFF14171E);
        cardBg.setCornerRadius(dpToPx(context, 12));
        cardBg.setStroke(dpToPx(context, 1), 0xFF2A2E37);
        layout.setBackground(cardBg);
        layout.setPadding(dpToPx(context, 8), dpToPx(context, 8), dpToPx(context, 8), dpToPx(context, 8));

        // Frame container untuk thumbnail dan tombol play di tengah
        FrameLayout videoFrame = new FrameLayout(context);
        LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        videoFrame.setLayoutParams(frameLp);

        ImageView ivPoster = new ImageView(context);
        ivPoster.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivPoster.setAdjustViewBounds(true);
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        videoFrame.addView(ivPoster, ivLp);

        // Muat gambar poster thumbnail
        if (!poster.isEmpty()) {
            loadImageAsync(context, ivPoster, poster, "", caption);
        } else {
            ivPoster.setImageResource(R.drawable.ic_intro_agent);
            ivPoster.setColorFilter(0x6638BDF8);
        }

        // Overlay Tombol Putar (Play Button)
        LinearLayout playButtonPill = new LinearLayout(context);
        playButtonPill.setOrientation(LinearLayout.HORIZONTAL);
        playButtonPill.setGravity(Gravity.CENTER);
        GradientDrawable playBg = new GradientDrawable();
        playBg.setShape(GradientDrawable.RECTANGLE);
        playBg.setColor(0xCC0F172A);
        playBg.setCornerRadius(dpToPx(context, 20));
        playBg.setStroke(dpToPx(context, 1), 0xFFF3BA32);
        playButtonPill.setBackground(playBg);
        playButtonPill.setPadding(dpToPx(context, 14), dpToPx(context, 8), dpToPx(context, 16), dpToPx(context, 8));

        TextView tvPlay = new TextView(context);
        tvPlay.setText("▶  PUTAR VIDEO SINEMATIK");
        tvPlay.setTextColor(0xFFF3BA32);
        tvPlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvPlay.setTypeface(Typeface.DEFAULT_BOLD);
        playButtonPill.addView(tvPlay);

        FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        videoFrame.addView(playButtonPill, pbLp);

        // Klik untuk memutar video
        videoFrame.setOnClickListener(v -> openVideoPlayer(context, src, caption));
        layout.addView(videoFrame);

        if (!caption.isEmpty()) {
            TextView tvCap = new TextView(context);
            tvCap.setText(caption);
            tvCap.setTextColor(0xFF94A3B8);
            tvCap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvCap.setGravity(Gravity.CENTER);
            tvCap.setPadding(dpToPx(context, 6), dpToPx(context, 6), dpToPx(context, 6), dpToPx(context, 2));
            layout.addView(tvCap);
        }

        return layout;
    }

    private static void openVideoPlayer(Context context, String videoUrl, String title) {
        if (context == null || videoUrl == null || videoUrl.trim().isEmpty()) return;
        String fullUrl = videoUrl.trim();
        if (fullUrl.startsWith("/")) {
            com.molly.agent.api.MollyApiClient apiClient = com.molly.agent.api.MollyApiClient.getInstance(context);
            String baseUrl = apiClient != null ? apiClient.getServerUrl() : "";
            if (baseUrl.endsWith("/")) {
                fullUrl = baseUrl + fullUrl.substring(1);
            } else {
                fullUrl = baseUrl + fullUrl;
            }
        }

        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(android.net.Uri.parse(fullUrl), "video/mp4");
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                android.content.Intent webIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(fullUrl));
                webIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(webIntent);
            } catch (Exception ignored) {
                Toast.makeText(context, "Membuka video: " + fullUrl, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static View buildImageGroupView(Context context, JsonObject node) {
        HorizontalScrollView hsv = new HorizontalScrollView(context);
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.HORIZONTAL);

        if (node.has("children") && node.get("children").isJsonArray()) {
            JsonArray arr = node.getAsJsonArray("children");
            for (int i = 0; i < arr.size(); i++) {
                if (arr.get(i).isJsonObject()) {
                    View imgView = buildImageView(context, arr.get(i).getAsJsonObject());
                    if (imgView != null) {
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                dpToPx(context, 200), ViewGroup.LayoutParams.WRAP_CONTENT);
                        lp.setMarginEnd(dpToPx(context, 8));
                        lp.rightMargin = dpToPx(context, 8);
                        imgView.setLayoutParams(lp);
                        group.addView(imgView);
                    }
                }
            }
        }
        hsv.addView(group);
        return hsv;
    }

    private static View buildChartSummaryView(Context context, JsonObject node) {
        LinearLayout chartBox = new LinearLayout(context);
        chartBox.setOrientation(LinearLayout.VERTICAL);
        chartBox.setBackgroundResource(R.drawable.bg_widget_card);
        int p = dpToPx(context, 12);
        chartBox.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dpToPx(context, 6);
        lp.bottomMargin = dpToPx(context, 6);
        chartBox.setLayoutParams(lp);

        String label = node.has("label") ? node.get("label").getAsString() : "Grafik Visual";
        TextView tvTitle = new TextView(context);
        tvTitle.setText("📊 " + label);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        chartBox.addView(tvTitle);

        if (node.has("data") && node.get("data").isJsonArray()) {
            JsonArray dataArr = node.getAsJsonArray("data");
            String xAxis = node.has("xAxis") ? node.get("xAxis").getAsString() : "key";
            for (int i = 0; i < Math.min(dataArr.size(), 8); i++) {
                if (!dataArr.get(i).isJsonObject()) continue;
                JsonObject row = dataArr.get(i).getAsJsonObject();
                String xVal = row.has(xAxis) ? row.get(xAxis).getAsString() : "Data " + (i + 1);

                LinearLayout rowLayout = new LinearLayout(context);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                rowLayout.setGravity(Gravity.CENTER_VERTICAL);
                rowLayout.setPadding(0, dpToPx(context, 4), 0, dpToPx(context, 4));

                TextView tvX = new TextView(context);
                tvX.setText(xVal);
                tvX.setTextColor(0xFF9CA3AF);
                tvX.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                LinearLayout.LayoutParams xLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                tvX.setLayoutParams(xLp);
                rowLayout.addView(tvX);

                if (node.has("series") && node.get("series").isJsonArray()) {
                    JsonArray series = node.getAsJsonArray("series");
                    for (int s = 0; s < series.size(); s++) {
                        if (!series.get(s).isJsonObject()) continue;
                        String dKey = series.get(s).getAsJsonObject().get("dataKey").getAsString();
                        String val = row.has(dKey) ? row.get(dKey).getAsString() : "-";
                        TextView tvVal = new TextView(context);
                        tvVal.setText(val);
                        tvVal.setTextColor(0xFF38BDF8);
                        tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                        tvVal.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                        tvVal.setPadding(dpToPx(context, 6), 0, 0, 0);
                        rowLayout.addView(tvVal);
                    }
                }
                chartBox.addView(rowLayout);
            }
        }
        return chartBox;
    }

    // =========================================================================
    // UI UTILITIES: Tombol Pill Copy [menyalin] & Prose TextView
    // =========================================================================
    private static TextView createCopyPillButton(Context context, String label, String textToCopy) {
        TextView btn = new TextView(context);
        btn.setText(label);
        btn.setTextColor(0xFF9CA3AF);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xFF1E232B);
        gd.setCornerRadius(dpToPx(context, 6));
        gd.setStroke(dpToPx(context, 1), 0xFF374151);
        btn.setBackground(gd);

        int hPad = dpToPx(context, 8);
        int vPad = dpToPx(context, 3);
        btn.setPadding(hPad, vPad, hPad, vPad);

        btn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip = ClipData.newPlainText("Molly Text", textToCopy);
                cm.setPrimaryClip(clip);
                btn.setText("Tersalin ✓");
                btn.setTextColor(0xFF38BDF8);
                Toast.makeText(context, "Teks berhasil disalin ke papan klip", Toast.LENGTH_SHORT).show();
                btn.postDelayed(() -> {
                    btn.setText(label);
                    btn.setTextColor(0xFF9CA3AF);
                }, 2000);
            }
        });

        return btn;
    }

    private static TextView createProseTextView(Context context, String proseText) {
        TextView tv = new TextView(context);
        tv.setText(MarkdownHelper.render(context, proseText));
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setLineSpacing(dpToPx(context, 3), 1.0f);
        tv.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        tv.setLinkTextColor(0xFF38BDF8);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dpToPx(context, 6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static boolean checkHasTable(String text) {
        if (text == null || !text.contains("|")) return false;
        String[] lines = text.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length - 1; i++) {
            if (lines[i].contains("|") && MarkdownHelper.isTableDelimiterLine(lines[i + 1])) {
                return true;
            }
        }
        return false;
    }

    private static void appendSegmentViews(Context context, String textSegment, List<View> outViews) {
        if (textSegment == null || textSegment.trim().isEmpty()) return;

        String[] lines = textSegment.split("\\r?\\n", -1);
        List<String> curProse = new ArrayList<>();
        int i = 0;
        Pattern mdImgPattern = Pattern.compile("!\\[(.*?)\\]\\((.*?)\\)");

        while (i < lines.length) {
            String line = lines[i];

            // 1. Deteksi Tabel Markdown
            if (i + 1 < lines.length && line.contains("|") && MarkdownHelper.isTableDelimiterLine(lines[i + 1])) {
                if (!curProse.isEmpty()) {
                    String pText = joinLines(curProse).trim();
                    if (!pText.isEmpty()) {
                        outViews.add(createProseTextView(context, pText));
                    }
                    curProse.clear();
                }

                String headerLine = line;
                String delimLine = lines[i + 1];
                List<String> rowLines = new ArrayList<>();
                i += 2;
                while (i < lines.length && lines[i].contains("|") && !lines[i].trim().isEmpty()) {
                    rowLines.add(lines[i]);
                    i++;
                }

                View tableView = createTableWidgetView(context, headerLine, delimLine, rowLines);
                if (tableView != null) {
                    outViews.add(tableView);
                }
                continue;
            }

            // 2. Deteksi Gambar Markdown: ![alt](url)
            Matcher imgMatcher = mdImgPattern.matcher(line);
            if (imgMatcher.find()) {
                String beforeImg = line.substring(0, imgMatcher.start()).trim();
                String afterImg = line.substring(imgMatcher.end()).trim();
                if (!beforeImg.isEmpty()) {
                    curProse.add(beforeImg);
                }
                if (!curProse.isEmpty()) {
                    String pText = joinLines(curProse).trim();
                    if (!pText.isEmpty()) {
                        outViews.add(createProseTextView(context, pText));
                    }
                    curProse.clear();
                }

                String alt = imgMatcher.group(1);
                String src = imgMatcher.group(2);
                JsonObject imgNode = new JsonObject();
                imgNode.addProperty("type", "Image");
                imgNode.addProperty("src", src);
                imgNode.addProperty("alt", alt);
                imgNode.addProperty("caption", alt);
                View imgView = buildImageView(context, imgNode);
                if (imgView != null) {
                    outViews.add(imgView);
                }
                if (!afterImg.isEmpty()) {
                    curProse.add(afterImg);
                }
                i++;
                continue;
            }

            curProse.add(line);
            i++;
        }

        if (!curProse.isEmpty()) {
            String pText = joinLines(curProse).trim();
            if (!pText.isEmpty()) {
                outViews.add(createProseTextView(context, pText));
            }
        }
    }

    private static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i));
            if (i < lines.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    private static View createTableWidgetView(
            Context context,
            String headerLine,
            String delimLine,
            List<String> rowLines
    ) {
        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setHorizontalScrollBarEnabled(true);
        hsv.setScrollbarFadingEnabled(false);
        hsv.setBackgroundResource(R.drawable.bg_table_container);

        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hsvLp.topMargin = dpToPx(context, 8);
        hsvLp.bottomMargin = dpToPx(context, 8);
        hsv.setLayoutParams(hsvLp);

        TableLayout table = new TableLayout(context);
        table.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        table.setStretchAllColumns(false);

        List<String> headers = MarkdownHelper.parseCells(headerLine);
        List<String> delims = MarkdownHelper.parseCells(delimLine);
        int colCount = headers.size();

        int[] alignments = new int[colCount];
        for (int c = 0; c < colCount; c++) {
            String d = (c < delims.size()) ? delims.get(c).trim() : "";
            boolean leftColon = d.startsWith(":");
            boolean rightColon = d.endsWith(":");
            if (leftColon && rightColon) {
                alignments[c] = Gravity.CENTER;
            } else if (rightColon) {
                alignments[c] = Gravity.END;
            } else {
                alignments[c] = Gravity.START;
            }
        }

        // Header Row
        if (!headers.isEmpty()) {
            TableRow headerRow = new TableRow(context);
            headerRow.setBackgroundResource(R.drawable.bg_table_header);

            for (int c = 0; c < colCount; c++) {
                TextView tvH = new TextView(context);
                tvH.setText(MarkdownHelper.parseInline(headers.get(c)));
                tvH.setTypeface(Typeface.DEFAULT_BOLD);
                tvH.setTextColor(0xFF38BDF8); // Cyan Molly accent
                tvH.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tvH.setGravity(alignments[c] | Gravity.CENTER_VERTICAL);
                int hPad = dpToPx(context, 14);
                int vPad = dpToPx(context, 10);
                tvH.setPadding(hPad, vPad, hPad, vPad);
                tvH.setMinWidth(dpToPx(context, 70));
                headerRow.addView(tvH);
            }
            table.addView(headerRow);

            // Subtle divider bawah header
            View divider = new View(context);
            divider.setBackgroundColor(0xFF334155);
            TableLayout.LayoutParams divLp = new TableLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 1.2f));
            divider.setLayoutParams(divLp);
            table.addView(divider);
        }

        // Data Rows
        if (rowLines != null) {
            for (int r = 0; r < rowLines.size(); r++) {
                List<String> cells = MarkdownHelper.parseCells(rowLines.get(r));
                TableRow dataRow = new TableRow(context);
                int rowBg = (r % 2 == 0) ? 0xFF0F172A : 0xFF141E33;
                dataRow.setBackgroundColor(rowBg);

                for (int c = 0; c < colCount; c++) {
                    TextView tvCell = new TextView(context);
                    String cellVal = (c < cells.size()) ? cells.get(c) : "";
                    tvCell.setText(MarkdownHelper.parseInline(cellVal));
                    tvCell.setTextColor(0xFFE2E8F0);
                    tvCell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    tvCell.setGravity(alignments[c] | Gravity.CENTER_VERTICAL);
                    tvCell.setTextIsSelectable(true);
                    int hPad = dpToPx(context, 14);
                    int vPad = dpToPx(context, 9);
                    tvCell.setPadding(hPad, vPad, hPad, vPad);
                    tvCell.setMinWidth(dpToPx(context, 70));
                    dataRow.addView(tvCell);
                }
                table.addView(dataRow);

                // Row divider
                if (r < rowLines.size() - 1) {
                    View rowDiv = new View(context);
                    rowDiv.setBackgroundColor(0xFF1E293B);
                    TableLayout.LayoutParams rowDivLp = new TableLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 0.8f));
                    rowDiv.setLayoutParams(rowDivLp);
                    table.addView(rowDiv);
                }
            }
        }

        hsv.addView(table);
        return hsv;
    }

    private static int dpToPx(Context context, float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
