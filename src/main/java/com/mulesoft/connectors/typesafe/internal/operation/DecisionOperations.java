package com.mulesoft.connectors.typesafe.internal.operation;

import org.mule.sdk.api.annotation.Alias;
import org.mule.sdk.api.annotation.error.Throws;
import org.mule.sdk.api.annotation.metadata.MetadataKeyId;
import org.mule.sdk.api.annotation.metadata.OutputResolver;
import org.mule.sdk.api.annotation.metadata.TypeResolver;
import org.mule.sdk.api.annotation.param.Config;
import org.mule.sdk.api.annotation.param.Connection;
import org.mule.sdk.api.annotation.param.Content;
import org.mule.sdk.api.annotation.param.MediaType;
import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.ParameterGroup;
import org.mule.sdk.api.annotation.param.display.DisplayName;
import org.mule.sdk.api.annotation.param.display.Summary;
import org.mule.sdk.api.annotation.values.OfValues;
import org.mule.sdk.api.exception.ModuleException;
import org.mule.sdk.api.runtime.operation.Result;
import org.mule.sdk.api.runtime.process.CompletionCallback;

import com.mulesoft.connectors.typesafe.api.attributes.DecisionAttributes;
import com.mulesoft.connectors.typesafe.internal.cache.DecisionCache;
import com.mulesoft.connectors.typesafe.internal.config.TypeSafeConfiguration;
import com.mulesoft.connectors.typesafe.internal.connection.TypeSafeConnection;
import com.mulesoft.connectors.typesafe.internal.domain.DecisionRequest;
import com.mulesoft.connectors.typesafe.internal.domain.ImageAsset;
import com.mulesoft.connectors.typesafe.internal.engine.BudgetGuard;
import com.mulesoft.connectors.typesafe.internal.engine.DecisionContext;
import com.mulesoft.connectors.typesafe.internal.engine.DecisionEngine;
import com.mulesoft.connectors.typesafe.internal.engine.DecisionOutcome;
import com.mulesoft.connectors.typesafe.internal.error.DecisionErrorTypeProvider;
import com.mulesoft.connectors.typesafe.internal.error.TypeSafeErrorType;
import com.mulesoft.connectors.typesafe.internal.metadata.BoxAnswerOutputResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.ChoiceAnswerOutputResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.DecisionAttributesResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.DecisionOutputResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.ImagesInputResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.JsonInputResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.NoulAnswerOutputResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.NullInputAttributesResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.QuestionsInputResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.ScoreAnswerOutputResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.SelectCandidateInputResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.SelectCandidateOutputResolver;
import com.mulesoft.connectors.typesafe.internal.stats.DecisionStatsRecorder;
import com.mulesoft.connectors.typesafe.internal.util.Json;
import com.mulesoft.connectors.typesafe.internal.value.QuestionSetValueProvider;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The billed decision operations. {@code evaluate} sends a full question set; {@code ask-noul}, {@code choose} and
 * {@code score} are single-question shortcuts; {@code select-candidate} turns upstream rows into a dynamic Choice.
 * Every operation is non-blocking (a {@link CompletionCallback} plus {@code sendAsync}), builds a canonical
 * {@link DecisionRequest}, hands it to the {@link DecisionEngine}, and streams JSON back with
 * {@link DecisionAttributes}. Retries run on a runtime scheduler, so no I/O thread ever sleeps.
 */
public class DecisionOperations {

  private static final String NO_MATCH_OPTION = "noMatchOption";
  private static final String SHORTCUT_KEY = "result";
  private static final String CANDIDATE_KEY = "candidate";
  private static final String CANDIDATE_NO_MATCH = "__no_match__";
  private static final int MAX_CANDIDATES = 254;

  static final String IMAGES_SUMMARY = "Reference to PNG/JPEG binary, e.g. #[{ (vars.photoRef): vars.photo }]. "
      + "Write each reference into the state, instructions or criteria. Sent as multipart.";

