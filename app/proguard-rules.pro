# Add project specific ProGuard rules here.

# Room
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Glance Widget
-keep class androidx.glance.appwidget.** { *; }

# Apache POI / iText (export libraries pull in reflection-heavy code)
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn com.itextpdf.**
-keep class org.apache.poi.** { *; }
-keep class com.itextpdf.** { *; }

# ZXing QR
-keep class com.google.zxing.** { *; }

# Google API client / Drive
-keep class com.google.api.** { *; }
-dontwarn com.google.api.client.**

# Keep data models used with reflection-based serialization
-keep class com.watercantracker.app.data.local.entity.** { *; }
-keep class com.watercantracker.app.domain.model.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ZXing QR
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Apache POI pulls in graphbuilder (used for chart shapes in desktop Excel) which
# references java.awt.Shape — a desktop-only AWT class not present on Android.
# We don't use Excel charting features, so it's safe to ignore these missing classes.
-dontwarn java.awt.**
-dontwarn com.graphbuilder.**
-dontwarn javax.imageio.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.xmlgraphics.**

# Apache POI general
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn schemasMicrosoftComOfficeOffice.**
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.** { *; }
