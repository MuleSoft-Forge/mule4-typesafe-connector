package com.mulesoft.connectors.typesafe.internal.error;

import org.mule.sdk.api.annotation.error.ErrorTypeProvider;
import org.mule.sdk.api.error.ErrorTypeDefinition;

import java.util.Set;

/**
 * Declares the {@code TYPESAFE:*} errors the scale operations ({@code evaluate-batch}, {@code filter}) may raise. It
 * adds {@code BATCH_TOO_LARGE} to the transport and validation set a single decision can produce; per-item failures are
 * reported inside the payload rather than raised, so they are not listed here.
 */
public class BatchErrorTypeProvider implements ErrorTypeProvider {

  @Override
  @SuppressWarnings("rawtypes")
  public Set<ErrorTypeDefinition> getErrorTypes() {
    return Set.of(TypeSafeErrorType.BATCH_TOO_LARGE, TypeSafeErrorType.INVALID_QUESTION_SET,
        TypeSafeErrorType.INVALID_STATE, TypeSafeErrorType.BUDGET_EXCEEDED, TypeSafeErrorType.UNAUTHORIZED,
        TypeSafeErrorType.RATE_LIMITED, TypeSafeErrorType.OVERLOADED, TypeSafeErrorType.TIMEOUT,
        TypeSafeErrorType.CONNECTIVITY, TypeSafeErrorType.PROVIDER_VALIDATION, TypeSafeErrorType.PROVIDER_ERROR,
        TypeSafeErrorType.INVALID_RESPONSE, TypeSafeErrorType.UNSUPPORTED_BY_PROVIDER);
  }
}
