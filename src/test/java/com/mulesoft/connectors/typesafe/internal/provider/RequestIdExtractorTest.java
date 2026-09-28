package com.mulesoft.connectors.typesafe.internal.provider;

import com.mulesoft.connectors.typesafe.internal.http.RawHttpResponse;
import com.mulesoft.connectors.typesafe.internal.util.Json;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestIdExtractorTest {

  @Test
  void openRouterPrefersGenerationIdHeader() {
    RawHttpResponse response = new RawHttpResponse(200, "{}",
        Map.of("x-generation-id", "gen-from-header", "x-request-id", "ignored"));
    JsonNode body = Json.read("{\"id\":\"gen-from-body\"}");

    assertEquals("gen-from-header", RequestIdExtractor.OPENROUTER.extract(response, body));
  }

  @Test
  void openRouterFallsBackToBodyIdWhenHeaderMissing() {
    RawHttpResponse response = new RawHttpResponse(200, "{}", Map.of("cf-ray", "edge-fallback"));
    JsonNode body = Json.read("{\"id\":\"gen-from-body\"}");

    assertEquals("gen-from-body", RequestIdExtractor.OPENROUTER.extract(response, body));
  }

  @Test
  void openRouterFallsBackToCloudflareRayForNonGenerationEndpoints() {
    RawHttpResponse response = new RawHttpResponse(200, "{}", Map.of("cf-ray", "a4222f2e1d545621-ARN"));
    JsonNode body = Json.read("{\"answers\":{}}");

    assertEquals("a4222f2e1d545621-ARN", RequestIdExtractor.OPENROUTER.extract(response, body));
  }

  @Test
  void openRouterReturnsNullWhenNoCorrelationIdIsPresent() {
    RawHttpResponse response = new RawHttpResponse(200, "{}", Map.of("x-request-id", "not-used"));

    assertNull(RequestIdExtractor.OPENROUTER.extract(response, Json.read("{\"answers\":{}}")));
  }

  @Test
  void typeSafeHeaderExtractorReadsTypesafeRequestId() {
    RawHttpResponse response = new RawHttpResponse(200, "{}", Map.of("x-typesafe-request-id", "req_abc"));

    assertEquals("req_abc", RequestIdExtractor.header("x-typesafe-request-id").extract(response, Json.object()));
  }
}
