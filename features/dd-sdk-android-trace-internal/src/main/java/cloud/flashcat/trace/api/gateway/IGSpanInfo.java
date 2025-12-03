package cloud.flashcat.trace.api.gateway;

import cloud.flashcat.trace.api.DDTraceId;
import cloud.flashcat.trace.bootstrap.instrumentation.api.AgentSpan;

import java.util.Map;

public interface IGSpanInfo {
  DDTraceId getTraceId();

  long getSpanId();

  Map<String, Object> getTags();

  AgentSpan setTag(String key, boolean value);

  void setRequestBlockingAction(Flow.Action.RequestBlockingAction rba);

  Flow.Action.RequestBlockingAction getRequestBlockingAction();
}
