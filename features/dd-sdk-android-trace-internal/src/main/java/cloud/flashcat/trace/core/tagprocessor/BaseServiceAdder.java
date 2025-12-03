package cloud.flashcat.trace.core.tagprocessor;

import androidx.annotation.Nullable;

import cloud.flashcat.trace.api.DDTags;
import cloud.flashcat.trace.bootstrap.instrumentation.api.UTF8BytesString;
import cloud.flashcat.trace.core.DDSpanContext;

import java.util.Map;

public class BaseServiceAdder implements TagsPostProcessor {
  private final UTF8BytesString ddService;

  public BaseServiceAdder(@Nullable final String ddService) {
    this.ddService = ddService != null ? UTF8BytesString.create(ddService) : null;
  }

  @Override
  public Map<String, Object> processTags(Map<String, Object> unsafeTags) {
    // we are only able to do something if the span service name is known
    return unsafeTags;
  }

  @Override
  public Map<String, Object> processTagsWithContext(
      Map<String, Object> unsafeTags, DDSpanContext spanContext) {
    if (ddService != null && !ddService.toString().equalsIgnoreCase(spanContext.getServiceName())) {
      unsafeTags.put(DDTags.BASE_SERVICE, ddService);
    }
    return unsafeTags;
  }
}
