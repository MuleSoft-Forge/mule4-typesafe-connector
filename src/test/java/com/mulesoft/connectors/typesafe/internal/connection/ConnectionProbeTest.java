package com.mulesoft.connectors.typesafe.internal.connection;

import org.mule.sdk.api.connectivity.ConnectionValidationResult;
import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.typesafe.internal.domain.DecisionRequest;
import com.mulesoft.connectors.typesafe.internal.domain.DecisionResponse;
import com.mulesoft.connectors.typesafe.internal.error.TypeSafeErrorType;
import com.mulesoft.connectors.typesafe.internal.http.ProviderHttpException;
import com.mulesoft.connectors.typesafe.internal.provider.Capabilities;
import com.mulesoft.connectors.typesafe.internal.provider.ModelListPage;
import com.mulesoft.connectors.typesafe.internal.provider.ProviderAdapter;
import com.mulesoft.connectors.typesafe.internal.util.Json;

import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionProbeTest {

  @Test
  void pingRequestIsMinimalNoulWithNullModel() {
    DecisionRequest ping = ConnectionProbe.pingRequest();

    assertNull(ping.requestedModel());
    assertTrue(ping.state().isObject());
    assertEquals("noul", ping.questions().path("ping").path("type").asText());
    assertEquals("Is the connection accepted?", ping.questions().path("ping").path("instructions").asText());
    assertEquals("yes", ping.questions().path("ping").path("criteria").path("true").asText());
    assertEquals("no", ping.questions().path("ping").path("criteria").path("false").asText());
  }

  @Test
  void validatePrimarySucceedsWhenEvaluateSucceeds() {
    ProviderAdapter adapter = new StubAdapter(CompletableFuture.completedFuture(
        DecisionResponse.builder().model("jev-latest").requestedModel("jev-latest").answers(Json.object()).build()));

    ConnectionValidationResult result = ConnectionProbe.validatePrimary(adapter);

    assertTrue(result.isValid());
  }

  @Test
  void validatePrimaryFailsUnauthorizedOn401() {
    ProviderAdapter adapter = new StubAdapter(CompletableFuture.failedFuture(
        new ProviderHttpException(401, "{\"error\":{\"message\":\"User not found.\"}}", OptionalLong.empty())));

    ConnectionValidationResult result = ConnectionProbe.validatePrimary(adapter);

    assertFalse(result.isValid());
    assertTrue(result.getMessage().contains("UNAUTHORIZED"));
    assertTrue(result.getMessage().contains("401"));
    assertTrue(result.getMessage().contains("User not found."));
    assertTrue(result.getException() instanceof ModuleException);
    assertEquals(TypeSafeErrorType.UNAUTHORIZED, ((ModuleException) result.getException()).getType());
  }

  private static final class StubAdapter implements ProviderAdapter {

    private final CompletableFuture<DecisionResponse> future;

    private StubAdapter(CompletableFuture<DecisionResponse> future) {
      this.future = future;
    }

    @Override
    public String routeName() {
      return "stub";
    }

    @Override
    public Capabilities capabilities() {
      return Capabilities.full(true);
    }

    @Override
    public CompletableFuture<DecisionResponse> evaluate(DecisionRequest request) {
      return future;
    }

    @Override
    public CompletableFuture<ModelListPage> listModels() {
      return CompletableFuture.completedFuture(new ModelListPage(List.of(), 200, null));
    }
  }
}
