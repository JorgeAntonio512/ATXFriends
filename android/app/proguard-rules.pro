# R8 rules for the release build. Firebase, Compose, Coil, OkHttp, coroutines and
# kotlinx.serialization all ship their own consumer rules; only app-specific needs are here.

# Readable crash stack traces (Play Console deobfuscates the rest with the uploaded mapping).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Navigation's type-safe routes are @Serializable classes/objects that Navigation looks up by
# KClass at runtime (reflection on Companion / INSTANCE / serializer()). The library rules cover
# this, but a route that fails to resolve crashes on tap, so keep ours explicitly.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature
-keep,includedescriptorclasses class com.georgeappdev.atxfriends.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.georgeappdev.atxfriends.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.georgeappdev.atxfriends.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Firestore models need no rules: every document is decoded by hand through DocReader /
# fromFirestore() and written as explicit field maps (never toObject() or reflection), and
# enums are stored by their explicit `raw` strings, not by Enum.name.
