package cloud.flashcat.trace.api.scopemanager;

import cloud.flashcat.trace.api.DDTraceId;
import cloud.flashcat.trace.api.TraceConfig;

public interface ExtendedScopeListener extends ScopeListener {
  void afterScopeActivated(
      DDTraceId traceId, long localRootSpanId, long spanId, TraceConfig traceConfig);

  /** Called just after a scope is closed. */
  @Override
  void afterScopeClosed();
}