  /**
   * Evaluates one decision against the connected route. Supply the questions inline ({@code questions}) or by
   * referencing a classpath question-set file ({@code questionSet}); exactly one is required. The output is the answers
   * object (each answer enriched with a {@code derived} block); {@code attributes} carry provider, usage, cost, timing
   * and a {@code traceEntry}. Questions may be noul, choice, score or box.
   * <p>
   * Hosted System One routes call {@code POST /{apiVersion}/systemone}. See
   * <a href="https://docs.typesafe.ai/api">https://docs.typesafe.ai/api</a>.
   * <p>
   * Images (experimental): write a reference string into the state, instructions or criteria and map it to a PNG or
   * JPEG binary under Images. The call becomes a multipart {@code POST /{apiVersion}/systemone} (JSON in part
   * {@code request}, one file part per image) with {@code TypeSafe-Danger-Zone-Images: true}; a box question sends the
   * same header. Only the typesafe, compatible and mock routes carry images. See
   * <a href="https://docs.typesafe.ai/images">https://docs.typesafe.ai/images</a>.
   */
  @Alias("evaluate")
  @DisplayName("[Decide] Evaluate")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @OutputResolver(output = DecisionOutputResolver.class, attributes = DecisionAttributesResolver.class)
  @Throws(DecisionErrorTypeProvider.class)
  public void evaluate(@Config TypeSafeConfiguration config, @Connection TypeSafeConnection connection,
      @Content @TypeResolver(JsonInputResolver.class) InputStream state,
      @Optional @Content(primary = false) @TypeResolver(QuestionsInputResolver.class) @DisplayName("Questions") InputStream questions,
      @Optional @DisplayName("Question set") @MetadataKeyId @OfValues(QuestionSetValueProvider.class) String questionSet,
      @Optional String questionSetId, @Optional String questionSetVersion,
      @Optional @Content(primary = false) @TypeResolver(ImagesInputResolver.class) @DisplayName("Images") @Summary(IMAGES_SUMMARY) Map<String, InputStream> images,
      @Optional @TypeResolver(NullInputAttributesResolver.class) @DisplayName("Input attributes") Object inputAttributes,
      @ParameterGroup(name = "Request options") RequestOptions options,
      CompletionCallback<InputStream, DecisionAttributes> callback) {
    DecisionRequest request;
    try {
      JsonNode stateNode = Json.read(state);
      QuestionResolution.Resolved resolved = QuestionResolution.resolve(config, questions, questionSet, questionSetId,
          questionSetVersion);
      request = new DecisionRequest(stateNode, options.getModelOverride(), resolved.questions, resolved.noMatchOptions,
          resolved.questionSetId, resolved.questionSetVersion, ImageInputs.read(images));
    } catch (ModuleException e) {
      callback.error(e);
      return;
    } catch (RuntimeException e) {
      callback.error(
          new ModuleException("Could not parse the decision input as JSON", TypeSafeErrorType.INVALID_QUESTION_SET, e));
      return;
    }
    run(config, connection, request, options, callback, DecisionOperations::wrapAnswers);
  }

  /** Wraps the enriched answers as the §7 canonical payload {@code {model, answers}} for the full evaluate op. */
  private static ObjectNode wrapAnswers(DecisionOutcome outcome) {
    ObjectNode payload = Json.object();
    if (outcome.attributes().getModel() == null) {
      payload.putNull("model");
    } else {
      payload.put("model", outcome.attributes().getModel());
    }
    payload.set("answers", outcome.payload());
    return payload;
  }

  /**
   * Asks a single yes/no (Noul) question and returns just that answer, so a flow reads {@code payload.noul} (the
   * probability of "yes") directly.
   * <p>
   * Calls {@code POST /{apiVersion}/systemone} on hosted System One routes. See
   * <a href="https://docs.typesafe.ai/api">https://docs.typesafe.ai/api</a>.
   * <p>
   * Images (experimental): map references used in the state, instructions or criteria to PNG/JPEG binaries under
   * Images, and the call becomes a multipart {@code POST /{apiVersion}/systemone} with
   * {@code TypeSafe-Danger-Zone-Images: true}. Typesafe, compatible and mock routes only. See
   * <a href="https://docs.typesafe.ai/images">https://docs.typesafe.ai/images</a>.
   */
  @Alias("ask-noul")
  @DisplayName("[Decide] Ask Yes/No")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @OutputResolver(output = NoulAnswerOutputResolver.class, attributes = DecisionAttributesResolver.class)
  @Throws(DecisionErrorTypeProvider.class)
  public void askNoul(@Config TypeSafeConfiguration config, @Connection TypeSafeConnection connection,
      @Content @TypeResolver(JsonInputResolver.class) InputStream state,
      @DisplayName("Instructions") String instructions, @Optional String criteriaTrue, @Optional String criteriaFalse,
      @Optional @Content(primary = false) @TypeResolver(ImagesInputResolver.class) @DisplayName("Images") @Summary(IMAGES_SUMMARY) Map<String, InputStream> images,
      @Optional @TypeResolver(NullInputAttributesResolver.class) @DisplayName("Input attributes") Object inputAttributes,
      @ParameterGroup(name = "Request options") RequestOptions options,
      CompletionCallback<InputStream, DecisionAttributes> callback) {
    ObjectNode question = Json.object();
    question.put("type", "noul");
    question.put("instructions", instructions);
    if (criteriaTrue != null || criteriaFalse != null) {
      ObjectNode criteria = question.putObject("criteria");
      if (criteriaTrue != null) {
        criteria.put("true", criteriaTrue);
      }
      if (criteriaFalse != null) {
        criteria.put("false", criteriaFalse);
      }
    }
    runShortcut(config, connection, state, question, new HashMap<>(), images, options, callback);
  }

