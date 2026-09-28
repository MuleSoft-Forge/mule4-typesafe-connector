package com.mulesoft.connectors.typesafe.internal.provider;

import org.mule.runtime.http.api.HttpConstants;
import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.typesafe.internal.domain.DecisionRequest;
import com.mulesoft.connectors.typesafe.internal.domain.DecisionResponse;
import com.mulesoft.connectors.typesafe.internal.error.TypeSafeErrorType;
import com.mulesoft.connectors.typesafe.internal.http.HttpTransport;
import com.mulesoft.connectors.typesafe.internal.http.ProviderHttpException;
import com.mulesoft.connectors.typesafe.internal.http.RawHttpResponse;
import com.mulesoft.connectors.typesafe.internal.util.Json;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemOneAdapterTest {

  @Mock
  private HttpTransport transport;

  private DecisionRequest request() {
    ObjectNode questions = (ObjectNode) Json.read("{\"q\":{\"type\":\"noul\",\"instructions\":\"?\"}}");
    return new DecisionRequest(Json.read("{\"x\":1}"), null, questions, Map.of(), "qs", "v1");
  }

  private SystemOneAdapter adapter(CostExtractor cost) {
    return new SystemOneAdapter("typesafe", "https://api.typesafe.ai/", "v1", "default-model", Capabilities.full(true),
        "key", Map.of(), cost, RequestIdExtractor.header("x-request-id"), transport);
  }

  @Test
  void parsesSuccessfulResponse() {
    String body = "{\"model\":\"m-1\",\"answers\":{\"q\":{\"type\":\"noul\",\"noul\":0.9}},"
        + "\"usage\":{\"input_tokens\":12,\"output_tokens\":3}}";
    when(transport.send(any(HttpConstants.Method.class), eq("https://api.typesafe.ai/v1/systemone"), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, body, Map.of("x-request-id", "req-9"))));

    DecisionResponse response = adapter(CostExtractor.NONE).evaluate(request()).join();

    assertEquals("m-1", response.model());
    assertEquals("default-model", response.requestedModel());
    assertEquals(12, response.inputTokens());
    assertEquals(3, response.outputTokens());
    assertEquals("req-9", response.providerRequestId());
    assertEquals(0.9, response.answers().get("q").get("noul").asDouble(), 1e-9);
    assertNull(response.providerReportedCost());
  }

  @Test
  void extractsProviderReportedCost() {
    String body = "{\"answers\":{\"q\":{\"type\":\"noul\"}},\"usage\":{\"input_tokens\":5,\"cost\":0.0021}}";
    when(transport.send(any(), any(), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, body, Map.of())));

    DecisionResponse response = adapter(CostExtractor.OPENROUTER).evaluate(request()).join();

    assertEquals(new BigDecimal("0.0021"), response.providerReportedCost());
  }

  @Test
  void throwsProviderHttpExceptionWithRetryAfterOnNon2xx() {
    when(transport.send(any(), any(), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(
            new RawHttpResponse(429, "{\"error\":\"slow\"}", Map.of("retry-after-ms", "1500"))));

    CompletionException thrown = assertThrows(CompletionException.class,
        () -> adapter(CostExtractor.NONE).evaluate(request()).join());
    ProviderHttpException cause = assertInstanceOf(ProviderHttpException.class, thrown.getCause());
    assertEquals(429, cause.status());
    assertEquals(1500L, cause.retryAfterMs().getAsLong());
  }

  @Test
  void listsModelsFromModelsEndpoint() {
    String body = "{\"models\":[{\"name\":\"jev-latest\"},{\"name\":\"jev-1.13.0\"}]}";
    when(transport.send(any(HttpConstants.Method.class), eq("https://api.typesafe.ai/v1/models"), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, body, Map.of())));

    List<String> models = adapter(CostExtractor.NONE).listModels().join().models().stream().map(ModelCard::name)
        .toList();

    assertEquals(List.of("jev-latest", "jev-1.13.0"), models);
  }

  @Test
  void listsModelsKeepsNameDescriptionAndReleaseDate() {
    String body = "{\"models\":["
        + "{\"name\":\"jev-latest\",\"description\":\"The latest iteration of TypeSafe's System One Model: Jev\","
        + "\"release_date\":\"2026-09-10T18:38:01.391457+00:00\"},"
        + "{\"name\":\"jev-preview\",\"description\":\"A preview version of jev-latest\","
        + "\"release_date\":\"2026-09-10T18:39:06.057655+00:00\"}]}";
    when(transport.send(any(HttpConstants.Method.class), eq("https://api.typesafe.ai/v1/models"), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, body, Map.of("x-request-id", "req-1"))));

    ModelListPage page = adapter(CostExtractor.NONE).listModels().join();

    assertEquals(200, page.statusCode());
    assertEquals("req-1", page.requestId());
    assertEquals("jev-latest", page.models().get(0).name());
    assertEquals("The latest iteration of TypeSafe's System One Model: Jev", page.models().get(0).description());
    assertEquals("2026-09-10T18:38:01.391457+00:00", page.models().get(0).releaseDate());
    assertEquals("jev-preview", page.models().get(1).name());
    assertEquals("2026-09-10T18:39:06.057655+00:00", page.models().get(1).releaseDate());
  }

  @Test
  void listsModelsFromConfiguredApiVersion() {
    String body = "{\"models\":[{\"name\":\"jev-latest\"}]}";
    when(transport.send(any(HttpConstants.Method.class), eq("https://api.typesafe.ai/v2/models"), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, body, Map.of())));

    SystemOneAdapter adapter = new SystemOneAdapter("typesafe", "https://api.typesafe.ai/", "/v2/", "default-model",
        Capabilities.full(true), "key", Map.of(), CostExtractor.NONE, RequestIdExtractor.NONE, transport);

    assertEquals(List.of("jev-latest"), adapter.listModels().join().models().stream().map(ModelCard::name).toList());
  }

  @Test
  void evaluatesFromConfiguredApiVersion() {
    String body = "{\"answers\":{\"q\":{\"type\":\"noul\",\"noul\":0.9}}}";
    when(transport.send(any(HttpConstants.Method.class), eq("https://api.typesafe.ai/v2/systemone"), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, body, Map.of())));

    SystemOneAdapter adapter = new SystemOneAdapter("typesafe", "https://api.typesafe.ai/", "/v2/", "default-model",
        Capabilities.full(true), "key", Map.of(), CostExtractor.NONE, RequestIdExtractor.NONE, transport);

    assertEquals(0.9, adapter.evaluate(request()).join().answers().get("q").get("noul").asDouble(), 1e-9);
  }

  @Test
  void listModelsIsUnsupportedWhenCapabilityAbsent() {
    SystemOneAdapter adapter = new SystemOneAdapter("compatible", "https://gw", "v1", "m", Capabilities.full(false),
        null, Map.of(), CostExtractor.NONE, RequestIdExtractor.NONE, transport);

    CompletionException thrown = assertThrows(CompletionException.class, () -> adapter.listModels().join());
    ModuleException cause = assertInstanceOf(ModuleException.class, thrown.getCause());
    assertEquals(TypeSafeErrorType.UNSUPPORTED_BY_PROVIDER, cause.getType());
  }

  @Test
  void listsOpenRouterDataArrayOntoTypeSafeContract() {
    String body = "{\"data\":[{" + "\"id\":\"typesafe/jev-router\"," + "\"name\":\"TypeSafe: Jev Router\","
        + "\"description\":\"Jev through OpenRouter\"," + "\"created\":1725989881" + "},{" + "\"id\":\"same-as-label\","
        + "\"name\":\"same-as-label\"," + "\"description\":\"No separate display label\"," + "\"created\":1725989882"
        + "}],\"total_count\":2,\"links\":{\"next\":null}}";
    when(transport.send(any(HttpConstants.Method.class), eq("https://openrouter.ai/api/v1/models"), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, body, Map.of())));

    SystemOneAdapter adapter = new SystemOneAdapter("openrouter", "https://openrouter.ai/api/", "v1",
        "~typesafe/jev-latest", Capabilities.full(true), "key", Map.of(), CostExtractor.OPENROUTER,
        RequestIdExtractor.OPENROUTER, transport);

    ModelListPage page = adapter.listModels().join();

    assertEquals(200, page.statusCode());
    assertNull(page.requestId());
    assertEquals(2, page.models().size());
    assertEquals("typesafe/jev-router", page.models().get(0).name());
    assertEquals("Jev through OpenRouter", page.models().get(0).description());
    assertEquals("2024-09-10T17:38:01Z", page.models().get(0).releaseDate());
    assertEquals("TypeSafe: Jev Router", page.models().get(0).displayName());
    assertEquals("same-as-label", page.models().get(1).name());
    assertNull(page.models().get(1).displayName());
  }

  @Test
  void listsModelsEmptyWhenBodyHasNeitherModelsNorData() {
    when(transport.send(any(HttpConstants.Method.class), eq("https://api.typesafe.ai/v1/models"), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, "{\"ok\":true}", Map.of())));

    ModelListPage page = adapter(CostExtractor.NONE).listModels().join();

    assertEquals(200, page.statusCode());
    assertEquals(List.of(), page.models());
  }

  @Test
  void typesafeListModelsDoesNotSetDisplayName() {
    String body = "{\"models\":[{\"name\":\"jev-latest\",\"description\":\"Latest\","
        + "\"release_date\":\"2026-09-10T18:38:01.391457+00:00\"}]}";
    when(transport.send(any(HttpConstants.Method.class), eq("https://api.typesafe.ai/v1/models"), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, body, Map.of())));

    ModelCard card = adapter(CostExtractor.NONE).listModels().join().models().get(0);

    assertEquals("jev-latest", card.name());
    assertNull(card.displayName());
  }
}
