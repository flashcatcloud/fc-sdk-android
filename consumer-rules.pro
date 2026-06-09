# This is needed for the Datadog Error Tracking feature to work reliably,
 # this file is used by Logs and RUM modules
-keepattributes SourceFile,LineNumberTable

# Shaded dependencies
-keep class cloud.flashcat.shaded.** { *; }

# Runtime dependencies checked through Class.forName during SDK initialization.
# Gson and OkHttp are compileOnly, so host apps provide them; keep their original
# class names so R8 minified apps still pass the SDK dependency check.
-keep class com.google.gson.** { *; }
-keep class okhttp3.OkHttpClient { *; }
