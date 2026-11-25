package com.molly.agent.model;

import java.util.HashMap;
import java.util.Map;

public class ToolNameHelper {
    private static final Map<String, String> TOOL_NAMES = new HashMap<>();

    static {
        TOOL_NAMES.put("search_thread_messages", "Cari pesan sejarah");
        TOOL_NAMES.put("search_messages", "Cari pesan sejarah");
        TOOL_NAMES.put("grep", "Cari isi file");
        TOOL_NAMES.put("find_files", "Cari file");
        TOOL_NAMES.put("glob", "Cari file (Glob)");
        TOOL_NAMES.put("read_file", "Baca file");
        TOOL_NAMES.put("write_file", "Tulis file");
        TOOL_NAMES.put("edit_file", "Sunting file");
        TOOL_NAMES.put("replace", "Sunting file");
        TOOL_NAMES.put("bash", "Jalankan Perintah Shell");
        TOOL_NAMES.put("cmd", "Jalankan Perintah Terminal");
        TOOL_NAMES.put("execute", "Jalankan Perintah Shell");
        TOOL_NAMES.put("run_shell_command", "Jalankan Perintah Shell");
        TOOL_NAMES.put("web_search", "Pencarian Web");
        TOOL_NAMES.put("tavily_search", "Pencarian Web Tavily");
        TOOL_NAMES.put("doubao_search", "Pencarian Web");
        TOOL_NAMES.put("web_fetch", "Pengikisan Web");
        TOOL_NAMES.put("mysql_query", "Jalankan Kueri SQL");
        TOOL_NAMES.put("mysql_list_tables", "Lihat Tabel Database");
        TOOL_NAMES.put("mysql_describe_table", "Lihat Struktur Tabel");
        TOOL_NAMES.put("ls", "Daftar Direktori");
        TOOL_NAMES.put("list_directory", "Daftar Direktori");
        TOOL_NAMES.put("query_kb", "Cari Basis Pengetahuan");
        TOOL_NAMES.put("find_kb_document", "Temukan Dokumen KB");
        TOOL_NAMES.put("open_kb_document", "Buka Dokumen KB");
        TOOL_NAMES.put("list_kbs", "Lihat Daftar Basis Pengetahuan");
        TOOL_NAMES.put("ask_user_question", "Pertanyaan Pengguna");
        TOOL_NAMES.put("install_skill", "Instalasi Skill Agen");
        TOOL_NAMES.put("calculator", "Kalkulator Cerdas");
        TOOL_NAMES.put("text_to_img_qwen_image", "Hasilkan Gambar AI");
        TOOL_NAMES.put("create_session", "Buat Sesi Kolaborasi");
        TOOL_NAMES.put("send_message", "Kirim Pesan Kolaborasi");
        TOOL_NAMES.put("submit_input", "Kirimkan Pekerjaan");
        TOOL_NAMES.put("remember_memory", "Perbarui Memori Sesi");
    }

    public static String getDisplayName(String toolId) {
        if (toolId == null) return "Alat Sistem";
        String lower = toolId.toLowerCase();
        if (TOOL_NAMES.containsKey(lower)) {
            return TOOL_NAMES.get(lower);
        }
        return "Alat: " + toolId;
    }
}
