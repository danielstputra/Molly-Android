package com.molly.agent.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.molly.agent.R;
import com.molly.agent.model.ChatDisplayItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Pengontrol navigasi linimasa vertikal (Timeline Scrubber / Minimap) dan Floating Preview Card.
 * Menampilkan linimasa elegan sesuai desain modern:
 *   ^  (Caret atas: lompat ke segmen sebelumnya)
 *   |  (Track garis vertikal)
 *   •  (Dot non-aktif)
 *   ●  (Dot aktif: putih cerah membesar)
 *   •  (Dot non-aktif)
 *   v  (Caret bawah: lompat ke segmen berikutnya)
 *
 * Disertai Floating Preview Card interaktif:
 * - Menampilkan judul prompt tebal (User message)
 * - Cuplikan jawaban asisten (AI snippet)
 * - Nomor posisi segmen: "247 of 251"
 * - Waktu pengiriman pesan (Timestamp)
 * - Scrubbing real-time saat jari digeser di sepanjang track
 * - Auto-hide halus saat tidak ada interaksi
 */
public class ChatSegmentNavigatorHelper {

    public static class SegmentInfo {
        public final int adapterPosition;
        public final String title;
        public final String snippet;
        public final String timestamp;
        public final int segmentIndex;

        public SegmentInfo(int adapterPosition, String title, String snippet, String timestamp, int segmentIndex) {
            this.adapterPosition = adapterPosition;
            this.title = title;
            this.snippet = snippet;
            this.timestamp = timestamp;
            this.segmentIndex = segmentIndex;
        }
    }

    private final Activity activity;
    private final RecyclerView rvMessages;
    private final LinearLayoutManager layoutManager;
    private final View llSegmentNavigator;
    private final LinearLayout llSegmentNodes;
    private final View btnSegmentPrev;
    private final View btnSegmentNext;
    private final View btnScrollBottom;

    // Floating Preview Card Views
    private final View cardSegmentPreview;
    private final TextView tvPreviewTitle;
    private final TextView tvPreviewSnippet;
    private final TextView tvPreviewCounter;
    private final TextView tvPreviewTime;

    private final List<SegmentInfo> segments = new ArrayList<>();
    private final List<View> dotViews = new ArrayList<>();
    private int currentActiveSegment = 0;

    // Auto-Hide Handlers
    private static final long AUTO_HIDE_DELAY_MS = 2500;
    private static final long CARD_HIDE_DELAY_MS = 2000;
    private final Handler autoHideHandler = new Handler(Looper.getMainLooper());
    private boolean isScrubbing = false;

    private final Runnable autoHideRailRunnable = new Runnable() {
        @Override
        public void run() {
            if (llSegmentNavigator != null && llSegmentNavigator.getVisibility() == View.VISIBLE && !isScrubbing) {
                llSegmentNavigator.animate()
                        .alpha(0f)
                        .setDuration(350)
                        .withEndAction(() -> {
                            if (llSegmentNavigator.getAlpha() == 0f) {
                                llSegmentNavigator.setVisibility(View.GONE);
                            }
                        })
                        .start();
            }
        }
    };

    private final Runnable autoHideCardRunnable = new Runnable() {
        @Override
        public void run() {
            if (cardSegmentPreview != null && cardSegmentPreview.getVisibility() == View.VISIBLE && !isScrubbing) {
                cardSegmentPreview.animate()
                        .alpha(0f)
                        .scaleX(0.96f)
                        .scaleY(0.96f)
                        .setDuration(250)
                        .withEndAction(() -> {
                            if (cardSegmentPreview.getAlpha() == 0f) {
                                cardSegmentPreview.setVisibility(View.GONE);
                            }
                        })
                        .start();
            }
        }
    };

    public ChatSegmentNavigatorHelper(
            Activity activity,
            RecyclerView rvMessages,
            LinearLayoutManager layoutManager,
            View llSegmentNavigator,
            LinearLayout llSegmentNodes,
            View btnSegmentPrev,
            View btnSegmentNext,
            View btnScrollBottom,
            View cardSegmentPreview,
            TextView tvPreviewTitle,
            TextView tvPreviewSnippet,
            TextView tvPreviewCounter,
            TextView tvPreviewTime
    ) {
        this.activity = activity;
        this.rvMessages = rvMessages;
        this.layoutManager = layoutManager;
        this.llSegmentNavigator = llSegmentNavigator;
        this.llSegmentNodes = llSegmentNodes;
        this.btnSegmentPrev = btnSegmentPrev;
        this.btnSegmentNext = btnSegmentNext;
        this.btnScrollBottom = btnScrollBottom;
        this.cardSegmentPreview = cardSegmentPreview;
        this.tvPreviewTitle = tvPreviewTitle;
        this.tvPreviewSnippet = tvPreviewSnippet;
        this.tvPreviewCounter = tvPreviewCounter;
        this.tvPreviewTime = tvPreviewTime;

        initListeners();
    }

