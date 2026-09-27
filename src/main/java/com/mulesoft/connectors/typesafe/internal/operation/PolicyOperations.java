package com.mulesoft.connectors.typesafe.internal.operation;

import org.mule.sdk.api.annotation.Alias;
import org.mule.sdk.api.annotation.error.Throws;
import org.mule.sdk.api.annotation.param.Config;
import org.mule.sdk.api.annotation.param.Content;
import org.mule.sdk.api.annotation.param.MediaType;
import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.display.DisplayName;
import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.typesafe.internal.config.TypeSafeConfiguration;
import com.mulesoft.connectors.typesafe.internal.error.DecisionErrorTypeProvider;
import com.mulesoft.connectors.typesafe.internal.error.TypeSafeErrorType;
import com.mulesoft.connectors.typesafe.internal.policy.PolicyEvaluator;
import com.mulesoft.connectors.typesafe.internal.questionset.QuestionSet;
import com.mulesoft.connectors.typesafe.internal.questionset.QuestionSetLoader;
import com.mulesoft.connectors.typesafe.internal.util.Json;

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
   * Applies a policy to a decision. Supply the thresholds inline ({@code policy}) or by naming a question-set file
   * whose {@code policy} block is used ({@code questionSet}); exactly one is required. Optionally raises
   * {@code TYPESAFE:BELOW_THRESHOLD} on {@code REVIEW} or {@code TYPESAFE:REJECTED} on {@code REJECT} for flows that
   * prefer error-based routing.
   */
  @Alias("apply-policy")
  @DisplayName("[Policy] Apply")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @Throws(DecisionErrorTypeProvider.class)
  public InputStream applyPolicy(@Config TypeSafeConfiguration config, @Content InputStream decision,
      @Optional @Content(primary = false) @DisplayName("Policy") InputStream policy,
      @Optional @DisplayName("Question set") String questionSet,
      @Optional(defaultValue = "false") boolean raiseOnReview,
      @Optional(defaultValue = "false") boolean raiseOnReject) {
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
    return new ByteArrayInputStream(payload);
  }

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
    if (hasInline) {
      return read(policy, "policy", TypeSafeErrorType.INVALID_QUESTION_SET);
    }
    QuestionSet set = QuestionSetLoader.load(config.getDefaultQuestionSetsLocation(), questionSet);
    if (set.policy() == null) {
      throw new ModuleException("Question-set file '" + questionSet + "' declares no 'policy' block",
          TypeSafeErrorType.INVALID_QUESTION_SET);
    }
    return set.policy();
  }

  private static JsonNode read(InputStream in, String what, TypeSafeErrorType type) {
    try {
      return Json.read(in);
    } catch (RuntimeException e) {
      throw new ModuleException("Could not parse " + what + " as JSON", type, e);
    }
  }
}