  /**
   * Asks a single Choice question over a fixed set of options and returns just that answer, so a flow reads
   * {@code payload.choice} and {@code payload.derived}.
   * <p>
   * Calls {@code POST /{apiVersion}/systemone} on hosted System One routes. See
   * <a href="https://docs.typesafe.ai/api">https://docs.typesafe.ai/api</a>.
   * <p>
   * Images (experimental): an option description may be an image reference, so options can be defined by example
   * pictures. Map each reference to a PNG/JPEG binary under Images, and the call becomes a multipart {@code POST
   * /{apiVersion}/systemone} with {@code TypeSafe-Danger-Zone-Images: true}. Typesafe, compatible and mock routes only.
   * See <a href="https://docs.typesafe.ai/images">https://docs.typesafe.ai/images</a>.
   */
  @Alias("choose")
  @DisplayName("[Decide] Choose")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @OutputResolver(output = ChoiceAnswerOutputResolver.class, attributes = DecisionAttributesResolver.class)
  @Throws(DecisionErrorTypeProvider.class)
  public void choose(@Config TypeSafeConfiguration config, @Connection TypeSafeConnection connection,
      @Content @TypeResolver(JsonInputResolver.class) InputStream state,
      @DisplayName("Instructions") String instructions, @DisplayName("Options") Map<String, String> chooseOptions,
      @Optional String noMatchOption,
      @Optional @Content(primary = false) @TypeResolver(ImagesInputResolver.class) @DisplayName("Images") @Summary(IMAGES_SUMMARY) Map<String, InputStream> images,
      @Optional @TypeResolver(NullInputAttributesResolver.class) @DisplayName("Input attributes") Object inputAttributes,
      @ParameterGroup(name = "Request options") RequestOptions options,
      CompletionCallback<InputStream, DecisionAttributes> callback) {
    if (chooseOptions == null || chooseOptions.isEmpty()) {
      callback
          .error(new ModuleException("choose requires at least one option", TypeSafeErrorType.INVALID_QUESTION_SET));
      return;
    }
    ObjectNode question = Json.object();
    question.put("type", "choice");
    question.put("instructions", instructions);
    ObjectNode optionsNode = question.putObject("criteria");
    for (Map.Entry<String, String> entry : chooseOptions.entrySet()) {
      optionsNode.put(entry.getKey(), entry.getValue());
    }
    Map<String, String> noMatch = new HashMap<>();
    if (noMatchOption != null && !noMatchOption.isBlank()) {
      if (!optionsNode.has(noMatchOption)) {
        optionsNode.put(noMatchOption, "None of the options apply");
      }
      noMatch.put(SHORTCUT_KEY, noMatchOption);
    }
    runShortcut(config, connection, state, question, noMatch, images, options, callback);
  }

