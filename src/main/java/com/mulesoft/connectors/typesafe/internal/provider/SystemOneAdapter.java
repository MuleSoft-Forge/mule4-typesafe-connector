package com.mulesoft.connectors.typesafe.internal.provider;

import org.mule.runtime.http.api.HttpConstants;
import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.typesafe.internal.domain.DecisionRequest;
import com.mulesoft.connectors.typesafe.internal.domain.DecisionResponse;
import com.mulesoft.connectors.typesafe.internal.domain.ImageAsset;
import com.mulesoft.connectors.typesafe.internal.engine.RetryPolicy;
import com.mulesoft.connectors.typesafe.internal.error.TypeSafeErrorType;
import com.mulesoft.connectors.typesafe.internal.http.HttpTransport;
import com.mulesoft.connectors.typesafe.internal.http.MultipartBody;
import com.mulesoft.connectors.typesafe.internal.http.ProviderHttpException;
import com.mulesoft.connectors.typesafe.internal.http.RawHttpResponse;
import com.mulesoft.connectors.typesafe.internal.util.Json;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Adapter for every route that speaks the canonical TypeSafe {@code systemOne} contract: TypeSafe direct, OpenRouter,
 * Vercel AI Gateway and any compatible gateway. It is parameterised by base URL, model, cost extractor and request-id
 * strategy, so one class serves four routes. The Cloudflare route subclasses it to reshape the request and unwrap the
 * v4 envelope.
 */
public class SystemOneAdapter implements ProviderAdapter {

  /** Opt-in header for images and Box questions, documented at https://docs.typesafe.ai/images#turning-on-images. */
  public static final String IMAGES_HEADER = "TypeSafe-Danger-Zone-Images";

  private final String routeName;
  private final String baseUrl;
  private final String apiVersion;
  private final String defaultModel;
  private final Capabilities capabilities;
  private final String apiKey;
  private final Map<String, String> extraHeaders;
  private final CostExtractor costExtractor;
  private final RequestIdExtractor requestIdExtractor;
  private final HttpTransport transport;

  public SystemOneAdapter(String routeName, String baseUrl, String apiVersion, String defaultModel,
      Capabilities capabilities, String apiKey, Map<String, String> extraHeaders, CostExtractor costExtractor,
      RequestIdExtractor requestIdExtractor, HttpTransport transport) {
    this.routeName = routeName;
    this.baseUrl = trimTrailingSlash(baseUrl);
    this.apiVersion = normalizeVersion(apiVersion);
    this.defaultModel = defaultModel;
    this.capabilities = capabilities;
    this.apiKey = apiKey;
    this.extraHeaders = extraHeaders == null ? Map.of() : Map.copyOf(extraHeaders);
    this.costExtractor = costExtractor == null ? CostExtractor.NONE : costExtractor;
    this.requestIdExtractor = requestIdExtractor == null ? RequestIdExtractor.NONE : requestIdExtractor;
    this.transport = transport;
  }

  /** The base URL, trailing slash stripped, for subclasses that build their own endpoints. */
  protected final String baseUrl() {
    return baseUrl;
  }

  @Override
  public String routeName() {
    return routeName;
  }

  @Override
  public String connectionTestTarget() {
    return "POST " + endpoint(defaultModel);
  }

  @Override
  public Capabilities capabilities() {
    return capabilities;
  }

  /**
   * Sends {@code POST /{apiVersion}/systemone}. A request with images goes as {@code multipart/form-data}: the JSON
   * first as part {@code request}, then each image as a file part that its {@code assets} entry names. A request with
   * images or a Box question also sends {@value #IMAGES_HEADER}{@code : true}.
   */
  @Override
  public CompletableFuture<DecisionResponse> evaluate(DecisionRequest request) {
    if (request.usesImages() && !capabilities.isSupportsImages()) {
      return CompletableFuture.failedFuture(new ModuleException(
          "The " + routeName + " route cannot send images or Box questions; use the TypeSafe or a compatible route",
          TypeSafeErrorType.UNSUPPORTED_BY_PROVIDER));
    }
    String model = request.requestedModel() != null ? request.requestedModel() : defaultModel;
    String json = buildBody(request, model);
    Map<String, String> headers = jsonHeaders();
    if (request.usesImages()) {
      headers.put(IMAGES_HEADER, "true");
    }

    byte[] body;
    if (request.images().isEmpty()) {
      body = json.getBytes(StandardCharsets.UTF_8);
    } else {
      MultipartBody multipart = MultipartBody.withRequest(json);
      List<ImageAsset> images = request.images();
      for (int i = 0; i < images.size(); i++) {
        multipart.file(partName(i), images.get(i).contentType(), images.get(i).bytes());
      }
      headers.put("Content-Type", multipart.contentType());
      body = multipart.toBytes();
    }

    return transport.send(HttpConstants.Method.POST, endpoint(model), headers, body)
        .thenApply(response -> parse(response, model));
  }

