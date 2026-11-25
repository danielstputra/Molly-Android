# ==============================================================================
# Molly Agent Enterprise Hardening & Obfuscation ProGuard / R8 Rules
# Anti-Reverse Engineering, Anti-JADX Decompiler, Anti-Tamper & Code Obfuscation
# ==============================================================================

# 1. Agresif Obfuscation & Repackaging (Menghilangkan struktur hierarki folder Java)
-repackageclasses ''
-allowaccessmodification
-overloadaggressively
-mergeinterfacesaggressively

# 2. Hilangkan Nama Berkas Asli & Atribut Debug dari Bytecode DEX
-renamesourcefileattribute ''
-keepattributes Exceptions, InnerClasses, EnclosingMethod, AnnotationDefault, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
# Hapus SourceFile dan LineNumberTable agar decompiler JADX tidak dapat merekonstruksi nama berkas & baris asli
# -keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable

# 3. Optimasi Bytecode Berulang
-optimizationpasses 5

# 4. Hapus Panggilan Logging (Log.v, Log.d, Log.i, Log.w, Log.e) secara otomatis
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# 5. Pertahankan Komponen Inti Android Manifest
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class com.google.android.material.** { *; }

# 6. Pertahankan Model Data Gson Serialization agar Network Parsing Berjalan Mulus
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }
-keep class com.molly.agent.model.** { *; }
-keep class com.molly.agent.api.MollyApiClient$* { *; }

# 7. Pertahankan Barcode Scanner (ZXing)
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }

# 8. Pertahankan OkHttp / Okio Network Stack
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# 9. Proteksi Kelas Keamanan (AppSecurityGuard)
-keep class com.molly.agent.security.AppSecurityGuard {
    public static *** verifyAppIntegrity(...);
    public static *** enforceSecurity(...);
}

# 10. Pertahankan UI Custom Views & Layout Inflater constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