  /**
   * Asks a single Score question over ordered levels and returns just that answer, so a flow reads
   * {@code payload.score} and {@code payload.derived.level}.
   * <p>
   * Calls {@code POST /{apiVersion}/systemone} on hosted System One routes. See
   * <a href="https://docs.typesafe.ai/api">https://docs.typesafe.ai/api</a>.
   * <p>
   * Images (experimental): a level may be an image reference, so levels can be shown by example. Map each reference to
   * a PNG/JPEG binary under Images, and the call becomes a multipart {@code POST /{apiVersion}/systemone} with
   * {@code TypeSafe-Danger-Zone-Images: true}. Typesafe, compatible and mock routes only. See
   * <a href="https://docs.typesafe.ai/images">https://docs.typesafe.ai/images</a>.
   */
  @Alias("score")
  @DisplayName("[Decide] Score")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @OutputResolver(output = ScoreAnswerOutputResolver.class, attributes = DecisionAttributesResolver.class)
  @Throws(DecisionErrorTypeProvider.class)
  public void score(@Config TypeSafeConfiguration config, @Connection TypeSafeConnection connection,
      @Content @TypeResolver(JsonInputResolver.class) InputStream state,
      @DisplayName("Instructions") String instructions, @DisplayName("Levels") List<String> levels,
      @Optional @Content(primary = false) @TypeResolver(ImagesInputResolver.class) @DisplayName("Images") @Summary(IMAGES_SUMMARY) Map<String, InputStream> images,
      @Optional @TypeResolver(NullInputAttributesResolver.class) @DisplayName("Input attributes") Object inputAttributes,
      @ParameterGroup(name = "Request options") RequestOptions options,
      CompletionCallback<InputStream, DecisionAttributes> callback) {
    if (levels == null || levels.size() < 2) {
      callback.error(new ModuleException("score requires at least two levels", TypeSafeErrorType.INVALID_QUESTION_SET));
      return;
    }
    ObjectNode question = Json.object();
    question.put("type", "score");
    question.put("instructions", instructions);
    ArrayNode levelsNode = question.putArray("criteria");
    for (String level : levels) {
      levelsNode.add(level);
    }
    runShortcut(config, connection, state, question, new HashMap<>(), images, options, callback);
  }

  /**
   * Asks a single Box question, which finds where something is in an image, and returns just that answer, so a flow
   * reads {@code payload.box.top}, {@code .bottom}, {@code .left} and {@code .right} (fractions of the image's height
   * and width, from the top-left corner), {@code payload.confidence} (null when the model gives none) and
   * {@code payload.derived} ({@code width}, {@code height}, {@code centerX}, {@code centerY}, {@code area}). A box is
   * always returned, even when the target is absent, so pair it with Ask Yes/No when absence is possible.
   * <p>
   * Images is required: write a reference into the state (for example {@code {"photo": "<reference>"}}) or the
   * instructions and map it to a PNG/JPEG binary. Calls multipart {@code POST /{apiVersion}/systemone} (JSON in part
   * {@code request}, one file part per image) with {@code TypeSafe-Danger-Zone-Images: true}. Typesafe, compatible and
   * mock routes only; the TypeSafe API needs Jev 1.14 or later ({@code jev-latest} or {@code jev-preview}). See
   * <a href="https://docs.typesafe.ai/primitives/box">https://docs.typesafe.ai/primitives/box</a> and
   * <a href="https://docs.typesafe.ai/images">https://docs.typesafe.ai/images</a>.
   */
  @Alias("locate")
  @DisplayName("[Decide] Locate")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @OutputResolver(output = BoxAnswerOutputResolver.class, attributes = DecisionAttributesResolver.class)
  @Throws(DecisionErrorTypeProvider.class)
  public void locate(@Config TypeSafeConfiguration config, @Connection TypeSafeConnection connection,
      @Content @TypeResolver(JsonInputResolver.class) InputStream state,
      @DisplayName("Instructions") @Summary("What to find, and in which image, e.g. the coffee mug in `photo`.") String instructions,
      @Content(primary = false) @TypeResolver(ImagesInputResolver.class) @DisplayName("Images") @Summary(IMAGES_SUMMARY) Map<String, InputStream> images,
      @Optional @TypeResolver(NullInputAttributesResolver.class) @DisplayName("Input attributes") Object inputAttributes,
      @ParameterGroup(name = "Request options") RequestOptions options,
      CompletionCallback<InputStream, DecisionAttributes> callback) {
    ObjectNode question = Json.object();
    question.put("type", DecisionRequest.BOX);
    question.put("instructions", instructions);
    runShortcut(config, connection, state, question, new HashMap<>(), images, options, callback);
  }

