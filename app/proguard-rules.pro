# Keep Room Database, Entities, DAOs & generated implementations
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep AndroidManifest entrypoints
-keep public class com.regainfocus.app.RegainFocusApplication
-keep public class com.regainfocus.app.MainActivity
-keep public class com.regainfocus.app.ui.blocker.StrictBlockShieldActivity
-keep public class com.regainfocus.app.service.** { *; }
-keep public class com.regainfocus.app.receiver.** { *; }
-keep public class com.regainfocus.app.worker.** { *; }

# Keep Kotlinx Serialization generated serializers & @Serializable models
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.regainfocus.app.**$$serializer { *; }
-keep @kotlinx.serialization.Serializable class com.regainfocus.app.** { *; }
-keepclassmembers class com.regainfocus.app.domain.model.** {
    *** Companion;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclasseswithmembers class com.regainfocus.app.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
