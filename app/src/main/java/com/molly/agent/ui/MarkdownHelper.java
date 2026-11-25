package com.molly.agent.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renderer Markdown native untuk Molly Agent Android.
 * Menangani secara elegan dan presisi:
 * 1. Headings (#, ##, ###, ####, dll)
 * 2. Numbered lists (1. 2. 3. paragraph indentation)
 * 3. Bullet lists (*, -, +)
 * 4. Fenced code blocks (```language ... ```)
 * 5. Inline code (`code`)
 * 6. Bold (**) & Italic (*) & Bold-Italic (***)
 * 7. Blockquotes (>)
 * 8. Strikethrough (~~)
 * 9. Links ([title](url))
 * 10. Horizontal rule (---, ***)
 */
public class MarkdownHelper {

    // Palet warna yang selaras dengan tema gelap Molly Agent
    public static final int COLOR_GOLD = 0xFFF3BA32;
    public static final int COLOR_CYAN = 0xFF38BDF8;
    public static final int COLOR_CODE_BG = 0xFF14171E;
    public static final int COLOR_CODE_TEXT = 0xFFE2E8F0;
    public static final int COLOR_INLINE_CODE_BG = 0xFF272C35;
    public static final int COLOR_INLINE_CODE_TEXT = 0xFFF3BA32;
    public static final int COLOR_MUTED = 0xFF94A3B8;
    public static final int COLOR_BORDER = 0xFF34383D;
    public static final int COLOR_TEXT_WHITE = 0xFFF5F7F7;

    // Pattern untuk inline markdown
    // Group 1-2: `code`
    // Group 3-5: [link](url)
    // Group 6-7: ***bold-italic***
    // Group 8-9: **bold**
    // Group 10-11: __bold__
    // Group 12-13: *italic*
    // Group 14-15: ~~strikethrough~~
    private static final Pattern INLINE_PATTERN = Pattern.compile(
            "(`([^`\\n]+)`)" +
            "|(\\[([^\\]\\n]+)\\]\\(([^)\\s]+)\\))" +
            "|(\\*{3}([^*\\n]+)\\*{3})" +
            "|(\\*{2}([^*\\n]+)\\*{2})" +
            "|(__([^_\\n]+)__)" +
            "|(\\*(?!\\s)([^*\\n]+?)(?<!\\s)\\*)" +
            "|(~~([^~\\n]+)~~)"
    );

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern NUMBERED_LIST_PATTERN = Pattern.compile("^(\\d+)\\.\\s+(.*)$");
    private static final Pattern BULLET_LIST_PATTERN = Pattern.compile("^[*\\-+]\\s+(.*)$");
    private static final Pattern QUOTE_PATTERN = Pattern.compile("^>\\s*(.*)$");
    private static final Pattern HR_PATTERN = Pattern.compile("^(\\*{3,}|-{3,}|_{3,})$");
    private static final Pattern CODE_BLOCK_START = Pattern.compile("^```([a-zA-Z0-9_-]*)$");

    public static CharSequence render(Context context, String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }

        String[] lines = rawText.split("\\r?\\n", -1);
        SpannableStringBuilder builder = new SpannableStringBuilder();

        boolean inCodeBlock = false;
        String codeBlockLang = "";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            // 1. Cek pembuka / penutup Code Block (```)
            Matcher codeStartMatcher = CODE_BLOCK_START.matcher(trimmed);
            if (codeStartMatcher.matches()) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    codeBlockLang = codeStartMatcher.group(1);
                    if (codeBlockLang != null && !codeBlockLang.isEmpty()) {
                        int startLang = builder.length();
                        builder.append("  [ ").append(codeBlockLang.toUpperCase()).append(" ]\n");
                        int endLang = builder.length();
                        builder.setSpan(new ForegroundColorSpan(COLOR_GOLD), startLang, endLang, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        builder.setSpan(new RelativeSizeSpan(0.80f), startLang, endLang, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        builder.setSpan(new StyleSpan(Typeface.BOLD), startLang, endLang, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        builder.setSpan(new BackgroundColorSpan(COLOR_CODE_BG), startLang, endLang, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                } else {
                    inCodeBlock = false;
                    codeBlockLang = "";
                }
                continue;
            }

            // Jika sedang di dalam Code Block
            if (inCodeBlock) {
                int start = builder.length();
                builder.append("  ").append(line);
                if (i < lines.length - 1) {
                    builder.append("\n");
                }
                int end = builder.length();

                builder.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new BackgroundColorSpan(COLOR_CODE_BG), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(COLOR_CODE_TEXT), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new RelativeSizeSpan(0.92f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            // 2. Cek Horizontal Rule (---, ***, ___)
            if (HR_PATTERN.matcher(trimmed).matches()) {
                int start = builder.length();
                builder.append("────────────────────────");
                if (i < lines.length - 1) builder.append("\n");
                int end = builder.length();
                builder.setSpan(new ForegroundColorSpan(COLOR_BORDER), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new RelativeSizeSpan(0.75f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            // 3. Cek Headings (#, ##, ###, ####, dll)
            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                String hashes = headingMatcher.group(1);
                String content = headingMatcher.group(2);
                int level = hashes != null ? hashes.length() : 1;

                int start = builder.length();
                CharSequence parsedContent = parseInline(content);
                builder.append(parsedContent);
                if (i < lines.length - 1) builder.append("\n");
                int end = builder.length();

                // Ukuran dan styling heading
                float sizeMultiplier;
                switch (level) {
                    case 1: sizeMultiplier = 1.35f; break;
                    case 2: sizeMultiplier = 1.22f; break;
                    case 3: sizeMultiplier = 1.14f; break;
                    default: sizeMultiplier = 1.05f; break;
                }

                builder.setSpan(new RelativeSizeSpan(sizeMultiplier), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(COLOR_GOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            // 4. Cek Blockquote (> quote)
            Matcher quoteMatcher = QUOTE_PATTERN.matcher(line);
            if (quoteMatcher.matches()) {
                String content = quoteMatcher.group(1);
                int start = builder.length();
                CharSequence parsedContent = parseInline(content);
                builder.append(parsedContent);
                if (i < lines.length - 1) builder.append("\n");
                int end = builder.length();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    builder.setSpan(new QuoteSpan(COLOR_GOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    builder.setSpan(new QuoteSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                builder.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(COLOR_MUTED), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            // 5. Cek Numbered List (1. , 2. , dst)
            Matcher numListMatcher = NUMBERED_LIST_PATTERN.matcher(line);
            if (numListMatcher.matches()) {
                String num = numListMatcher.group(1);
                String content = numListMatcher.group(2);

                int start = builder.length();
                String prefix = num + ". ";
                builder.append(prefix);
                int prefixEnd = builder.length();

                builder.setSpan(new StyleSpan(Typeface.BOLD), start, prefixEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(COLOR_GOLD), start, prefixEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                CharSequence parsedContent = parseInline(content);
                builder.append(parsedContent);
                if (i < lines.length - 1) builder.append("\n");
                int end = builder.length();

                builder.setSpan(new LeadingMarginSpan.Standard(18, 44), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            // 6. Cek Bullet List (*, -, +)
            Matcher bulletMatcher = BULLET_LIST_PATTERN.matcher(line);
            if (bulletMatcher.matches()) {
                String content = bulletMatcher.group(1);

                int start = builder.length();
                String prefix = "•  ";
                builder.append(prefix);
                int prefixEnd = builder.length();

                builder.setSpan(new StyleSpan(Typeface.BOLD), start, prefixEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(COLOR_GOLD), start, prefixEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                CharSequence parsedContent = parseInline(content);
                builder.append(parsedContent);
                if (i < lines.length - 1) builder.append("\n");
                int end = builder.length();

                builder.setSpan(new LeadingMarginSpan.Standard(18, 40), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            // 7. Paragraf / Teks Biasa
            int start = builder.length();
            CharSequence parsedContent = parseInline(line);
            builder.append(parsedContent);
            if (i < lines.length - 1) {
                builder.append("\n");
            }
        }

        return builder;
    }

    /**
     * Mem-parse format teks inline:
     * - `inline code`
     * - [link](url)
     * - ***bold italic***
     * - **bold** / __bold__
     * - *italic*
     * - ~~strikethrough~~
     */
    public static SpannableStringBuilder parseInline(String text) {
        if (text == null || text.isEmpty()) {
            return new SpannableStringBuilder("");
        }

        SpannableStringBuilder sb = new SpannableStringBuilder();
        Matcher matcher = INLINE_PATTERN.matcher(text);
        int lastIndex = 0;

        while (matcher.find()) {
            // Teks biasa sebelum kecocokan
            if (matcher.start() > lastIndex) {
                sb.append(text.substring(lastIndex, matcher.start()));
            }

            // Group 1 & 2: `code`
            if (matcher.group(1) != null) {
                String codeContent = matcher.group(2);
                int start = sb.length();
                sb.append(" ").append(codeContent).append(" ");
                int end = sb.length();

                sb.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new BackgroundColorSpan(COLOR_INLINE_CODE_BG), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(COLOR_INLINE_CODE_TEXT), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new RelativeSizeSpan(0.92f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // Group 3, 4, 5: [title](url)
            else if (matcher.group(3) != null) {
                String title = matcher.group(4);
                String url = matcher.group(5);
                int start = sb.length();
                sb.append(title);
                int end = sb.length();

                if (url != null && !url.isEmpty()) {
                    sb.setSpan(new URLSpan(url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                sb.setSpan(new ForegroundColorSpan(COLOR_CYAN), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // Group 6 & 7: ***bold-italic***
            else if (matcher.group(6) != null) {
                String content = matcher.group(7);
                int start = sb.length();
                sb.append(content);
                int end = sb.length();
                sb.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // Group 8 & 9: **bold**
            else if (matcher.group(8) != null) {
                String content = matcher.group(9);
                int start = sb.length();
                sb.append(content);
                int end = sb.length();
                sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // Group 10 & 11: __bold__
            else if (matcher.group(10) != null) {
                String content = matcher.group(11);
                int start = sb.length();
                sb.append(content);
                int end = sb.length();
                sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // Group 12 & 13: *italic*
            else if (matcher.group(12) != null) {
                String content = matcher.group(13);
                int start = sb.length();
                sb.append(content);
                int end = sb.length();
                sb.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // Group 14 & 15: ~~strikethrough~~
            else if (matcher.group(14) != null) {
                String content = matcher.group(15);
                int start = sb.length();
                sb.append(content);
                int end = sb.length();
                sb.setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            lastIndex = matcher.end();
        }

        // Sisa teks setelah kecocokan terakhir
        if (lastIndex < text.length()) {
            sb.append(text.substring(lastIndex));
        }

        return sb;
    }
}
