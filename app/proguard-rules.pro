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
