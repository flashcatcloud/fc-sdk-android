# Needed to make sure we don't remove any test code
-dontshrink
#-dontoptimize
#-keepattributes *Annotation*

-dontwarn kotlin.Experimental$Level
-dontwarn kotlin.Experimental

-dontwarn com.google.gson.**
-dontwarn okhttp3.**
-dontwarn okio.**

