package com.mulesoft.connectors.typesafe.internal.connection;

import org.mule.sdk.api.connectivity.ConnectionValidationResult;
import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.typesafe.internal.domain.DecisionRequest;
import com.mulesoft.connectors.typesafe.internal.http.HttpErrorMapper;
import com.mulesoft.connectors.typesafe.internal.http.ProviderHttpException;
import com.mulesoft.connectors.typesafe.internal.provider.ProviderAdapter;
import com.mulesoft.connectors.typesafe.internal.util.Json;

import java.util.Map;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Minimal decision used by Test Connection. One Noul question on {@code POST /{apiVersion}/systemone} (or the
 * Cloudflare equivalent) so a rejected API key fails validation. List Models is not used: OpenRouter's catalog is
 * public and returns HTTP 200 without a valid key.
 */
public final class ConnectionProbe {

  private ConnectionProbe() {
  }

  /** Ping request; {@code requestedModel} is null so the route uses the model on the connection config. */
  public static DecisionRequest pingRequest() {
    ObjectNode questions = Json.object();
    ObjectNode ping = questions.putObject("ping");
    ping.put("type", "noul");
    ping.put("instructions", "Is the connection accepted?");
    ObjectNode criteria = ping.putObject("criteria");
    criteria.put("true", "yes");
    criteria.put("false", "no");
    return new DecisionRequest(Json.object(), null, questions, Map.of(), null, null);
  }

  /** Runs the ping on {@code primary} and maps success or typed provider failures for Studio Test Connection. */
  public static ConnectionValidationResult validatePrimary(ProviderAdapter primary) {
    try {
      primary.evaluate(pingRequest()).join();
      return ConnectionValidationResult.success();
    } catch (CompletionException e) {
      return failure(e.getCause() != null ? e.getCause() : e);
    } catch (RuntimeException e) {
      return failure(e);
    }
  }

  private static ConnectionValidationResult failure(Throwable cause) {
    if (cause instanceof ProviderHttpException) {
      ProviderHttpException http = (ProviderHttpException) cause;
      ModuleException mapped = HttpErrorMapper.toException(http.status(), http.body());
      return ConnectionValidationResult.failure(mapped.getMessage(), mapped);
    }
    if (cause instanceof ModuleException) {
      ModuleException mapped = (ModuleException) cause;
      return ConnectionValidationResult.failure(mapped.getMessage(), mapped);
    }
    Exception exception = cause instanceof Exception ? (Exception) cause : new RuntimeException(cause);
    String message = exception.getMessage() != null ? exception.getMessage() : "Connection validation failed";
    return ConnectionValidationResult.failure(message, exception);
  }
}
