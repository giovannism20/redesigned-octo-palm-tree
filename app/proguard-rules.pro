# R8 rules for release builds. Libraries ship their own consumer rules; add
# project-specific rules here with a comment explaining why each is needed.

# PdfBox-Android decodes JPEG 2000 (JPX) images only when the optional
# com.gemalto.jp2 library is present. R Reader does not ship it (text extraction
# never decodes images), so PdfBox's references to it must not fail R8.
-dontwarn com.gemalto.jp2.**

# SR-8: release builds keep no verbose, debug or information logging. R8 removes
# these calls, from the app and from libraries alike, so nothing logged at those
# levels can reach the system log. Warnings and errors stay: Diagnostics logs
# exception types with Log.w, and WorkManager is set to log errors only
# (RReaderApplication).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# sherpa-onnx and piper-phonemize (offline neural voices, ADR 0018) read their Kotlin configuration classes and
# create their result classes from native code, by class, field and method name (JNI). Their AARs ship no
# consumer rules, so keep those classes as they are.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class com.github.csukuangfj.piper.phonemize.** { *; }

# Commons Compress refers to optional codecs and tools that R Reader does not ship (it only reads bzip2 tar
# archives of voice models): XZ, Zstandard, Brotli and ASM.
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
