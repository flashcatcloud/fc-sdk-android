package cloud.flashcat.trace.api.naming.v1;

import androidx.annotation.NonNull;

import cloud.flashcat.trace.api.naming.NamingSchema;

public class CacheNamingV1 implements NamingSchema.ForCache {
  @NonNull
  @Override
  public String operation(@NonNull String cacheSystem) {
    return cacheSystem + ".command";
  }

  @Override
  public String service(@NonNull String cacheSystem) {
    return null;
  }
}
