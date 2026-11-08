# Conservative: only remove unused library code. No renaming, no optimisation.
-dontobfuscate
-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,SourceFile,LineNumberTable

# Our code: JNI (Native), serialization, services/receivers are all looked up by name.
-keep class com.anibiar.app.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Libraries that use JNI or reflection.
-keep class org.webrtc.** { *; }
-keep class io.getstream.** { *; }
-keep class org.chromium.** { *; }
-keep class androidx.media3.** { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn org.webrtc.**
-dontwarn io.getstream.**
-dontwarn org.chromium.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
