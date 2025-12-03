package cloud.flashcat.trace.common.sampling;

public interface RateSampler extends Sampler {
  double getSampleRate();
}
