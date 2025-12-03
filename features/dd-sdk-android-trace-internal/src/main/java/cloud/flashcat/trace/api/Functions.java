package cloud.flashcat.trace.api;

import cloud.flashcat.trace.bootstrap.instrumentation.api.UTF8BytesString;

import java.util.Locale;
import cloud.flashcat.android.trace.internal.compat.function.Function;

public final class Functions {

  private Functions() {}

  public static final Function<String, UTF8BytesString> UTF8_ENCODE = UTF8BytesString::create;

  public static final class LowerCase implements Function<String, String> {

    public static final LowerCase INSTANCE = new LowerCase();

    @Override
    public String apply(String key) {
      return key.toLowerCase(Locale.ROOT);
    }
  }

}