  /**
   * Turns a list of upstream candidates (DB rows, Salesforce queues, …) into a dynamic Choice: each candidate becomes
   * an option keyed by {@code idField} and described by {@code labelField} / {@code descriptionField}. The payload
   * names the selected candidate object, its probability and confidence, whether it was a no-match, and the full
   * ranking.
   * <p>
   * Calls {@code POST /{apiVersion}/systemone} on hosted System One routes. See
   * <a href="https://docs.typesafe.ai/api">https://docs.typesafe.ai/api</a>.
   * <p>
   * Images (experimental): a candidate's label or description, or the query, may hold an image reference (for example a
   * product photo per candidate). Map each reference to a PNG/JPEG binary under Images, and the call becomes a
   * multipart {@code POST /{apiVersion}/systemone} with {@code TypeSafe-Danger-Zone-Images: true}. Typesafe, compatible
   * and mock routes only. See <a href="https://docs.typesafe.ai/images">https://docs.typesafe.ai/images</a>.
   */
  @Alias("select-candidate")
  @DisplayName("[Select] Candidate")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @OutputResolver(output = SelectCandidateOutputResolver.class, attributes = DecisionAttributesResolver.class)
  @Throws(DecisionErrorTypeProvider.class)
  public void selectCandidate(@Config TypeSafeConfiguration config, @Connection TypeSafeConnection connection,
      @Content @TypeResolver(SelectCandidateInputResolver.class) @DisplayName("Candidates") InputStream candidates,
      @Content(primary = false) @TypeResolver(JsonInputResolver.class) @DisplayName("Query") InputStream query,
      String idField, String labelField, @Optional String descriptionField,
      @Optional(defaultValue = "Which candidate best matches the request?") String instructions,
      @Optional(defaultValue = "true") boolean includeNoMatch,
      @Optional @Content(primary = false) @TypeResolver(ImagesInputResolver.class) @DisplayName("Images") @Summary(IMAGES_SUMMARY) Map<String, InputStream> images,
      @Optional @TypeResolver(NullInputAttributesResolver.class) @DisplayName("Input attributes") Object inputAttributes,
      @ParameterGroup(name = "Request options") RequestOptions options,
      CompletionCallback<InputStream, DecisionAttributes> callback) {
    List<JsonNode> candidateList;
    ObjectNode question;
    Map<String, String> noMatch = new HashMap<>();
    Map<String, JsonNode> byId = new LinkedHashMap<>();
    List<ImageAsset> imageAssets;
    try {
      candidateList = readArray(candidates);
      question = buildCandidateQuestion(candidateList, idField, labelField, descriptionField, instructions,
          includeNoMatch, byId, noMatch);
      imageAssets = ImageInputs.read(images);
    } catch (ModuleException e) {
      callback.error(e);
      return;
    } catch (RuntimeException e) {
      callback.error(
          new ModuleException("Could not parse candidates as a JSON array", TypeSafeErrorType.INVALID_QUESTION_SET, e));
      return;
    }

    JsonNode queryNode;
    try {
      queryNode = Json.read(query);
    } catch (RuntimeException e) {
      callback.error(new ModuleException("Could not parse query as JSON", TypeSafeErrorType.INVALID_STATE, e));
      return;
    }

    ObjectNode questions = Json.object();
    questions.set(CANDIDATE_KEY, question);
    DecisionRequest request = new DecisionRequest(queryNode, options.getModelOverride(), questions, noMatch, null, null,
        imageAssets);
    run(config, connection, request, options, callback, outcome -> candidatePayload(outcome, byId));
  }

  /**
   * Shared tail: validate against the config's cache → budget → engine path (section 4), then stream the payload the
   * caller shaped. A cache hit skips the engine entirely; the budget guard reserves one call before the engine runs and
   * raises {@code TYPESAFE:BUDGET_EXCEEDED} when a limit is hit; usage and stats are recorded once the engine
   * completes.
   */
  private void run(TypeSafeConfiguration config, TypeSafeConnection connection, DecisionRequest request,
      RequestOptions options, CompletionCallback<InputStream, DecisionAttributes> callback,
      Function<DecisionOutcome, ObjectNode> payloadFn) {
    try {
      ImageInputs.validate(request);
      requireImageRoute(connection, request);
    } catch (ModuleException e) {
      callback.error(e);
      return;
    }
    DecisionContext context = new DecisionContext(config.getPricePerMillionInputTokens(),
        options.isIncludeRawResponse(), options.getStep());

    DecisionCache cache = connection.cache();
    boolean useCache = config.isCacheEnabled() && options.isUseCache() && cache != null;
    String cacheKey = null;
    if (useCache) {
      cacheKey = cache.keyFor(connection.primary().routeName(), request.requestedModel(), request);
      java.util.Optional<DecisionOutcome> hit = cache.lookup(cacheKey, config.cacheTtlMillis());
      if (hit.isPresent()) {
        emit(callback, hit.get(), payloadFn);
        return;
      }
    }

    BudgetGuard budget = connection.budget();
    if (budget != null && config.isBudgetEnabled()) {
      try {
        budget.reserve(config.getBudgetMaxCallsPerWindow(), config.getBudgetMaxInputTokensPerWindow(),
            config.budgetWindowMillis());
      } catch (ModuleException e) {
        callback.error(e);
        return;
      }
    }

    final String key = cacheKey;
    final boolean cacheThis = useCache;
    connection.engine().evaluate(connection, request, context).whenComplete((outcome, error) -> {
      if (error != null) {
        callback.error(unwrap(error));
        return;
      }
      recordGovernance(config, connection, request, outcome, cacheThis, cache, key);
      emit(callback, outcome, payloadFn);
    });
  }

