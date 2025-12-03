package cloud.flashcat.trace.core.scopemanager;

import cloud.flashcat.trace.api.Stateful;
import cloud.flashcat.trace.bootstrap.instrumentation.api.AgentSpan;
import cloud.flashcat.trace.logger.Logger;
import cloud.flashcat.trace.relocate.api.RatelimitedLogger;

final class ContinuingScope extends ContinuableScope {
  /** Continuation that created this scope. */
  private final AbstractContinuation continuation;

  ContinuingScope(
          final ContinuableScopeManager scopeManager,
          final AgentSpan span,
          final byte source,
          final boolean isAsyncPropagating,
          final AbstractContinuation continuation,
          final Stateful scopeState,
          final Logger logger,
          final RatelimitedLogger ratelimitedLogger) {
    super(scopeManager, span, source, isAsyncPropagating, scopeState, logger, ratelimitedLogger);
    this.continuation = continuation;
  }

  @Override
  void cleanup(final ScopeStack scopeStack) {
    super.cleanup(scopeStack);

    continuation.cancelFromContinuedScopeClose();
  }
}
