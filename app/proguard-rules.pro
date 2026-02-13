# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable
-dontnote kotlinx.serialization.AnnotationsKt

# Keep all classes with @Serializable annotation
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes
-keep class io.github.climbintelligence.data.** { *; }
-keep class io.github.climbintelligence.engine.** { *; }

# Keep Karoo extension classes
-keep class io.github.climbintelligence.ClimbIntelligenceExtension { *; }
-keep class io.github.climbintelligence.datatypes.** { *; }
-keep class io.hammerhead.karooext.** { *; }

# Keep coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