  /**
   * Fails before cache and budget when the request needs images and the primary route cannot carry them, so no budget
   * is reserved for a call that cannot be made.
   */
  private static void requireImageRoute(TypeSafeConnection connection, DecisionRequest request) {
    if (request.usesImages() && !connection.primary().capabilities().isSupportsImages()) {
      throw new ModuleException(
          "The " + connection.primary().routeName()
              + " route cannot send images or Box questions; connect with the TypeSafe or a compatible route",
          TypeSafeErrorType.UNSUPPORTED_BY_PROVIDER);
    }
  }

  /** After a successful engine call: bill the budget window, cache the result and fold it into the stats counters. */
  private static void recordGovernance(TypeSafeConfiguration config, TypeSafeConnection connection,
      DecisionRequest request, DecisionOutcome outcome, boolean cacheThis, DecisionCache cache, String cacheKey) {
    if (connection.budget() != null && config.isBudgetEnabled()) {
      Integer inputTokens = outcome.attributes().getUsage() == null
          ? null
          : outcome.attributes().getUsage().getInputTokens();
      connection.budget().recordUsage(inputTokens == null ? null : inputTokens.longValue(),
          config.budgetWindowMillis());
    }
    if (cacheThis && cache != null) {
      cache.put(cacheKey, outcome);
    }
    if (connection.stats() != null && config.isStatsEnabled()) {
      connection.stats().record(request, outcome, DecisionStatsRecorder.DEFAULT_WINDOW_SIZE);
    }
  }

  private void emit(CompletionCallback<InputStream, DecisionAttributes> callback, DecisionOutcome outcome,
      Function<DecisionOutcome, ObjectNode> payloadFn) {
    byte[] payload = Json.write(payloadFn.apply(outcome)).getBytes(StandardCharsets.UTF_8);
    callback.success(Result.<InputStream, DecisionAttributes>builder().output(new ByteArrayInputStream(payload))
        .attributes(outcome.attributes()).build());
  }

  private void runShortcut(TypeSafeConfiguration config, TypeSafeConnection connection, InputStream state,
      ObjectNode question, Map<String, String> noMatchOptions, Map<String, InputStream> images, RequestOptions options,
      CompletionCallback<InputStream, DecisionAttributes> callback) {
    JsonNode stateNode;
    try {
      stateNode = Json.read(state);
    } catch (RuntimeException e) {
      callback.error(new ModuleException("Could not parse state as JSON", TypeSafeErrorType.INVALID_STATE, e));
      return;
    }
    List<ImageAsset> imageAssets;
    try {
      imageAssets = ImageInputs.read(images);
    } catch (ModuleException e) {
      callback.error(e);
      return;
    }
    ObjectNode questions = Json.object();
    questions.set(SHORTCUT_KEY, question);
    DecisionRequest request = new DecisionRequest(stateNode, options.getModelOverride(), questions, noMatchOptions,
        null, null, imageAssets);
    run(config, connection, request, options, callback, outcome -> unwrapSingle(outcome));
  }

  /** Lifts the single shortcut answer out of the {@code answers} wrapper so flows read it as the payload. */
  private static ObjectNode unwrapSingle(DecisionOutcome outcome) {
    JsonNode answer = outcome.payload().get(SHORTCUT_KEY);
    return answer instanceof ObjectNode ? (ObjectNode) answer : outcome.payload();
  }

