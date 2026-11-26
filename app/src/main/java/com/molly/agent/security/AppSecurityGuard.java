package com.molly.agent.security;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Debug;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;

/**
 * Enterprise Application Shielding & Runtime Application Self-Protection (RASP).
 * Melindungi Molly Agent dari Reverse Engineering, Dekompilasi, Modifikasi,
 * Kloning Paket, Debugger, Framework Hooking (Frida/Xposed), dan Perangkat Ter-root.
 */
public class AppSecurityGuard {

    private static final String TAG = "MollyShield";

    // Expected Official Package Name
    private static final String EXPECTED_PACKAGE_NAME = "com.molly.agent";

    // Official SHA-256 Release Certificate Fingerprint
    // Hash: 42:8C:C9:15:FF:95:FD:2C:A0:BB:99:2B:00:DB:B9:8D:0B:80:AF:22:C8:CE:6D:C1:55:94:73:8D:78:56:BE:53
    private static final String EXPECTED_CERT_SHA256 = "428CC915FF95FD2CA0BB992B00DBB98D0B80AF22C8CE6DC15594738D7856BE53";

    public interface SecurityCallback {
        void onVerified();
        void onViolation(String reason);
    }

    /**
     * Memeriksa integritas aplikasi secara menyeluruh saat runtime.
     * @return null jika aman, atau deskripsi pelanggaran jika terdeteksi ancaman.
     */
    public static String verifyAppIntegrity(Context context) {
        // 1. Anti-Clone & Package Name Integrity
        if (!verifyPackageName(context)) {
            return "Pelanggaran Integritas: Nama paket aplikasi telah dimodifikasi (Anti-Clone Triggered).";
        }

        // 2. Anti-Tamper & Signature Verification
        if (!verifySignature(context)) {
            return "Pelanggaran Keamanan: Sertifikat penandatangan APK tidak valid atau telah ditandatangani ulang.";
        }

        // 3. Anti-Debug Detection
        if (isDebuggerAttached(context)) {
            return "Peringatan Keamanan: Terdeteksi upaya debugging atau profiling pada aplikasi.";
        }

        // 4. Anti-Hooking & Instrumentasi Dinamis (Frida / Xposed)
        if (isHookingFrameworkDetected()) {
            return "Peringatan Keamanan: Terdeteksi framework instrumentasi/injeksi kode berbahaya (Frida/Xposed).";
        }

        return null;
    }

    /**
     * Menjalankan proteksi aplikasi pada Activity.
     * Jika terdeteksi manipulasi, tampilkan dialog keamanan dan terminasi aplikasi.
     */
    public static boolean enforceSecurity(Activity activity) {
        String violation = verifyAppIntegrity(activity);
        if (violation != null) {
            handleSecurityBreach(activity, violation);
            return false;
        }
        return true;
    }

    private static boolean verifyPackageName(Context context) {
        try {
            String currentPkg = context.getPackageName();
            return EXPECTED_PACKAGE_NAME.equals(currentPkg);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean verifySignature(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();

            Signature[] signatures = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                SigningInfo signingInfo = packageInfo.signingInfo;
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signatures = signingInfo.getApkContentsSigners();
                    } else {
                        signatures = signingInfo.getSigningCertificateHistory();
                    }
                }
            } else {
                @SuppressWarnings("deprecation")
                PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
                signatures = packageInfo.signatures;
            }

            if (signatures == null || signatures.length == 0) {
                return false;
            }

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Signature sig : signatures) {
                byte[] certBytes = sig.toByteArray();
                md.update(certBytes);
                byte[] digest = md.digest();
                String hex = bytesToHex(digest);

                if (EXPECTED_CERT_SHA256.equalsIgnoreCase(hex)) {
                    return true;
                }
            }

            // Jika dalam mode development tanpa release key, log peringatan tapi tetap validasi hash
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isDebuggerAttached(Context context) {
        try {
            if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
                return true;
            }
            int flags = context.getApplicationInfo().flags;
            return (flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isHookingFrameworkDetected() {
        // Cek kelas Xposed Bridge
        try {
            Class.forName("de.robv.android.xposed.XposedBridge");
            return true;
        } catch (ClassNotFoundException ignored) {}

        // Cek kelas Frida hooking artifacts
        try {
            Class.forName("com.saurik.substrate.MS");
            return true;
        } catch (ClassNotFoundException ignored) {}

        // Cek berkas server Frida umum
        String[] fridaPaths = {
                "/data/local/tmp/frida-server",
                "/data/local/tmp/re.frida.server",
                "/system/bin/frida-server",
                "/system/xbin/frida-server"
        };
        for (String path : fridaPaths) {
            if (new File(path).exists()) {
                return true;
            }
        }

        // Cek socket Frida port default 27042 (non-blocking test)
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", 27042), 60);
            socket.close();
            return true;
        } catch (Exception ignored) {}

        return false;
    }

    public static boolean isDeviceRooted() {
        String[] paths = {
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
        };
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }

        String buildTags = Build.TAGS;
        return buildTags != null && buildTags.contains("test-keys");
    }

    private static void handleSecurityBreach(Activity activity, String reason) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        activity.runOnUiThread(() -> {
            new com.molly.agent.ui.MollyDialog.Builder(activity)
                    .setTitle("🛡️ Perlindungan Keamanan Molly")
                    .setMessage(reason + "\n\nAplikasi ditutup secara aman untuk mencegah manipulasi atau kebocoran data.")
                    .setCancelable(false)
                    .setDestructiveButton("Keluar", (dialog, which) -> {
                        activity.finishAffinity();
                        Process.killProcess(Process.myPid());
                        System.exit(0);
                    })
                    .show();
        });
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
