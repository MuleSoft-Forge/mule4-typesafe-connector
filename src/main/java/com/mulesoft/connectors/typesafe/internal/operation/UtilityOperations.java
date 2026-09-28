package com.mulesoft.connectors.typesafe.internal.operation;

import org.mule.sdk.api.annotation.Alias;
import org.mule.sdk.api.annotation.error.Throws;
import org.mule.sdk.api.annotation.metadata.OutputResolver;
import org.mule.sdk.api.annotation.metadata.TypeResolver;
import org.mule.sdk.api.annotation.param.Config;
import org.mule.sdk.api.annotation.param.Connection;
import org.mule.sdk.api.annotation.param.Content;
import org.mule.sdk.api.annotation.param.MediaType;
import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.display.DisplayName;
import org.mule.sdk.api.annotation.param.display.Placement;
import org.mule.sdk.api.annotation.param.display.Summary;
import org.mule.sdk.api.annotation.values.OfValues;
import org.mule.sdk.api.exception.ModuleException;
import org.mule.sdk.api.runtime.operation.Result;
import org.mule.sdk.api.runtime.process.CompletionCallback;

import com.mulesoft.connectors.typesafe.api.attributes.CapabilitiesAttributes;
import com.mulesoft.connectors.typesafe.api.attributes.ModelListAttributes;
import com.mulesoft.connectors.typesafe.api.attributes.ModelListCall;
import com.mulesoft.connectors.typesafe.internal.config.TypeSafeConfiguration;
import com.mulesoft.connectors.typesafe.internal.connection.TypeSafeConnection;
import com.mulesoft.connectors.typesafe.internal.error.DecisionErrorTypeProvider;
import com.mulesoft.connectors.typesafe.internal.error.TypeSafeErrorType;
import com.mulesoft.connectors.typesafe.internal.http.HttpErrorMapper;
import com.mulesoft.connectors.typesafe.internal.http.ProviderHttpException;
import com.mulesoft.connectors.typesafe.internal.metadata.CapabilitiesAttributesResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.CapabilitiesInputAttributesResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.CapabilitiesInputResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.CapabilitiesOutputResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.ModelListAttributesResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.ModelListInputAttributesResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.ModelListInputResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.ModelListOutputResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.QuestionSetValidationAttributesResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.QuestionSetValidationInputAttributesResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.QuestionSetValidationOutputResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.QuestionsInputResolver;
import com.mulesoft.connectors.typesafe.internal.provider.Capabilities;
import com.mulesoft.connectors.typesafe.internal.provider.ModelCard;
import com.mulesoft.connectors.typesafe.internal.provider.ModelListPage;
import com.mulesoft.connectors.typesafe.internal.provider.ProviderAdapter;
import com.mulesoft.connectors.typesafe.internal.questionset.QuestionSet;
import com.mulesoft.connectors.typesafe.internal.questionset.QuestionSetLoader;
import com.mulesoft.connectors.typesafe.internal.util.Json;
import com.mulesoft.connectors.typesafe.internal.validation.QuestionSetValidator;
import com.mulesoft.connectors.typesafe.internal.validation.ValidationResult;
import com.mulesoft.connectors.typesafe.internal.value.QuestionSetValueProvider;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Connection operations and the local question-set check. {@code get-capabilities} answers from connection metadata.
 * {@code list-models} calls every connected route that can list models. {@code validate-question-set} checks a
 * question-set document on this machine and does not read the connection.
 */
public class UtilityOperations {