  /** The multipart file-part name for the image at {@code index}; never {@code request}. */
  static String partName(int index) {
    return "image-" + index;
  }

  /**
   * The request's {@code assets} map: each reference names its file part and format. {@code null} when there are no
   * images, so the field is omitted.
   */
  protected static ObjectNode assetsNode(DecisionRequest request) {
    if (request.images().isEmpty()) {
      return null;
    }
    ObjectNode assets = Json.object();
    List<ImageAsset> images = request.images();
    for (int i = 0; i < images.size(); i++) {
      ImageAsset image = images.get(i);
      assets.putObject(image.reference()).put("type", "image").put("format", image.format()).put("part", partName(i));
    }
    return assets;
  }

  @Override
  public CompletableFuture<ModelListPage> listModels() {
    if (!capabilities.isSupportsModelList()) {
      return CompletableFuture.failedFuture(new ModuleException(
          "The " + routeName + " route does not support model listing", TypeSafeErrorType.UNSUPPORTED_BY_PROVIDER));
    }
    return transport.send(HttpConstants.Method.GET, baseUrl + "/" + apiVersion + "/models", jsonHeaders(), null)
        .thenApply(this::parseModels);
  }

  /** The configured path version, defaulting to {@code v1} when the connection leaves it blank. */
  private static String normalizeVersion(String apiVersion) {
    if (apiVersion == null || apiVersion.isBlank()) {
      return "v1";
    }
    String trimmed = apiVersion.trim();
    while (trimmed.startsWith("/")) {
      trimmed = trimmed.substring(1);
    }
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed.isBlank() ? "v1" : trimmed;
  }

  /** Headers shared by every request: auth (when keyed), plus JSON content negotiation and the route's extras. */
  private Map<String, String> jsonHeaders() {
    Map<String, String> headers = new HashMap<>(extraHeaders);
    if (apiKey != null && !apiKey.isBlank()) {
      headers.put("Authorization", "Bearer " + apiKey);
    }
    headers.put("Content-Type", "application/json");
    headers.put("Accept", "application/json");
    return headers;
  }

  /**
   * The configured decision endpoint, {@code POST /{apiVersion}/systemone}. Overridden by the Cloudflare adapter, which
   * posts to {@code {baseUrl}/{model}}.
   */
  protected String endpoint(String model) {
    return baseUrl + "/" + apiVersion + "/systemone";
  }

  /**
   * Route hook for provider-wide catalogs. The canonical TypeSafe and compatible routes accept every listed model;
   * adapters for broad multi-vendor gateways can restrict entries to their TypeSafe vendor namespace.
   */
  protected boolean includeListedModel(String id) {
    return true;
  }

  /** Builds the request body. Overridden by the Cloudflare adapter, which nests under {@code input}. */
  protected String buildBody(DecisionRequest request, String model) {
    ObjectNode node = Json.object();
    node.set("state", request.state());
    node.put("model", model);
    node.set("questions", request.questions());
    ObjectNode assets = assetsNode(request);
    if (assets != null) {
      node.set("assets", assets);
    }
    return Json.write(node);
  }

