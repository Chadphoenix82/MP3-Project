# Photon Audio Deck — Production R8 / ProGuard Rules

# Preserve Kotlin metadata & kotlinx.serialization @Serializable NavKeys / models
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,includedescriptorclasses class com.example.ui.navigation.**$$serializer { *; }
-keepclassmembers class com.example.ui.navigation.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.ui.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room Database Entities, DAOs, and Generated Implementations
-keep class com.example.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Retrofit & Moshi JSON Adapters
-keep class com.example.data.network.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Media3 / ExoPlayer & Audio Effects
-keep class androidx.media3.** { *; }
-keep class com.example.player.PhotonPlaybackService { *; }
-dontwarn androidx.media3.**

# Jetpack Glance Widgets
-keep class com.example.widget.** { *; }
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**
