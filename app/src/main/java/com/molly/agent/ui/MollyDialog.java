package com.molly.agent.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.molly.agent.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Standard Dialog Helper untuk aplikasi Molly Android.
 * Mengadopsi tata letak, background, tipografi, dan estetika yang 100% seragam dengan
 * modal "Pilih Model AI" (ModelSelectorDialogHelper).
 */
public class MollyDialog {

    public interface OnClickListener {
        void onClick(DialogInterface dialog, int which);
    }

    public interface OnSingleChoiceClickListener {
        void onClick(DialogInterface dialog, int which);
    }

    public static class Builder {
        private final Context context;
        private CharSequence title;
        private CharSequence message;
        private View customView;
        private boolean cancelable = true;

        private CharSequence positiveText;
        private OnClickListener positiveListener;
        private boolean isDestructive = false;

        private CharSequence negativeText;
        private OnClickListener negativeListener;

        private CharSequence[] listItems;
        private OnClickListener listListener;

        private CharSequence[] singleChoiceItems;
        private int checkedItemIndex = -1;
        private OnSingleChoiceClickListener singleChoiceListener;

        private CharSequence neutralText;
        private OnClickListener neutralListener;

        private DialogInterface.OnDismissListener dismissListener;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setTitle(CharSequence title) {
            this.title = title;
            return this;
        }

        public Builder setTitle(int titleResId) {
            this.title = context.getString(titleResId);
            return this;
        }

        public Builder setMessage(CharSequence message) {
            this.message = message;
            return this;
        }

        public Builder setMessage(int messageResId) {
            this.message = context.getString(messageResId);
            return this;
        }

        public Builder setView(View view) {
            this.customView = view;
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        public Builder setPositiveButton(CharSequence text, OnClickListener listener) {
            this.positiveText = text;
            this.positiveListener = listener;
            this.isDestructive = false;
            return this;
        }

        public Builder setDestructiveButton(CharSequence text, OnClickListener listener) {
            this.positiveText = text;
            this.positiveListener = listener;
            this.isDestructive = true;
            return this;
        }

        public Builder setNegativeButton(CharSequence text, OnClickListener listener) {
            this.negativeText = text;
            this.negativeListener = listener;
            return this;
        }

        public Builder setNeutralButton(CharSequence text, OnClickListener listener) {
            this.neutralText = text;
            this.neutralListener = listener;
            return this;
        }

        public Builder setItems(CharSequence[] items, OnClickListener listener) {
            this.listItems = items;
            this.listListener = listener;
            return this;
        }

        public Builder setSingleChoiceItems(CharSequence[] items, int checkedItem, OnSingleChoiceClickListener listener) {
            this.singleChoiceItems = items;
            this.checkedItemIndex = checkedItem;
            this.singleChoiceListener = listener;
            return this;
        }

        public Builder setOnDismissListener(DialogInterface.OnDismissListener listener) {
            this.dismissListener = listener;
            return this;
        }

        public Dialog create() {
            final Dialog dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCancelable(cancelable);

            View root = LayoutInflater.from(context).inflate(R.layout.dialog_standard_template, null);
            dialog.setContentView(root);

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92);
                dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }

            TextView tvTitle = root.findViewById(R.id.tvStandardDialogTitle);
            ImageView btnClose = root.findViewById(R.id.btnStandardDialogClose);
            TextView tvMessage = root.findViewById(R.id.tvStandardDialogMessage);
            FrameLayout flContent = root.findViewById(R.id.flStandardDialogContent);
            View vButtonDivider = root.findViewById(R.id.vStandardDialogButtonDivider);
            LinearLayout llButtons = root.findViewById(R.id.llStandardDialogButtons);
            TextView btnNeutral = root.findViewById(R.id.btnStandardDialogNeutral);
            TextView btnNegative = root.findViewById(R.id.btnStandardDialogNegative);
            TextView btnPositive = root.findViewById(R.id.btnStandardDialogPositive);

            // Set Title & Close
            if (title != null && title.length() > 0) {
                tvTitle.setText(title);
            } else {
                tvTitle.setText("");
            }

            btnClose.setOnClickListener(v -> dialog.dismiss());

            // Set Message
            if (message != null && message.length() > 0) {
                tvMessage.setText(message);
                tvMessage.setVisibility(View.VISIBLE);
            } else {
                tvMessage.setVisibility(View.GONE);
            }

            // Set Regular List Items (seperti "Tambah Konten / Lampiran")
            if (listItems != null && listItems.length > 0) {
                flContent.removeAllViews();
                ScrollView sv = new ScrollView(context);
                sv.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(context, Math.min(listItems.length * 52, 340))));
                sv.setOverScrollMode(View.OVER_SCROLL_NEVER);

                LinearLayout listContainer = new LinearLayout(context);
                listContainer.setOrientation(LinearLayout.VERTICAL);
                listContainer.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                for (int i = 0; i < listItems.length; i++) {
                    final int index = i;
                    CharSequence itemText = listItems[i];

                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setBackgroundResource(R.drawable.bg_code_block);
                    row.setBackgroundTintList(context.getColorStateList(R.color.card_dark));
                    row.setClickable(true);
                    row.setFocusable(true);
                    int padH = dpToPx(context, 14);
                    int padV = dpToPx(context, 12);
                    row.setPadding(padH, padV, padH, padV);

                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rowLp.topMargin = dpToPx(context, 3);
                    rowLp.bottomMargin = dpToPx(context, 3);
                    row.setLayoutParams(rowLp);

                    TextView tvName = new TextView(context);
                    tvName.setText(itemText);
                    tvName.setTextColor(0xFFFFFFFF);
                    tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
                    LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                    tvName.setLayoutParams(nameLp);
                    row.addView(tvName);

                    row.setOnClickListener(v -> {
                        dialog.dismiss();
                        if (listListener != null) {
                            listListener.onClick(dialog, index);
                        }
                    });

                    listContainer.addView(row);
                }

