# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/rhuta/Softs/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools-proguard.html

# Add any project specific keep rules here:

# AI Engine / llama.cpp / JNI Keep Rules
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.rhuta.kask.domain.engine.** { *; }
-keep interface com.rhuta.kask.domain.engine.TokenCallback { *; }

# PDFBox Keep Rules
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-keep class org.apache.fontbox.** { *; }
-dontwarn org.apache.fontbox.**

# Room Library
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class com.rhuta.kask.di.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, EnclosingMethod, Signature
-keepclassmembers class com.rhuta.kask.domain.model.** {
    *** Companion;
    *** $serializer;
}

# General Compose / Material 3
-keep class androidx.compose.material3.** { *; }
-dontwarn androidx.compose.material3.**