  /**
   * Answers a question you would otherwise have to remember: what can this connection do? The connection is the input.
   * There is no message body and no call to TypeSafe or any other host.
   * <p>
   * It looks at the primary route on the connection, then each fallback, and writes one JSON object per route. route is
   * the name (openrouter, typesafe, vercel, cloudflare, compatible, mock). primary is true only for the first route.
   * capabilities says which question types that route accepts (supportsNoul, supportsChoice, supportsScore), whether it
   * returns confidence, whether it can list models, and the ceilings maxChoiceOptions and maxScoreLevels. Those values
   * are fixed for the route type you picked on the connection. They are not fetched from the vendor.
   * <p>
   * Use it before a billed call, to skip a question type the route cannot run. attributes.count is how many routes were
   * written.
   */
  @Alias("get-capabilities")
  @DisplayName("[Util] Connection Get Capabilities")
  @MediaType(MediaType.APPLICATION_JSON)
  @OutputResolver(output = CapabilitiesOutputResolver.class, attributes = CapabilitiesAttributesResolver.class)
  public Result<InputStream, CapabilitiesAttributes> getCapabilities(@Connection TypeSafeConnection connection,
      @Optional @Content @TypeResolver(CapabilitiesInputResolver.class) @DisplayName("Input payload") @Placement(tab = "Advanced", order = 1) @Summary("Unused. Route Capabilities sends no body.") InputStream inputPayload,
      @Optional @TypeResolver(CapabilitiesInputAttributesResolver.class) @DisplayName("Input attributes") @Placement(tab = "Advanced", order = 2) @Summary("Unused. Route Capabilities reads no incoming message attributes.") Object inputAttributes) {
    ArrayNode payload = Json.mapper().createArrayNode();
    ProviderAdapter primary = connection.primary();
    payload.add(routeNode(primary.routeName(), true, primary.capabilities()));
    for (ProviderAdapter fallback : connection.fallbacks()) {
      payload.add(routeNode(fallback.routeName(), false, fallback.capabilities()));
    }
    byte[] body = Json.write(payload).getBytes(StandardCharsets.UTF_8);
    return Result.<InputStream, CapabilitiesAttributes>builder().output(new ByteArrayInputStream(body))
        .attributes(new CapabilitiesAttributes(payload.size())).build();
  }

  private static ObjectNode routeNode(String route, boolean primary, Capabilities capabilities) {
    ObjectNode node = Json.object();
    node.put("route", route);
    node.put("primary", primary);
    ObjectNode fields = node.putObject("capabilities");
    fields.put("supportsNoul", capabilities.isSupportsNoul());
    fields.put("supportsChoice", capabilities.isSupportsChoice());
    fields.put("supportsScore", capabilities.isSupportsScore());
    fields.put("returnsConfidence", capabilities.isReturnsConfidence());
    fields.put("supportsModelList", capabilities.isSupportsModelList());
    fields.put("supportsStructuredInstructions", capabilities.isSupportsStructuredInstructions());
    fields.put("maxChoiceOptions", capabilities.getMaxChoiceOptions());
    fields.put("maxScoreLevels", capabilities.getMaxScoreLevels());
    return node;
  }

