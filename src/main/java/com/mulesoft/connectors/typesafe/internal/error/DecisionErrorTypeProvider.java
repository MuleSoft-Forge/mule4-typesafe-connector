package com.mulesoft.connectors.typesafe.internal.error;

import org.mule.sdk.api.annotation.error.ErrorTypeProvider;
import org.mule.sdk.api.error.ErrorTypeDefinition;

import java.util.Set;

/**
 * Declares the {@code TYPESAFE:*} errors a decision operation may raise, so the runtime shows them in the palette and
 * flows can catch them by type. It covers the transport and validation errors {@code evaluate} can produce. An
 * operation that throws a type not listed here fails with an unexpected error instead of the typed one.
 */
public class DecisionErrorTypeProvider implements ErrorTypeProvider {

  @Override
  @SuppressWarnings("rawtypes")
  public Set<ErrorTypeDefinition> getErrorTypes() {
    return Set.of(TypeSafeErrorType.INVALID_QUESTION_SET, TypeSafeErrorType.INVALID_STATE,
        TypeSafeErrorType.TOO_MANY_OPTIONS, TypeSafeErrorType.UNAUTHORIZED, TypeSafeErrorType.RATE_LIMITED,
        TypeSafeErrorType.OVERLOADED, TypeSafeErrorType.TIMEOUT, TypeSafeErrorType.CONNECTIVITY,
        TypeSafeErrorType.PROVIDER_VALIDATION, TypeSafeErrorType.PROVIDER_ERROR, TypeSafeErrorType.INVALID_RESPONSE,
        TypeSafeErrorType.UNSUPPORTED_BY_PROVIDER, TypeSafeErrorType.BUDGET_EXCEEDED);
  }
}
