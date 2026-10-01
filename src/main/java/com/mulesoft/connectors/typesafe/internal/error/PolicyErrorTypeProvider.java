package com.mulesoft.connectors.typesafe.internal.error;

import org.mule.sdk.api.annotation.error.ErrorTypeProvider;
import org.mule.sdk.api.error.ErrorTypeDefinition;

import java.util.Set;

/**
 * Declares the {@code TYPESAFE:*} errors {@code apply-policy} may raise. It makes no provider call, so no transport
 * errors are listed: only an invalid decision or policy, and the opt-in {@code raiseOnReview} / {@code raiseOnReject}
 * outcomes.
 */
public class PolicyErrorTypeProvider implements ErrorTypeProvider {

  @Override
  @SuppressWarnings("rawtypes")
  public Set<ErrorTypeDefinition> getErrorTypes() {
    return Set.of(TypeSafeErrorType.INVALID_QUESTION_SET, TypeSafeErrorType.BELOW_THRESHOLD,
        TypeSafeErrorType.REJECTED);
  }
}