                sv.addView(listContainer);
                flContent.addView(sv);
                flContent.setVisibility(View.VISIBLE);
            }
            // Set Single Choice Items (seperti "Pilih Agent Molly")
            else if (singleChoiceItems != null && singleChoiceItems.length > 0) {
                flContent.removeAllViews();
                ScrollView sv = new ScrollView(context);
                sv.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(context, Math.min(singleChoiceItems.length * 48, 260))));
                sv.setOverScrollMode(View.OVER_SCROLL_NEVER);

                LinearLayout listContainer = new LinearLayout(context);
                listContainer.setOrientation(LinearLayout.VERTICAL);
                listContainer.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                List<View> itemViews = new ArrayList<>();
                for (int i = 0; i < singleChoiceItems.length; i++) {
                    final int index = i;
                    CharSequence itemText = singleChoiceItems[i];

                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setBackgroundResource(R.drawable.bg_code_block);
                    row.setBackgroundTintList(context.getColorStateList(R.color.card_dark));
                    row.setClickable(true);
                    row.setFocusable(true);
                    int padH = dpToPx(context, 12);
                    int padV = dpToPx(context, 10);
                    row.setPadding(padH, padV, padH, padV);

                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rowLp.topMargin = dpToPx(context, 3);
                    rowLp.bottomMargin = dpToPx(context, 3);
                    row.setLayoutParams(rowLp);

                    TextView tvName = new TextView(context);
                    tvName.setText(itemText);
                    tvName.setTextColor(0xFFFFFFFF);
                    tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
                    LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                    tvName.setLayoutParams(nameLp);
                    row.addView(tvName);

                    TextView tvCheck = new TextView(context);
                    tvCheck.setText("✓");
                    tvCheck.setTextColor(0xFFF3BA32);
                    tvCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    tvCheck.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                    tvCheck.setVisibility(checkedItemIndex == i ? View.VISIBLE : View.GONE);
                    row.addView(tvCheck);

                    row.setOnClickListener(v -> {
                        if (singleChoiceListener != null) {
                            singleChoiceListener.onClick(dialog, index);
                        }
                    });

                    itemViews.add(row);
                    listContainer.addView(row);
                }

                sv.addView(listContainer);
                flContent.addView(sv);
                flContent.setVisibility(View.VISIBLE);
            }
            // Set Custom View
            else if (customView != null) {
                flContent.removeAllViews();
                if (customView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) customView.getParent()).removeView(customView);
                }
                flContent.addView(customView);
                flContent.setVisibility(View.VISIBLE);
            } else {
                flContent.setVisibility(View.GONE);
            }

            // Set Buttons
            boolean hasButtons = (positiveText != null || negativeText != null || neutralText != null);
            if (hasButtons) {
                vButtonDivider.setVisibility(View.VISIBLE);
                llButtons.setVisibility(View.VISIBLE);

                if (neutralText != null) {
                    btnNeutral.setText(neutralText);
                    btnNeutral.setVisibility(View.VISIBLE);
                    btnNeutral.setOnClickListener(v -> {
                        dialog.dismiss();
                        if (neutralListener != null) {
                            neutralListener.onClick(dialog, DialogInterface.BUTTON_NEUTRAL);
                        }
                    });
                } else {
                    btnNeutral.setVisibility(View.GONE);
                }

                if (negativeText != null) {
                    btnNegative.setText(negativeText);
                    btnNegative.setVisibility(View.VISIBLE);
                    btnNegative.setOnClickListener(v -> {
                        dialog.dismiss();
                        if (negativeListener != null) {
                            negativeListener.onClick(dialog, DialogInterface.BUTTON_NEGATIVE);
                        }
                    });
                } else {
                    btnNegative.setVisibility(View.GONE);
                }

                if (positiveText != null) {
                    btnPositive.setText(positiveText);
                    btnPositive.setVisibility(View.VISIBLE);
                    if (isDestructive) {
                        btnPositive.setBackgroundResource(R.drawable.bg_button_destructive);
                    } else {
                        btnPositive.setBackgroundResource(R.drawable.bg_widget_submit_btn);
                    }
                    btnPositive.setOnClickListener(v -> {
                        dialog.dismiss();
                        if (positiveListener != null) {
                            positiveListener.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                        }
                    });
                } else {
                    btnPositive.setVisibility(View.GONE);
                }
            } else {
                vButtonDivider.setVisibility(View.GONE);
                llButtons.setVisibility(View.GONE);
            }

            if (dismissListener != null) {
                dialog.setOnDismissListener(dismissListener);
            }

            return dialog;
        }

        public Dialog show() {
            Dialog d = create();
            d.show();
            return d;
        }

        private static int dpToPx(Context context, int dp) {
            return (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
        }
    }
}