  private ObjectNode buildCandidateQuestion(List<JsonNode> candidates, String idField, String labelField,
      String descriptionField, String instructions, boolean includeNoMatch, Map<String, JsonNode> byId,
      Map<String, String> noMatch) {
    if (candidates.isEmpty()) {
      throw new ModuleException("select-candidate requires at least one candidate",
          TypeSafeErrorType.INVALID_QUESTION_SET);
    }
    if (candidates.size() > MAX_CANDIDATES) {
      throw new ModuleException(
          "select-candidate supports at most " + MAX_CANDIDATES + " candidates; got " + candidates.size(),
          TypeSafeErrorType.TOO_MANY_OPTIONS);
    }
    ObjectNode question = Json.object();
    question.put("type", "choice");
    question.put("instructions", instructions);
    ObjectNode optionsNode = question.putObject("criteria");
    for (JsonNode candidate : candidates) {
      JsonNode idNode = candidate.get(idField);
      String id = idNode != null && !idNode.isNull() ? idNode.asText() : null;
      if (id == null || id.isBlank()) {
        throw new ModuleException("Every candidate needs a non-empty '" + idField + "'",
            TypeSafeErrorType.INVALID_QUESTION_SET);
      }
      if (byId.containsKey(id)) {
        throw new ModuleException("Candidate ids must be unique; '" + id + "' is repeated",
            TypeSafeErrorType.INVALID_QUESTION_SET);
      }
      byId.put(id, candidate);
      optionsNode.put(id, describe(candidate, labelField, descriptionField));
    }
    if (includeNoMatch) {
      optionsNode.put(CANDIDATE_NO_MATCH, "None of the candidates apply");
      noMatch.put(CANDIDATE_KEY, CANDIDATE_NO_MATCH);
    }
    return question;
  }

  private static String describe(JsonNode candidate, String labelField, String descriptionField) {
    String label = text(candidate, labelField);
    String description = descriptionField == null ? null : text(candidate, descriptionField);
    if (description == null || description.isBlank()) {
      return label;
    }
    return label + " — " + description;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = field == null ? null : node.get(field);
    return value != null && !value.isNull() ? value.asText() : "";
  }

  private static ObjectNode candidatePayload(DecisionOutcome outcome, Map<String, JsonNode> byId) {
    JsonNode answerNode = outcome.payload().get(CANDIDATE_KEY);
    ObjectNode answer = answerNode instanceof ObjectNode ? (ObjectNode) answerNode : Json.object();
    String id = answer.path("choice").asText(null);
    boolean isNoMatch = answer.path("derived").path("isNoMatch").asBoolean(false) || CANDIDATE_NO_MATCH.equals(id);

    ObjectNode payload = Json.object();
    JsonNode selected = id != null && !isNoMatch ? byId.get(id) : null;
    if (selected != null) {
      payload.set("selected", selected.deepCopy());
    } else {
      payload.putNull("selected");
    }
    if (id == null) {
      payload.putNull("id");
    } else {
      payload.put("id", id);
    }
    JsonNode probabilities = answer.get("probabilities");
    double probability = id != null && probabilities != null ? probabilities.path(id).asDouble(0.0) : 0.0;
    payload.put("probability", probability);
    if (answer.hasNonNull("confidence")) {
      payload.put("confidence", answer.get("confidence").asDouble());
    } else {
      payload.putNull("confidence");
    }
    payload.put("isNoMatch", isNoMatch);
    ArrayNode ranking = payload.putArray("ranking");
    if (probabilities != null && probabilities.isObject()) {
      List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
      probabilities.fields().forEachRemaining(entries::add);
      entries
          .sort(Comparator.comparingDouble((Map.Entry<String, JsonNode> e) -> e.getValue().asDouble(0.0)).reversed());
      for (Map.Entry<String, JsonNode> entry : entries) {
        ranking.addObject().put("id", entry.getKey()).put("probability", entry.getValue().asDouble(0.0));
      }
    }
    return payload;
  }

  private static List<JsonNode> readArray(InputStream in) {
    JsonNode node = Json.read(in);
    if (node == null || !node.isArray()) {
      throw new ModuleException("candidates must be a JSON array of objects", TypeSafeErrorType.INVALID_QUESTION_SET);
    }
    List<JsonNode> list = new ArrayList<>();
    node.forEach(list::add);
    return list;
  }

  private static Throwable unwrap(Throwable error) {
    if (error instanceof CompletionException && error.getCause() != null) {
      return error.getCause();
    }
    return error;
  }
}
