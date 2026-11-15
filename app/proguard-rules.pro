# Conservar información útil en stack traces sin exponer rutas locales de compilación
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Reglas para anotaciones, genéricos y clases anidadas (Compose / Coroutines / Firebase)
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Credential Manager & Google Identity Services (reflection en tiempo de ejecución)
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**

# Kotlin Coroutines (ServiceLoader & FastServiceLoader)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.flow.**

# Firebase Auth / Firestore / App Check
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.firestore.** { *; }
-dontwarn com.google.firebase.ktx.Firebase
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
