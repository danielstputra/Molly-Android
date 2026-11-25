package com.molly.agent.model;

import java.util.HashMap;
import java.util.Map;

public class ToolNameHelper {
    private static final Map<String, String> TOOL_NAMES = new HashMap<>();

    static {
        TOOL_NAMES.put("search_thread_messages", "Cari pesan sejarah");
        TOOL_NAMES.put("read_thread_messages", "Baca pesan sejarah");
        TOOL_NAMES.put("search_messages", "Cari pesan sejarah");
        TOOL_NAMES.put("grep", "Cari isi file");
        TOOL_NAMES.put("search_file_content", "Cari isi file");
        TOOL_NAMES.put("find_files", "Cari file");
        TOOL_NAMES.put("glob", "Cocokkan jalur file");
        TOOL_NAMES.put("read_file", "Membaca berkas");
        TOOL_NAMES.put("write_file", "Tulis berkas");
        TOOL_NAMES.put("edit_file", "Sunting berkas");
        TOOL_NAMES.put("replace", "Sunting berkas");
        TOOL_NAMES.put("bash", "Jalankan perintah");
        TOOL_NAMES.put("cmd", "Jalankan perintah");
        TOOL_NAMES.put("execute", "Jalankan perintah");
        TOOL_NAMES.put("run_shell_command", "Jalankan perintah");
        TOOL_NAMES.put("web_search", "Pencarian web");
        TOOL_NAMES.put("tavily_search", "Pencarian web");
        TOOL_NAMES.put("doubao_search", "Pencarian web");
        TOOL_NAMES.put("web_fetch", "Pengikisan web");
        TOOL_NAMES.put("mysql_query", "Jalankan kueri SQL");
        TOOL_NAMES.put("mysql_list_tables", "Lihat tabel database");
        TOOL_NAMES.put("mysql_describe_table", "Lihat struktur tabel");
        TOOL_NAMES.put("ls", "Direktori daftar");
        TOOL_NAMES.put("list_directory", "Direktori daftar");
        TOOL_NAMES.put("query_kb", "Cari basis pengetahuan");
        TOOL_NAMES.put("find_kb_document", "Temukan dokumen basis pengetahuan");
        TOOL_NAMES.put("open_kb_document", "Buka dokumen basis pengetahuan");
        TOOL_NAMES.put("list_kbs", "Lihat daftar basis pengetahuan");
        TOOL_NAMES.put("search_file", "Cari dokumen basis pengetahuan");
        TOOL_NAMES.put("ask_user_question", "Ajukan pertanyaan pengguna");
        TOOL_NAMES.put("install_skill", "Keterampilan instalasi");
        TOOL_NAMES.put("calculator", "Kalkulator");
        TOOL_NAMES.put("text_to_img_qwen_image", "Hasilkan gambar");
        TOOL_NAMES.put("create_session", "Buat sesi");
        TOOL_NAMES.put("send_message", "Posting pesan kolaborasi");
        TOOL_NAMES.put("submit_input", "Kirimkan pekerjaan");
        TOOL_NAMES.put("remember_memory", "Perbarui memori");
        TOOL_NAMES.put("cancel_turn", "Batalkan putaran");
        TOOL_NAMES.put("wait_sessions", "Menunggu pembaruan kolaborasi");
        TOOL_NAMES.put("wait_inputs", "Menunggu hasil tugas");
        TOOL_NAMES.put("list_sessions", "Lihat sesi");
        TOOL_NAMES.put("list_agents", "Lihat agen");
        TOOL_NAMES.put("get_result", "Lihat hasil tugas");
        TOOL_NAMES.put("ocr_parse_file", "File pengenalan OCR");
    }

    public static String getDisplayName(String toolId) {
        if (toolId == null || toolId.trim().isEmpty()) return "Alat Sistem";
        String lower = toolId.trim().toLowerCase();
        if (TOOL_NAMES.containsKey(lower)) {
            return TOOL_NAMES.get(lower);
        }
        return toolId.trim();
    }
}
