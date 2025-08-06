##############################################################
# Kotlin Reflect (kotlin.reflect.*)
##############################################################
-keep class kotlin.reflect.** { *; }


##############################################################
# UCrop Library
##############################################################
-dontwarn com.yalantis.ucrop**
-keep class com.yalantis.ucrop** { *; }
-keep interface com.yalantis.ucrop** { *; }


##############################################################
# General Keep Attributes (annotations, signatures, etc.)
##############################################################
-keepattributes *Annotation*
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault

##############################################################
# Google API Client (e.g. @Key annotation)
##############################################################
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}


##############################################################
# Google Play Services & Firebase
##############################################################
-keep class com.google.** { *; }
-keep interface com.google.** { *; }
-dontwarn com.google.**

# Firebase Messaging
-dontwarn com.google.firebase.messaging.**

# Flexbox Layout Manager
-keepnames public class com.google.android.flexbox.FlexboxLayoutManager


##############################################################
# Crashlytics
##############################################################
-keep class com.crashlytics.** { *; }
-dontwarn com.crashlytics.**


##############################################################
# Square Libraries
##############################################################
-dontwarn com.squareup.picasso.**
-dontwarn com.squareup.okhttp.**


##############################################################
# Google Play Billing
##############################################################
-keep class com.android.vending.billing.**


##############################################################
# Gson - General Configuration
##############################################################

# Suppress warning for sun.misc (used by Gson internally sometimes)
-dontwarn sun.misc.**

# Optional: keep Gson stream package
#-keep class com.google.gson.stream.** { *; }

# Keep model used in official gson example (change if not used)
-keep class com.google.gson.examples.android.model.** { <fields>; }

# Keep TypeAdapter and JsonAdapter related classes
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent Gson from stripping fields with @SerializedName
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Retain TypeToken and subclasses for generic type parsing
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Keep no-args constructor of classes which can be used with @JsonAdapter
# By default their no-args constructor is invoked to create an adapter instance
-keepclassmembers class * extends com.google.gson.TypeAdapter {
  <init>();
}
-keepclassmembers class * implements com.google.gson.TypeAdapterFactory {
  <init>();
}
-keepclassmembers class * implements com.google.gson.JsonSerializer {
  <init>();
}
-keepclassmembers class * implements com.google.gson.JsonDeserializer {
  <init>();
}

# Keep fields annotated with @SerializedName for classes which are referenced.
# If classes with fields annotated with @SerializedName have a no-args
# constructor keep that as well. Based on
# https://issuetracker.google.com/issues/150189783#comment11.
# See also https://github.com/google/gson/pull/2420#discussion_r1241813541
# for a more detailed explanation.
-if class *
-keepclasseswithmembers,allowobfuscation class <1> {
  @com.google.gson.annotations.SerializedName <fields>;
}
-if class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers,allowobfuscation,allowoptimization class <1> {
  <init>();
}

##############################################################
# Retrofit
##############################################################
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}
# Keep generic signature of Call, Response (R8 full mode strips signatures from non-kept items).
 -keep,allowobfuscation,allowshrinking interface retrofit2.Call
 -keep,allowobfuscation,allowshrinking class retrofit2.Response

 # With R8 full mode generic signatures are stripped for classes that are not
 # kept. Suspend functions are wrapped in continuations where the type argument
 # is used.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

##############################################################
# Custom App Classes
##############################################################

# Keep constructors with Context (e.g. for View/Adapter)
-keepclasseswithmembers class * {
    public <init>(android.content.Context);
}

# Custom calendar library
-keep class com.haibin.calendarview.** { *; }

# WebView JS interface
-keepclassmembers class fqcn.of.javascript.interface.for.webview {
   public *;
}