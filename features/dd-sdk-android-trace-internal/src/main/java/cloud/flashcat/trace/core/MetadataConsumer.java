package cloud.flashcat.trace.core;

import cloud.flashcat.android.trace.internal.compat.function.Consumer;

@FunctionalInterface
public interface MetadataConsumer extends Consumer<Metadata> {

  MetadataConsumer NO_OP = (md) -> {};

  void accept(Metadata metadata);
}