  private ModelListPage parseModels(RawHttpResponse response) {
    if (!response.isSuccess()) {
      OptionalLong retryAfter = RetryPolicy.parseRetryAfter(response.header("retry-after-ms"),
          response.header("retry-after"), Instant.now());
      throw new ProviderHttpException(response.status(), response.body(), retryAfter);
    }
    JsonNode root;
    try {
      root = Json.read(response.body());
    } catch (RuntimeException e) {
      throw new ModuleException("Model list body was not valid JSON", TypeSafeErrorType.INVALID_RESPONSE, e);
    }
    JsonNode unwrapped = unwrap(root);
    List<ModelCard> cards = parseModelCards(unwrapped);
    return new ModelListPage(cards, response.status(), requestIdExtractor.extract(response, unwrapped));
  }

  /**
   * Projects a models response onto the TypeSafe model-list contract. TypeSafe bodies use a {@code models} array with
   * {@code name} / {@code description} / {@code release_date}. OpenRouter bodies use a {@code data} array whose
   * callable id is {@code id}. Provider adapters may filter broad catalogs through {@link #includeListedModel(String)}.
   * The catalog label {@code name} becomes {@code display_name} when it differs from {@code id}, and unix
   * {@code created} becomes UTC ISO-8601 {@code release_date}.
   */
  private List<ModelCard> parseModelCards(JsonNode unwrapped) {
    List<ModelCard> cards = new ArrayList<>();
    JsonNode models = unwrapped.path("models");
    if (models.isArray()) {
      for (JsonNode model : models) {
        if (!model.isObject()) {
          continue;
        }
        String name = textOrNull(model.get("name"));
        if (name == null) {
          continue;
        }
        cards.add(new ModelCard(name, textOrNull(model.get("description")), textOrNull(model.get("release_date"))));
      }
      return cards;
    }
    JsonNode data = unwrapped.path("data");
    if (data.isArray()) {
      for (JsonNode model : data) {
        if (!model.isObject()) {
          continue;
        }
        String id = textOrNull(model.get("id"));
        if (id == null) {
          continue;
        }
        if (!includeListedModel(id)) {
          continue;
        }
        String catalogName = textOrNull(model.get("name"));
        String displayName = catalogName != null && !catalogName.equals(id) ? catalogName : null;
        cards.add(new ModelCard(id, textOrNull(model.get("description")), releaseDateFromCreated(model.get("created")),
            displayName));
      }
    }
    return cards;
  }

  /** OpenRouter {@code created} is unix seconds; TypeSafe uses an ISO-8601 {@code release_date} string. */
  private static String releaseDateFromCreated(JsonNode created) {
    if (created == null || !created.isNumber()) {
      return null;
    }
    return Instant.ofEpochSecond(created.asLong()).toString();
  }

  private static String textOrNull(JsonNode node) {
    return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
  }

  private DecisionResponse parse(RawHttpResponse response, String sentModel) {
    if (!response.isSuccess()) {
      OptionalLong retryAfter = RetryPolicy.parseRetryAfter(response.header("retry-after-ms"),
          response.header("retry-after"), Instant.now());
      throw new ProviderHttpException(response.status(), response.body(), retryAfter);
    }

    JsonNode root;
    try {
      root = Json.read(response.body());
    } catch (RuntimeException e) {
      throw new ModuleException("Response body was not valid JSON", TypeSafeErrorType.INVALID_RESPONSE, e);
    }
    JsonNode body = unwrap(root);

    JsonNode answers = body.get("answers");
    if (answers == null || !answers.isObject()) {
      throw new ModuleException("Response has no 'answers' object", TypeSafeErrorType.INVALID_RESPONSE);
    }

    BigDecimal cost = costExtractor.extract(body);
    String requestId = requestIdExtractor.extract(response, body);

    return DecisionResponse.builder().model(body.path("model").asText(sentModel)).requestedModel(sentModel)
        .answers((ObjectNode) answers).inputTokens(intOrNull(body.path("usage").path("input_tokens")))
        .outputTokens(intOrNull(body.path("usage").path("output_tokens"))).providerReportedCost(cost)
        .providerRequestId(requestId).rawBody(response.body()).build();
  }

  /** SystemOne responses are unwrapped already. Cloudflare overrides to peel off {@code result}. */
  protected JsonNode unwrap(JsonNode root) {
    return root;
  }

  private static Integer intOrNull(JsonNode node) {
    return node != null && node.isNumber() ? node.asInt() : null;
  }

  private static String trimTrailingSlash(String url) {
    if (url == null) {
      return null;
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
