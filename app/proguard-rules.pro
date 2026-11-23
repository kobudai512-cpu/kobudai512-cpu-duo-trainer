# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.duotrainer.**$$serializer { *; }
-keepclassmembers class app.duotrainer.** { *** Companion; }
-keepclasseswithmembers class app.duotrainer.** { kotlinx.serialization.KSerializer serializer(...); }
