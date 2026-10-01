package com.mulesoft.connectors.typesafe.internal.policy;

import com.mulesoft.connectors.typesafe.internal.util.Json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyEvaluatorTest {

  private static final String CHOICE_POLICY = "{\"team\":{\"minProbability\":0.55,\"minMargin\":0.15,\"onNoMatch\":\"REVIEW\"}}";

  private static ObjectNode choice(double top, double margin, boolean noMatch) {
    String json = "{\"team\":{\"type\":\"choice\",\"choice\":\"billing\",\"probabilities\":{\"billing\":" + top
        + ",\"technical\":" + (top - margin) + "},\"derived\":{\"margin\":" + margin + ",\"isNoMatch\":" + noMatch
        + "}}}";
    return (ObjectNode) Json.read(json);
  }

  @Test
  void choiceThatClearsThresholdsIsAcceptedAndSetsRouteKey() {
    ObjectNode result = PolicyEvaluator.evaluate(choice(0.7, 0.5, false), Json.read(CHOICE_POLICY));
    assertEquals("ACCEPT", result.get("action").asText());
    assertEquals("billing", result.get("routeKey").asText());
    assertTrue(result.get("reasons").isEmpty());
  }

  @Test
  void choiceBelowMarginIsReviewedWithReason() {
    ObjectNode result = PolicyEvaluator.evaluate(choice(0.6, 0.05, false), Json.read(CHOICE_POLICY));
    assertEquals("REVIEW", result.get("action").asText());
    boolean mentionsMargin = false;
    for (JsonNode reason : result.get("reasons")) {
      mentionsMargin |= reason.asText().contains("margin");
    }
    assertTrue(mentionsMargin, "expected a margin reason in " + result.get("reasons"));
  }

  @Test
  void noMatchChoiceFollowsOnNoMatch() {
    ObjectNode result = PolicyEvaluator.evaluate(choice(0.9, 0.8, true), Json.read(CHOICE_POLICY));
    assertEquals("REVIEW", result.get("action").asText());
  }

  @Test
  void noulAcceptReviewReject() {
    JsonNode policy = Json.read("{\"urgent\":{\"acceptAbove\":0.7,\"rejectBelow\":0.3}}");
    assertEquals("ACCEPT", action(noul(0.8), policy));
    assertEquals("REVIEW", action(noul(0.5), policy));
    assertEquals("REJECT", action(noul(0.2), policy));
  }

  @Test
  void scoreLevelsDriveAction() {
    JsonNode policy = Json.read("{\"sentiment\":{\"acceptLevels\":[\"3\",\"4\",\"5\"],\"reviewLevels\":[\"2\"]}}");
    assertEquals("ACCEPT", action(score(4), policy));
    assertEquals("REVIEW", action(score(2), policy));
    assertEquals("REJECT", action(score(1), policy));
  }

  @Test
  void mostCautiousOutcomeWinsAcrossQuestions() {
    ObjectNode decision = choice(0.7, 0.5, false);
    decision.set("urgent", Json.read("{\"type\":\"noul\",\"noul\":0.1}").deepCopy());
    JsonNode policy = Json.read(
        "{\"team\":{\"minProbability\":0.55,\"minMargin\":0.15},\"urgent\":{\"acceptAbove\":0.7,\"rejectBelow\":0.3}}");
    ObjectNode result = PolicyEvaluator.evaluate(decision, policy);
    assertEquals("REJECT", result.get("action").asText());
  }

  @Test
  void canonicalEvaluatePayloadIsUnwrappedToItsAnswers() {
    ObjectNode payload = Json.object();
    payload.put("model", "mock");
    payload.set("answers", choice(0.6, 0.05, false));
    // Without unwrapping, the {model, answers} wrapper would judge no questions and default to ACCEPT.
    assertEquals("REVIEW", PolicyEvaluator.evaluate(payload, Json.read(CHOICE_POLICY)).get("action").asText());
  }

  @Test
  void singleAnswerIsWrappedAndJudged() {
    JsonNode decision = Json.read("{\"type\":\"noul\",\"noul\":0.1}");
    JsonNode policy = Json.read("{\"result\":{\"acceptAbove\":0.7,\"rejectBelow\":0.3}}");
    assertEquals("REJECT", PolicyEvaluator.evaluate(decision, policy).get("action").asText());
  }

  @Test
  void emptyDecisionIsReviewedNotAccepted() {
    ObjectNode result = PolicyEvaluator.evaluate(Json.object(), Json.read(CHOICE_POLICY));
    assertEquals("REVIEW", result.get("action").asText());
    assertTrue(result.get("reasons").toString().contains("no answers to judge"));
  }

  @Test
  void decisionWithNoAnswerObjectsIsReviewedEvenWithoutPolicy() {
    assertEquals("REVIEW", action(Json.read("{\"foo\":\"bar\"}"), Json.object()));
  }

  @Test
  void policiedQuestionWithoutAnAnswerIsReviewed() {
    JsonNode policy = Json
        .read("{\"team\":{\"minProbability\":0.55},\"urgent\":{\"acceptAbove\":0.7,\"rejectBelow\":0.3}}");
    ObjectNode result = PolicyEvaluator.evaluate(choice(0.7, 0.5, false), policy);
    assertEquals("REVIEW", result.get("action").asText());
    assertEquals("ACCEPT", result.at("/perQuestion/team/action").asText());
    assertEquals("REVIEW", result.at("/perQuestion/urgent/action").asText());
    assertEquals("urgent: no answer to judge", result.at("/perQuestion/urgent/reasons/0").asText());
  }

  @Test
  void shortcutAnswerJudgedAgainstAQuestionSetPolicyIsReviewed() {
    // A single answer is judged as "result", so a file policy keyed by real question ids finds nothing to judge.
    JsonNode decision = Json.read("{\"type\":\"noul\",\"noul\":0.9}");
    assertEquals("REVIEW", action(decision, Json.read("{\"urgent\":{\"acceptAbove\":0.7}}")));
  }

  @Test
  void ruleThatDoesNotFitTheAnswerTypeIsReviewed() {
    JsonNode policy = Json.read("{\"team\":{\"acceptAbove\":0.7}}");
    ObjectNode result = PolicyEvaluator.evaluate(choice(0.9, 0.8, false), policy);
    assertEquals("REVIEW", result.get("action").asText());
    assertEquals("team: rule does not fit a 'choice' answer", result.at("/perQuestion/team/reasons/0").asText());
  }

  @Test
  void ruleWithAMisspeltKeyIsReviewed() {
    JsonNode policy = Json.read("{\"team\":{\"minProbabilty\":0.99}}");
    assertEquals("REVIEW", action(choice(0.9, 0.8, false), policy));
  }

  @Test
  void answerOfUnknownTypeIsReviewedWhenARuleNamesIt() {
    JsonNode decision = Json.read("{\"x\":{\"type\":\"rating\",\"value\":3}}");
    assertEquals("REVIEW", action(decision, Json.read("{\"x\":{}}")));
    assertEquals("ACCEPT", action(decision, Json.object()));
  }

  @Test
  void perOptionChoiceCanDemandHigherConfidenceOrMapToReview() {
    String answers = "{\"team\":{\"type\":\"choice\",\"choice\":\"other\",\"confidence\":0.6,"
        + "\"probabilities\":{\"billing\":0.2,\"other\":0.6},\"derived\":{\"margin\":0.4,\"isNoMatch\":false}}}";
    JsonNode soft = Json.read("{\"team\":{\"minProbability\":0.55,\"options\":{\"other\":{\"action\":\"REVIEW\"}}}}");
    assertEquals("REVIEW", action(Json.read(answers), soft));

    JsonNode strict = Json.read("{\"team\":{\"minProbability\":0.55,\"options\":{\"other\":{\"minConfidence\":0.8}}}}");
    assertEquals("REVIEW", action(Json.read(answers), strict));

    JsonNode ok = Json.read("{\"team\":{\"minProbability\":0.55,\"options\":{\"other\":{\"minConfidence\":0.5}}}}");
    assertEquals("ACCEPT", action(Json.read(answers), ok));
  }

  @Test
  void otherOptionsAppliesWhenTheChoiceIsNotListed() {
    String answers = "{\"team\":{\"type\":\"choice\",\"choice\":\"account\",\"confidence\":0.9,"
        + "\"probabilities\":{\"account\":0.9},\"derived\":{\"margin\":0.9,\"isNoMatch\":false}}}";
    JsonNode policy = Json
        .read("{\"team\":{\"options\":{\"billing\":{\"action\":\"ACCEPT\"}},\"otherOptions\":\"REVIEW\"}}");
    assertEquals("REVIEW", action(Json.read(answers), policy));
  }

  @Test
  void threeBandNoulMapsYesNoAndUncertainSeparately() {
    JsonNode policy = Json.read(
        "{\"urgent\":{\"yesAbove\":0.7,\"noBelow\":0.3,\"onYes\":\"ACCEPT\",\"onNo\":\"ACCEPT\",\"onUncertain\":\"REVIEW\"}}");
    assertEquals("ACCEPT", action(noul(0.9), policy));
    assertEquals("ACCEPT", action(noul(0.1), policy));
    assertEquals("REVIEW", action(noul(0.5), policy));
  }

  @Test
  void threeBandNoulCanEscalateAClearYes() {
    JsonNode policy = Json
        .read("{\"urgent\":{\"yesAbove\":0.7,\"noBelow\":0.3,\"onYes\":\"REVIEW\",\"onNo\":\"ACCEPT\"}}");
    assertEquals("REVIEW", action(noul(0.95), policy));
    assertEquals("ACCEPT", action(noul(0.1), policy));
  }

  private static ObjectNode noul(double value) {
    return (ObjectNode) Json.read("{\"urgent\":{\"type\":\"noul\",\"noul\":" + value + "}}");
  }

  private static ObjectNode score(int level) {
    return (ObjectNode) Json
        .read("{\"sentiment\":{\"type\":\"score\",\"score\":\"" + level + "\",\"derived\":{\"level\":" + level + "}}}");
  }

  private static String action(JsonNode decision, JsonNode policy) {
    return PolicyEvaluator.evaluate(decision, policy).get("action").asText();
  }
}
