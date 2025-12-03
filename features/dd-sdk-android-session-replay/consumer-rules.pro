# Keep the optional selector class name. We need this in the SR recorder.
-keepnames class * extends android.view.View
-keepnames class * extends android.graphics.drawable.Drawable
-keepnames class * extends android.graphics.ColorFilter

# Kept for our internal telemetry
-keepnames class cloud.flashcat.android.sessionreplay.internal.recorder.listener.WindowsOnDrawListener
-keepnames class cloud.flashcat.android.sessionreplay.internal.recorder.TreeViewTraversal
-keepnames class * extends cloud.flashcat.android.sessionreplay.recorder.mapper.WireframeMapper
-keepnames class * extends cloud.flashcat.android.sessionreplay.internal.async.RecordedDataQueueItem

# Keep the fine grained masking level enums
-keepnames enum * extends cloud.flashcat.android.sessionreplay.PrivacyLevel { *; }
