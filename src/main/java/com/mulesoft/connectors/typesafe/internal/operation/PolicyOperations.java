package com.mulesoft.connectors.typesafe.internal.operation;

import org.mule.sdk.api.annotation.Alias;
import org.mule.sdk.api.annotation.error.Throws;
import org.mule.sdk.api.annotation.metadata.OutputResolver;
import org.mule.sdk.api.annotation.metadata.TypeResolver;
import org.mule.sdk.api.annotation.param.Config;
import org.mule.sdk.api.annotation.param.Content;
import org.mule.sdk.api.annotation.param.MediaType;
import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.display.DisplayName;
import org.mule.sdk.api.annotation.param.display.Placement;
import org.mule.sdk.api.annotation.param.display.Summary;
import org.mule.sdk.api.exception.ModuleException;
import org.mule.sdk.api.runtime.operation.Result;

import com.mulesoft.connectors.typesafe.internal.config.TypeSafeConfiguration;
import com.mulesoft.connectors.typesafe.internal.error.PolicyErrorTypeProvider;
import com.mulesoft.connectors.typesafe.internal.error.TypeSafeErrorType;
import com.mulesoft.connectors.typesafe.internal.metadata.JsonInputResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.NullInputAttributesResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.PolicyInputResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.PolicyResultAttributesResolver;
import com.mulesoft.connectors.typesafe.internal.metadata.PolicyResultOutputResolver;
import com.mulesoft.connectors.typesafe.internal.policy.PolicyEvaluator;
import com.mulesoft.connectors.typesafe.internal.questionset.QuestionSet;
import com.mulesoft.connectors.typesafe.internal.questionset.QuestionSetLoader;
import com.mulesoft.connectors.typesafe.internal.util.Json;
import com.mulesoft.connectors.typesafe.internal.validation.PolicyValidator;
import com.mulesoft.connectors.typesafe.internal.validation.ValidationResult;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The local governance operation. {@code apply-policy} turns a decision into an {@code ACCEPT}/{@code REVIEW}/
 * {@code REJECT} action plus a {@code routeKey} ready for a {@code <choice>} router. It calls no provider and takes no
 * connection; the work is a pure evaluation delegated to {@link PolicyEvaluator}.
 */
public class PolicyOperations {

  /**
   * Applies a policy to a decision. There is no HTTP call, so there is no API version and the connection is not read.
   * <p>
   * Decision is the message payload: the JSON written by Evaluate ({@code {model, answers}}), its answers object, or a
   * single shortcut answer. Supply the thresholds inline (Policy) or name a question-set file whose {@code policy}
   * block is used (Question set); exactly one is required.
   * <p>
   * The policy is validated before it is applied. A misspelt key, a rule for a question id that does not exist, a
   * threshold outside 0 to 1 or an out-of-range Score level raises {@code TYPESAFE:INVALID_QUESTION_SET}. A policy from
   * a file is checked against that file's questions; an inline policy is checked for structure only.
   * <p>
   * The evaluation fails closed. A decision with no answers, a policied question with no answer, or a rule that does
   * not fit its answer's type is {@code REVIEW}, never {@code ACCEPT}.
   * <p>
   * The payload is JSON with {@code action}, {@code routeKey}, {@code reasons} and {@code perQuestion}. The operation
   * reads no incoming message attributes and writes none. Optionally raises {@code TYPESAFE:BELOW_THRESHOLD} on
   * {@code REVIEW} or {@code TYPESAFE:REJECTED} on {@code REJECT} for flows that prefer error-based routing.
   * <p>
   * How to set thresholds by the cost of a wrong answer:
   * <a href="https://docs.typesafe.ai/confidence">https://docs.typesafe.ai/confidence</a>.
   */
  @Alias("apply-policy")
  @DisplayName("[Policy] Apply")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @OutputResolver(output = PolicyResultOutputResolver.class, attributes = PolicyResultAttributesResolver.class)
  @Throws(PolicyErrorTypeProvider.class)
  public Result<InputStream, Void> applyPolicy(@Config TypeSafeConfiguration config,
      @Content @TypeResolver(JsonInputResolver.class) InputStream decision,
      @Optional @Content(primary = false) @TypeResolver(PolicyInputResolver.class) @DisplayName("Policy") InputStream policy,
      @Optional @DisplayName("Question set") String questionSet,
      @Optional(defaultValue = "false") boolean raiseOnReview, @Optional(defaultValue = "false") boolean raiseOnReject,
      @Optional @TypeResolver(NullInputAttributesResolver.class) @DisplayName("Input attributes") @Placement(tab = "Advanced", order = 1) @Summary("Unused. Apply Policy reads no incoming message attributes.") Object inputAttributes) {
    JsonNode decisionNode = read(decision, "decision", TypeSafeErrorType.INVALID_QUESTION_SET);
    JsonNode policyNode = resolvePolicy(config, policy, questionSet);

    ObjectNode result = PolicyEvaluator.evaluate(decisionNode, policyNode);
    String action = result.path("action").asText("ACCEPT");
    if (raiseOnReject && "REJECT".equals(action)) {
      throw new ModuleException("Policy rejected the decision: " + result.path("reasons"), TypeSafeErrorType.REJECTED);
    }
    if (raiseOnReview && "REVIEW".equals(action)) {
      throw new ModuleException("Policy flagged the decision for review: " + result.path("reasons"),
          TypeSafeErrorType.BELOW_THRESHOLD);
    }
    byte[] payload = Json.write(result).getBytes(StandardCharsets.UTF_8);
    return Result.<InputStream, Void>builder().output(new ByteArrayInputStream(payload)).build();
  }

  /** Reads the policy from exactly one source and validates it, against the file's questions when there is a file. */
  private static JsonNode resolvePolicy(TypeSafeConfiguration config, InputStream policy, String questionSet) {
    boolean hasInline = policy != null;
    boolean hasFile = questionSet != null && !questionSet.isBlank();
    if (hasInline && hasFile) {
      throw new ModuleException("Supply either 'policy' or 'questionSet', not both",
          TypeSafeErrorType.INVALID_QUESTION_SET);
    }
    if (!hasInline && !hasFile) {
      throw new ModuleException("Supply one of 'policy' or 'questionSet'", TypeSafeErrorType.INVALID_QUESTION_SET);
    }
    JsonNode policyNode;
    JsonNode questions = null;
    if (hasInline) {
      policyNode = read(policy, "policy", TypeSafeErrorType.INVALID_QUESTION_SET);
    } else {
      QuestionSet set = QuestionSetLoader.load(config.getDefaultQuestionSetsLocation(), questionSet);
      if (set.policy() == null) {
        throw new ModuleException("Question-set file '" + questionSet + "' declares no 'policy' block",
            TypeSafeErrorType.INVALID_QUESTION_SET);
      }
      policyNode = set.policy();
      questions = set.questions();
    }
    ValidationResult validation = new PolicyValidator().validate(policyNode, questions);
    if (!validation.isValid()) {
      throw new ModuleException("Policy is invalid: " + String.join("; ", validation.getErrors()),
          TypeSafeErrorType.INVALID_QUESTION_SET);
    }
    return policyNode;
  }

  private static JsonNode read(InputStream in, String what, TypeSafeErrorType type) {
    try {
      return Json.read(in);
    } catch (RuntimeException e) {
      throw new ModuleException("Could not parse " + what + " as JSON", type, e);
    }
  }
}
