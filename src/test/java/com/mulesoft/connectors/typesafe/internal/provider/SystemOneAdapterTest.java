package com.mulesoft.connectors.typesafe.internal.provider;

import org.mule.runtime.http.api.HttpConstants;
import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.typesafe.internal.domain.DecisionRequest;
import com.mulesoft.connectors.typesafe.internal.domain.DecisionResponse;
import com.mulesoft.connectors.typesafe.internal.domain.ImageAsset;
import com.mulesoft.connectors.typesafe.internal.error.TypeSafeErrorType;
import com.mulesoft.connectors.typesafe.internal.http.HttpTransport;
import com.mulesoft.connectors.typesafe.internal.http.ProviderHttpException;
import com.mulesoft.connectors.typesafe.internal.http.RawHttpResponse;
import com.mulesoft.connectors.typesafe.internal.support.TestImages;
import com.mulesoft.connectors.typesafe.internal.util.Json;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
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
  void connectionTestTargetUsesConfiguredVersionAndDefaultModelEndpoint() {
    SystemOneAdapter adapter = new SystemOneAdapter("typesafe", "https://api.typesafe.ai/", "/v2/", "jev-latest",
        Capabilities.full(true), "key", Map.of(), CostExtractor.NONE, RequestIdExtractor.NONE, transport);

    assertEquals("POST https://api.typesafe.ai/v2/systemone", adapter.connectionTestTarget());
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
  void listsOnlyTypeSafeOpenRouterModelsOntoCanonicalContract() {
    String body = "{\"data\":[{" + "\"id\":\"openai/gpt-4\"," + "\"name\":\"OpenAI: GPT-4\","
        + "\"description\":\"Not a TypeSafe model\"," + "\"created\":1725989880" + "},{"
        + "\"id\":\"typesafe/jev-router\"," + "\"name\":\"TypeSafe: Jev Router\","
        + "\"description\":\"Jev through OpenRouter\"," + "\"created\":1725989881" + "},{"
        + "\"id\":\"anthropic/claude-3\"," + "\"name\":\"Anthropic: Claude 3\","
        + "\"description\":\"Not a TypeSafe model\"," + "\"created\":1725989882"
        + "}],\"total_count\":3,\"links\":{\"next\":null}}";
    when(transport.send(any(HttpConstants.Method.class), eq("https://openrouter.ai/api/v1/models"), anyMap(), any()))
        .thenReturn(CompletableFuture
            .completedFuture(new RawHttpResponse(200, body, Map.of("cf-ray", "a4222f2e1d545621-ARN"))));

    SystemOneAdapter adapter = new OpenRouterAdapter("openrouter", "https://openrouter.ai/api/", "v1",
        "~typesafe/jev-latest", Capabilities.full(true), "key", Map.of(), CostExtractor.OPENROUTER,
        RequestIdExtractor.OPENROUTER, transport);

    ModelListPage page = adapter.listModels().join();

    assertEquals(200, page.statusCode());
    assertEquals("a4222f2e1d545621-ARN", page.requestId());
    assertEquals(1, page.models().size());
    assertEquals("typesafe/jev-router", page.models().get(0).name());
    assertEquals("Jev through OpenRouter", page.models().get(0).description());
    assertEquals("2024-09-10T17:38:01Z", page.models().get(0).releaseDate());
    assertEquals("TypeSafe: Jev Router", page.models().get(0).displayName());
  }

  @Test
  void compatibleDataArrayIsNotRestrictedToOpenRouterVendorScope() {
    String body = "{\"data\":[{\"id\":\"vendor/model-a\",\"name\":\"Model A\",\"created\":1725989881},"
        + "{\"id\":\"vendor/model-b\",\"name\":\"Model B\",\"created\":1725989882}]}";
    when(transport.send(any(HttpConstants.Method.class), eq("https://gateway.example/v1/models"), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, body, Map.of())));

    SystemOneAdapter adapter = new SystemOneAdapter("compatible", "https://gateway.example", "v1", "vendor/model-a",
        Capabilities.full(true), "key", Map.of(), CostExtractor.NONE, RequestIdExtractor.NONE, transport);

    assertEquals(List.of("vendor/model-a", "vendor/model-b"),
        adapter.listModels().join().models().stream().map(ModelCard::name).toList());
  }

  @Test
  void listsModelsEmptyWhenBodyHasNeitherModelsNorData() {
    when(transport.send(any(HttpConstants.Method.class), eq("https://api.typesafe.ai/v1/models"), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, "{\"ok\":true}", Map.of())));

    ModelListPage page = adapter(CostExtractor.NONE).listModels().join();

    assertEquals(200, page.statusCode());
    assertEquals(List.of(), page.models());
  }

  private static final String BOX_BODY = "{\"model\":\"jev-1.14.0\",\"answers\":{\"q\":{\"type\":\"box\","
      + "\"box\":{\"top\":0.29,\"bottom\":0.69,\"left\":0.24,\"right\":0.75},\"confidence\":0.78}},"
      + "\"usage\":{\"input_tokens\":614,\"output_tokens\":37}}";

  private DecisionRequest imageRequest(byte[] png) {
    ObjectNode questions = (ObjectNode) Json
        .read("{\"q\":{\"type\":\"box\",\"instructions\":\"the red rectangle in `photo`\"}}");
    return new DecisionRequest(Json.read("{\"photo\":\"ref-1\"}"), null, questions, Map.of(), null, null,
        List.of(new ImageAsset("ref-1", png, "png")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void sendsImagesAsMultipartWithTheImagesHeader() {
    byte[] png = TestImages.png(320, 320);
    ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
    when(transport.send(eq(HttpConstants.Method.POST), eq("https://api.typesafe.ai/v1/systemone"), headers.capture(),
        body.capture())).thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, BOX_BODY, Map.of())));

    DecisionResponse response = adapter(CostExtractor.NONE).evaluate(imageRequest(png)).join();

    assertEquals(0.75, response.answers().path("q").path("box").path("right").asDouble(), 1e-9);
    assertEquals("true", headers.getValue().get(SystemOneAdapter.IMAGES_HEADER));
    String contentType = headers.getValue().get("Content-Type");
    assertTrue(contentType.startsWith("multipart/form-data; boundary="));
    String boundary = contentType.substring(contentType.indexOf('=') + 1);

    String text = new String(body.getValue(), StandardCharsets.ISO_8859_1);
    assertTrue(text.startsWith("--" + boundary + "\r\nContent-Disposition: form-data; name=\"request\"\r\n"
        + "Content-Type: application/json\r\n\r\n"), "request must be the first part");
    int jsonStart = text.indexOf("\r\n\r\n") + 4;
    String json = text.substring(jsonStart, text.indexOf("\r\n--" + boundary, jsonStart));
    JsonNode request = Json.read(json);
    assertEquals("ref-1", request.path("state").path("photo").asText());
    assertEquals("image", request.path("assets").path("ref-1").path("type").asText());
    assertEquals("png", request.path("assets").path("ref-1").path("format").asText());
    assertEquals("image-0", request.path("assets").path("ref-1").path("part").asText());
    assertFalse(request.path("assets").path("ref-1").has("data_base64"), "bytes travel as a file part, not base64");

    assertTrue(text.contains("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image-0\"; "
        + "filename=\"image-0\"\r\nContent-Type: image/png\r\n\r\n"));
    assertTrue(text.endsWith("\r\n--" + boundary + "--\r\n"));
    int fileStart = text.indexOf("Content-Type: image/png\r\n\r\n") + "Content-Type: image/png\r\n\r\n".length();
    byte[] sent = Arrays.copyOfRange(body.getValue(), fileStart, fileStart + png.length);
    assertArrayEquals(png, sent, "the image part must carry the original bytes");
  }

  @Test
  @SuppressWarnings("unchecked")
  void textOnlyRequestsStayJsonWithoutTheImagesHeader() {
    ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
    when(transport.send(any(), any(), headers.capture(), body.capture())).thenReturn(CompletableFuture
        .completedFuture(new RawHttpResponse(200, "{\"answers\":{\"q\":{\"type\":\"noul\",\"noul\":0.9}}}", Map.of())));

    adapter(CostExtractor.NONE).evaluate(request()).join();

    assertEquals("application/json", headers.getValue().get("Content-Type"));
    assertFalse(headers.getValue().containsKey(SystemOneAdapter.IMAGES_HEADER));
    assertFalse(Json.read(new String(body.getValue(), StandardCharsets.UTF_8)).has("assets"));
  }

  @Test
  void routeWithoutImageSupportFailsLocally() {
    SystemOneAdapter adapter = new OpenRouterAdapter("openrouter", "https://openrouter.ai/api", "v1",
        "~typesafe/jev-latest", Capabilities.full(true).withoutImages(), "key", Map.of(), CostExtractor.OPENROUTER,
        RequestIdExtractor.OPENROUTER, transport);

    CompletionException thrown = assertThrows(CompletionException.class,
        () -> adapter.evaluate(imageRequest(TestImages.png(320, 320))).join());
    ModuleException cause = assertInstanceOf(ModuleException.class, thrown.getCause());
    assertEquals(TypeSafeErrorType.UNSUPPORTED_BY_PROVIDER, cause.getType());
    verifyNoInteractions(transport);
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
