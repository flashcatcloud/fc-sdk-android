package cloud.flashcat.trace.common.sampling;

import cloud.flashcat.trace.core.CoreSpan;

public interface PrioritySampler {
  <T extends CoreSpan<T>> void setSamplingPriority(T span);
}
