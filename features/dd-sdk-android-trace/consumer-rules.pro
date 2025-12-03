-keepnames class cloud.flashcat.android.trace.GlobalDatadogTracer {
    public cloud.flashcat.android.trace.api.tracer.DatadogTracer getOrNull();
    public static cloud.flashcat.android.trace.GlobalDatadogTracer INSTANCE;
}
-keepclassmembernames class org.jctools.** { *; }