    private void initListeners() {
        if (btnScrollBottom != null) {
            btnScrollBottom.setOnClickListener(v -> {
                int total = (rvMessages.getAdapter() != null) ? rvMessages.getAdapter().getItemCount() : 0;
                if (total > 0) {
                    rvMessages.smoothScrollToPosition(total - 1);
                }
            });
        }

        if (btnSegmentPrev != null) {
            btnSegmentPrev.setOnClickListener(v -> {
                showNavigator();
                jumpToSegment(currentActiveSegment - 1, true);
            });
        }

        if (btnSegmentNext != null) {
            btnSegmentNext.setOnClickListener(v -> {
                showNavigator();
                jumpToSegment(currentActiveSegment + 1, true);
            });
        }

        if (rvMessages != null) {
            rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                    showNavigator();
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        scheduleAutoHide();
                    }
                }

                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (Math.abs(dy) > 2) {
                        showNavigator();
                    }
                    handleScrollEvent();
                }
            });

            rvMessages.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
                @Override
                public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                    showNavigator();
                    if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                        scheduleAutoHide();
                    }
                    return false;
                }
            });
        }

        // Interaksi sentuh dan scrubbing pada rail navigator
        if (llSegmentNavigator != null) {
            llSegmentNavigator.setOnTouchListener((v, event) -> {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    isScrubbing = true;
                    autoHideHandler.removeCallbacks(autoHideRailRunnable);
                    autoHideHandler.removeCallbacks(autoHideCardRunnable);
                    llSegmentNavigator.animate().cancel();
                    llSegmentNavigator.setAlpha(1f);

                    handleTouchScrub(event.getY());
                    return true;
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    isScrubbing = false;
                    scheduleAutoHide();
                    scheduleCardHide();
                    return true;
                }
                return false;
            });
        }

        // Menutup kartu pratinjau saat diklik
        if (cardSegmentPreview != null) {
            cardSegmentPreview.setOnClickListener(v -> {
                jumpToSegment(currentActiveSegment, false);
                scheduleCardHide();
            });
        }
    }

    private void handleTouchScrub(float touchY) {
        if (segments.isEmpty() || llSegmentNodes == null) return;

        int nodesHeight = llSegmentNodes.getHeight();
        if (nodesHeight <= 0) nodesHeight = 1;

        // Hitung indeks segmen terdekat berdasarkan koordinat Y sentuhan
        float clampedY = Math.max(0, Math.min(touchY - llSegmentNodes.getTop(), nodesHeight));
        float ratio = clampedY / (float) nodesHeight;
        int targetIndex = Math.round(ratio * (segments.size() - 1));
        targetIndex = Math.max(0, Math.min(targetIndex, segments.size() - 1));

        jumpToSegment(targetIndex, true);
    }

    public void showNavigator() {
        if (segments.size() < 2 || llSegmentNavigator == null) return;
        autoHideHandler.removeCallbacks(autoHideRailRunnable);
        llSegmentNavigator.animate().cancel();
        if (llSegmentNavigator.getVisibility() != View.VISIBLE) {
            llSegmentNavigator.setAlpha(0f);
            llSegmentNavigator.setVisibility(View.VISIBLE);
        }
        llSegmentNavigator.animate().alpha(1f).setDuration(200).start();
        scheduleAutoHide();
    }

    public void scheduleAutoHide() {
        autoHideHandler.removeCallbacks(autoHideRailRunnable);
        if (!isScrubbing && segments.size() >= 2) {
            autoHideHandler.postDelayed(autoHideRailRunnable, AUTO_HIDE_DELAY_MS);
        }
    }

    public void scheduleCardHide() {
        autoHideHandler.removeCallbacks(autoHideCardRunnable);
        if (!isScrubbing) {
            autoHideHandler.postDelayed(autoHideCardRunnable, CARD_HIDE_DELAY_MS);
        }
    }

    private void handleScrollEvent() {
        if (layoutManager == null || rvMessages.getAdapter() == null) return;

        int totalItems = rvMessages.getAdapter().getItemCount();
        int lastVisible = layoutManager.findLastVisibleItemPosition();

        // 1. Tampilkan / sembunyikan FAB Scroll to Bottom
        if (btnScrollBottom != null) {
            if (totalItems > 3 && lastVisible < totalItems - 2) {
                if (btnScrollBottom.getVisibility() != View.VISIBLE) {
                    btnScrollBottom.setAlpha(0f);
                    btnScrollBottom.setVisibility(View.VISIBLE);
                    btnScrollBottom.animate().alpha(1f).setDuration(200).start();
                }
            } else {
                if (btnScrollBottom.getVisibility() == View.VISIBLE) {
                    btnScrollBottom.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                        btnScrollBottom.setVisibility(View.GONE);
                    }).start();
                }
            }
        }

        // 2. Deteksi segmen aktif berdasarkan item pertama yang terlihat
        if (segments.isEmpty()) return;

        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int matchedSegment = 0;
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).adapterPosition <= firstVisible) {
                matchedSegment = i;
            } else {
                break;
            }
        }

        if (matchedSegment != currentActiveSegment) {
            currentActiveSegment = matchedSegment;
            updateActiveHighlight(currentActiveSegment);
        }
    }

    /**
     * Memperbarui daftar segmen percakapan (setiap giliran user pesan = 1 segmen).
     */
    public void updateSegments(List<ChatDisplayItem> items) {
        if (items == null && rvMessages != null && rvMessages.getAdapter() instanceof MessageAdapter) {
            items = ((MessageAdapter) rvMessages.getAdapter()).getItems();
        }
        if (items == null || items.isEmpty()) {
            segments.clear();
            autoHideHandler.removeCallbacks(autoHideRailRunnable);
            autoHideHandler.removeCallbacks(autoHideCardRunnable);
            if (llSegmentNavigator != null) llSegmentNavigator.setVisibility(View.GONE);
            if (cardSegmentPreview != null) cardSegmentPreview.setVisibility(View.GONE);
            return;
        }

        segments.clear();
        for (int i = 0; i < items.size(); i++) {
            ChatDisplayItem item = items.get(i);
            if (item != null && item.getItemType() == ChatDisplayItem.TYPE_USER) {
                String title = (item.getText() != null) ? item.getText().trim() : "";
                String timestamp = (item.getTimestamp() != null) ? item.getTimestamp() : "";

                // Cari jawaban asisten berikutnya untuk cuplikan respons
                String snippet = "";
                for (int j = i + 1; j < items.size(); j++) {
                    ChatDisplayItem next = items.get(j);
                    if (next != null && next.getItemType() == ChatDisplayItem.TYPE_AGENT) {
                        snippet = cleanSnippet(next.getText());
                        break;
                    }
                }

                segments.add(new SegmentInfo(i, title, snippet, timestamp, segments.size()));
            }
        }

        if (segments.size() < 2) {
            autoHideHandler.removeCallbacks(autoHideRailRunnable);
            autoHideHandler.removeCallbacks(autoHideCardRunnable);
            if (llSegmentNavigator != null) llSegmentNavigator.setVisibility(View.GONE);
            if (cardSegmentPreview != null) cardSegmentPreview.setVisibility(View.GONE);
            return;
        }

        rebuildTimelineUi();
        showNavigator();
    }

    private void rebuildTimelineUi() {
        if (llSegmentNodes == null) return;
        llSegmentNodes.removeAllViews();
        dotViews.clear();

        float density = activity.getResources().getDisplayMetrics().density;
        int colWidth = (int) (18 * density);
        int dotSizeNormal = (int) (5 * density);
        int marginV = (int) (3 * density);

        // Jika jumlah segmen banyak (>40), rapatkan margin agar tetap fit di layar
        if (segments.size() > 40) {
            marginV = (int) (1.5f * density);
            dotSizeNormal = (int) (4 * density);
        }

        for (int i = 0; i < segments.size(); i++) {
            final int segIndex = i;

            FrameLayout dotContainer = new FrameLayout(activity);
            LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(colWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            containerLp.setMargins(0, marginV, 0, marginV);
            dotContainer.setLayoutParams(containerLp);
            dotContainer.setClickable(true);
            dotContainer.setFocusable(true);

            View dot = new View(activity);
            FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dotSizeNormal, dotSizeNormal);
            dotLp.gravity = Gravity.CENTER;
            dot.setLayoutParams(dotLp);
            dot.setBackgroundResource(R.drawable.bg_timeline_dot_inactive);

            dotContainer.addView(dot);
            dotViews.add(dot);

            dotContainer.setOnClickListener(v -> {
                showNavigator();
                jumpToSegment(segIndex, true);
            });

            llSegmentNodes.addView(dotContainer);
        }

        if (currentActiveSegment >= segments.size()) {
            currentActiveSegment = segments.size() - 1;
        }
        updateActiveHighlight(currentActiveSegment);
    }

    public void jumpToSegment(int segIndex, boolean showPreviewCard) {
        if (segIndex < 0 || segIndex >= segments.size()) return;
        currentActiveSegment = segIndex;
        SegmentInfo seg = segments.get(segIndex);

        if (layoutManager != null) {
            layoutManager.scrollToPositionWithOffset(seg.adapterPosition, 0);
        }
        updateActiveHighlight(currentActiveSegment);
        showNavigator();
        scheduleAutoHide();

        if (showPreviewCard) {
            displayPreviewCard(seg);
        }
    }

    private void displayPreviewCard(SegmentInfo seg) {
        if (cardSegmentPreview == null) return;

        autoHideHandler.removeCallbacks(autoHideCardRunnable);

        if (tvPreviewTitle != null) {
            tvPreviewTitle.setText(seg.title.isEmpty() ? activity.getString(R.string.app_name) : seg.title);
        }

        if (tvPreviewSnippet != null) {
            if (seg.snippet.isEmpty()) {
                tvPreviewSnippet.setVisibility(View.GONE);
            } else {
                tvPreviewSnippet.setVisibility(View.VISIBLE);
                tvPreviewSnippet.setText(seg.snippet);
            }
        }

        if (tvPreviewCounter != null) {
            tvPreviewCounter.setText((seg.segmentIndex + 1) + " of " + segments.size());
        }

        if (tvPreviewTime != null) {
            tvPreviewTime.setText(seg.timestamp);
        }

        // Posisikan kartu mengambang sejajar secara vertikal dengan dot yang disentuh
        float density = activity.getResources().getDisplayMetrics().density;
        if (llSegmentNavigator != null && seg.segmentIndex < dotViews.size()) {
            View dotView = dotViews.get(seg.segmentIndex);
            int[] dotLocation = new int[2];
            dotView.getLocationOnScreen(dotLocation);

            int[] parentLocation = new int[2];
            View parent = (View) cardSegmentPreview.getParent();
            if (parent != null) {
                parent.getLocationOnScreen(parentLocation);
            }

            int targetY = dotLocation[1] - parentLocation[1] - (int) (40 * density);
            int maxY = (parent != null) ? parent.getHeight() - (int) (160 * density) : (int) (500 * density);
            int minY = (int) (20 * density);
            int clampedY = Math.max(minY, Math.min(targetY, maxY));

            cardSegmentPreview.setY(clampedY);
        }

        if (cardSegmentPreview.getVisibility() != View.VISIBLE) {
            cardSegmentPreview.setAlpha(0f);
            cardSegmentPreview.setScaleX(0.95f);
            cardSegmentPreview.setScaleY(0.95f);
            cardSegmentPreview.setVisibility(View.VISIBLE);
        }

        cardSegmentPreview.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(160)
                .start();

        scheduleCardHide();
    }

    private void updateActiveHighlight(int activeIndex) {
        float density = activity.getResources().getDisplayMetrics().density;
        int activeSize = (int) (8 * density);
        int inactiveSize = (int) (segments.size() > 40 ? 4 * density : 5 * density);

        for (int i = 0; i < dotViews.size(); i++) {
            View dot = dotViews.get(i);
            boolean isActive = (i == activeIndex);

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) dot.getLayoutParams();
            if (lp != null) {
                int size = isActive ? activeSize : inactiveSize;
                lp.width = size;
                lp.height = size;
                dot.setLayoutParams(lp);
            }

            if (isActive) {
                dot.setBackgroundResource(R.drawable.bg_timeline_dot_active);
            } else {
                dot.setBackgroundResource(R.drawable.bg_timeline_dot_inactive);
            }
        }

        if (btnSegmentPrev != null) {
            btnSegmentPrev.setAlpha(activeIndex > 0 ? 1f : 0.35f);
            btnSegmentPrev.setEnabled(activeIndex > 0);
        }

        if (btnSegmentNext != null) {
            btnSegmentNext.setAlpha(activeIndex < segments.size() - 1 ? 1f : 0.35f);
            btnSegmentNext.setEnabled(activeIndex < segments.size() - 1);
        }
    }

    private String cleanSnippet(String raw) {
        if (raw == null) return "";
        String clean = raw.replaceAll("```[\\s\\S]*?```", "")
                .replaceAll("<[^>]*>", "")
                .replaceAll("[#*`_~>\\[\\]]", "")
                .replace("\r", "")
                .replaceAll("\n+", " ")
                .trim();
        if (clean.length() > 140) {
            clean = clean.substring(0, 140) + "...";
        }
        return clean;
    }
}
