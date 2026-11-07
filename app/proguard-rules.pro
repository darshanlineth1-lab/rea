# Keep Room entities & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepnames class com.regainfocus.app.** { *; }

# Keep Kotlinx Serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.regainfocus.app.**$$serializer { *; }
-keepclassmembers class com.regainfocus.app.** { *** Companion; }
-keepclasseswithmembers class com.regainfocus.app.** { kotlinx.serialization.KSerializer serializer(...); }
