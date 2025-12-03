package cloud.flashcat.trace.core.util;

public interface Matcher {
  boolean matches(String str);

  boolean matches(CharSequence charSeq);
}
