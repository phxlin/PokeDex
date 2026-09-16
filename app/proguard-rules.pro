# kotlinx.serialization — keep generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.pokedex.app.**$$serializer { *; }
-keepclassmembers class com.pokedex.app.** {
    *** Companion;
}

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, Exceptions

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
