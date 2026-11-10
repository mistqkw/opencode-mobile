-keepclassmembers class app.ocmobile.** { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ML Kit (QR scanner) creates its component registrars by reflection. R8 full mode keeps no constructor
# on a bare "-keep class" (firebase-components 16.1.0 has only that), so SharedPrefManager went
# unregistered and the scanner crashed with an NPE.
-keep class * implements com.google.firebase.components.ComponentRegistrar { void <init>(); }