  /**
   * Lists the models available on the connected routes, primary route first. The payload is a JSON array. Each entry
   * has name, description, and release_date from <code>GET /{apiVersion}/models</code>, plus route. Each route adapter
   * normalizes its provider's response and scopes broad provider catalogs to that route's TypeSafe model namespace;
   * labels that differ from the callable id appear as display_name. Routes that cannot enumerate models are skipped. If
   * none can, the operation raises TYPESAFE:UNSUPPORTED_BY_PROVIDER.
   * <p>
   * This call sends no body and reads no incoming message attributes. Output attributes are count, and calls with
   * route, statusCode, and requestId.
   * <p>
   * See <a href="https://docs.typesafe.ai/models">https://docs.typesafe.ai/models</a>.
   */
  @Alias("list-models")
  @DisplayName("[Util] Connection List Models")
  @MediaType(MediaType.APPLICATION_JSON)
  @OutputResolver(output = ModelListOutputResolver.class, attributes = ModelListAttributesResolver.class)
  @Throws(DecisionErrorTypeProvider.class)
  public void listModels(@Connection TypeSafeConnection connection,
      @Optional @Content @TypeResolver(ModelListInputResolver.class) @DisplayName("Input payload") @Placement(tab = "Advanced", order = 1) @Summary("Unused. GET /{apiVersion}/models sends no body.") InputStream inputPayload,
      @Optional @TypeResolver(ModelListInputAttributesResolver.class) @DisplayName("Input attributes") @Placement(tab = "Advanced", order = 2) @Summary("Unused. List Models reads no incoming message attributes.") Object inputAttributes,
      CompletionCallback<InputStream, ModelListAttributes> callback) {
    List<ProviderAdapter> adapters = new ArrayList<>();
    adapters.add(connection.primary());
    adapters.addAll(connection.fallbacks());

    List<ProviderAdapter> supporting = new ArrayList<>();
    for (ProviderAdapter adapter : adapters) {
      if (adapter.capabilities().isSupportsModelList()) {
        supporting.add(adapter);
      }
    }
    if (supporting.isEmpty()) {
      callback.error(
          new ModuleException("No connected route can enumerate models", TypeSafeErrorType.UNSUPPORTED_BY_PROVIDER));
      return;
    }

    List<CompletableFuture<RouteModels>> futures = new ArrayList<>();
    for (ProviderAdapter adapter : supporting) {
      futures.add(adapter.listModels().thenApply(page -> new RouteModels(adapter.routeName(), page)));
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((ignored, error) -> {
      if (error != null) {
        callback.error(toTerminal(unwrap(error)));
        return;
      }
      ArrayNode models = Json.mapper().createArrayNode();
      List<ModelListCall> calls = new ArrayList<>();
      for (CompletableFuture<RouteModels> future : futures) {
        RouteModels routeModels = future.join();
        ModelListPage page = routeModels.page();
        calls.add(new ModelListCall(routeModels.route(), page.statusCode(), page.requestId()));
        for (ModelCard card : page.models()) {
          ObjectNode item = models.addObject();
          putText(item, "name", card.name());
          putText(item, "description", card.description());
          putText(item, "release_date", card.releaseDate());
          putText(item, "route", routeModels.route());
          if (card.displayName() != null) {
            putText(item, "display_name", card.displayName());
          }
        }
      }
      byte[] body = Json.write(models).getBytes(StandardCharsets.UTF_8);
      callback.success(Result.<InputStream, ModelListAttributes>builder().output(new ByteArrayInputStream(body))
          .attributes(new ModelListAttributes(models.size(), calls)).build());
    });
  }

  /**
   * Checks a question set on this machine, before any billed call. There is no HTTP call, so there is no API version
   * and the connection is not read. This is a check of the question-set document, not of the route.
   * <p>
   * Supply exactly one input. Questions is the message payload: a JSON object of question id to question. Question set
   * names a classpath file whose questions object is checked instead. Each question needs type (noul, choice, or score)
   * and instructions. Choice options, yes/no meanings, and score levels go under criteria.
   * <p>
   * The payload comes back as JSON with valid, errors, and warnings. errors are the hard limits from the TypeSafe API:
   * a choice has at most 255 options, a score has 2 to 10 levels, and options, levels, and yes/no criteria belong under
   * criteria. warnings flag a legal set that is likely to behave poorly: a choice with no no-match option, a duplicate
   * or empty option description, a score with fewer than 3 levels, or a choice with more than 20 options.
   * <p>
   * The operation reads no incoming message attributes and writes none.
   * <p>
   * The limits are the ones documented at <a href="https://docs.typesafe.ai/api">https://docs.typesafe.ai/api</a>.
   */
  @Alias("validate-question-set")
  @DisplayName("[Util] Validate Question Set")
  @MediaType(MediaType.APPLICATION_JSON)
  @OutputResolver(output = QuestionSetValidationOutputResolver.class, attributes = QuestionSetValidationAttributesResolver.class)
  @Throws(DecisionErrorTypeProvider.class)
  public Result<InputStream, Void> validateQuestionSet(@Config TypeSafeConfiguration config,
      @Optional @Content @TypeResolver(QuestionsInputResolver.class) @DisplayName("Questions") @Summary("JSON object of question id to question. Leave empty when Question set names a classpath file.") InputStream questions,
      @Optional @DisplayName("Question set") @OfValues(QuestionSetValueProvider.class) @Summary("Classpath question-set file. Leave empty when Questions carries the object.") String questionSet,
      @Optional @TypeResolver(QuestionSetValidationInputAttributesResolver.class) @DisplayName("Input attributes") @Placement(tab = "Advanced", order = 1) @Summary("Unused. Validate reads no incoming message attributes.") Object inputAttributes) {
    JsonNode questionsNode = resolveQuestions(config, questions, questionSet);
    Map<String, String> noMatchOptions = noMatchOptions(questionsNode);

    ValidationResult result = new QuestionSetValidator().validate(questionsNode, noMatchOptions);

    ObjectNode payload = Json.object();
    payload.put("valid", result.isValid());
    ArrayNode errors = payload.putArray("errors");
    result.getErrors().forEach(errors::add);
    ArrayNode warnings = payload.putArray("warnings");
    result.getWarnings().forEach(warnings::add);
    byte[] body = Json.write(payload).getBytes(StandardCharsets.UTF_8);
    return Result.<InputStream, Void>builder().output(new ByteArrayInputStream(body)).build();
  }

  private static JsonNode resolveQuestions(TypeSafeConfiguration config, InputStream questions, String questionSet) {
    boolean hasInline = questions != null;
    boolean hasFile = questionSet != null && !questionSet.isBlank();
    if (hasInline == hasFile) {
      throw new ModuleException("Supply exactly one of 'questions' or 'questionSet'",
          TypeSafeErrorType.INVALID_QUESTION_SET);
    }
    if (hasFile) {
      QuestionSet set = QuestionSetLoader.load(config.getDefaultQuestionSetsLocation(), questionSet);
      return set.questions();
    }
    try {
      return Json.read(questions);
    } catch (RuntimeException e) {
      throw new ModuleException("Could not parse questions as JSON", TypeSafeErrorType.INVALID_QUESTION_SET, e);
    }
  }

  /**
   * Collects each question's declared {@code noMatchOption}, so the "missing no-match" warning is not raised for it.
   */
  private static Map<String, String> noMatchOptions(JsonNode questions) {
    Map<String, String> noMatch = new HashMap<>();
    if (questions == null || !questions.isObject()) {
      return noMatch;
    }
    Iterator<Map.Entry<String, JsonNode>> it = questions.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> entry = it.next();
      JsonNode value = entry.getValue();
      if (value.isObject() && value.hasNonNull("noMatchOption")) {
        noMatch.put(entry.getKey(), value.get("noMatchOption").asText());
      }
    }
    return noMatch;
  }

  private static Throwable toTerminal(Throwable cause) {
    if (cause instanceof ModuleException) {
      return cause;
    }
    if (cause instanceof ProviderHttpException) {
      ProviderHttpException httpError = (ProviderHttpException) cause;
      return HttpErrorMapper.toException(httpError.status(), httpError.body());
    }
    return new ModuleException("Could not list models: " + cause.getMessage(), TypeSafeErrorType.CONNECTIVITY, cause);
  }

  private static Throwable unwrap(Throwable error) {
    if (error instanceof CompletionException && error.getCause() != null) {
      return error.getCause();
    }
    return error;
  }

  private static void putText(ObjectNode node, String key, String value) {
    if (value == null) {
      node.putNull(key);
    } else {
      node.put(key, value);
    }
  }

  /** A route's model-list page, carried through the async merge. */
  private static final class RouteModels {

    private final String route;
    private final ModelListPage page;

    RouteModels(String route, ModelListPage page) {
      this.route = route;
      this.page = page;
    }

    String route() {
      return route;
    }

    ModelListPage page() {
      return page;
    }
  }
}
