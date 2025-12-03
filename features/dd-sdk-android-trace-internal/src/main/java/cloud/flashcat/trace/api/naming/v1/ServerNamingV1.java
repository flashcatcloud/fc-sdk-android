package cloud.flashcat.trace.api.naming.v1;

import androidx.annotation.NonNull;

import cloud.flashcat.trace.api.naming.NamingSchema;

public class ServerNamingV1 implements NamingSchema.ForServer {

  @NonNull
  @Override
  public String operationForProtocol(@NonNull String protocol) {
    return protocol + ".server.request";
  }

  @NonNull
  @Override
  public String operationForComponent(@NonNull String component) {
    return "http.server.request";
  }
}
