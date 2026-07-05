# 默认 ProGuard 规则
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 题库 Schema 保留
-keep class app.unlocklearn.quizbank.model.** { *; }
-keep class app.unlocklearn.scheduler.** { *; }
