package com.mulesoft.connectors.typesafe.internal.validation;

import com.mulesoft.connectors.typesafe.internal.questionset.QuestionSet;
import com.mulesoft.connectors.typesafe.internal.questionset.QuestionSetLoader;
import com.mulesoft.connectors.typesafe.internal.util.Json;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyValidatorTest {

  private static final JsonNode QUESTIONS = Json.read("{"
      + "\"team\":{\"type\":\"choice\",\"instructions\":\"Team?\",\"criteria\":{\"billing\":\"b\",\"other\":\"o\"},"
      + "\"noMatchOption\":\"other\"}," + "\"urgent\":{\"type\":\"noul\",\"instructions\":\"Urgent?\"},"
      + "\"sentiment\":{\"type\":\"score\",\"instructions\":\"Tone?\",\"criteria\":[\"neg\",\"neutral\",\"pos\"]}}");

  private final PolicyValidator validator = new PolicyValidator();

  private ValidationResult check(String policy) {
    return validator.validate(Json.read(policy), QUESTIONS);
  }

  private static boolean mentions(List<String> messages, String text) {
    return messages.stream().anyMatch(m -> m.contains(text));
  }

  @Test
  void wellFormedPolicyIsValid() {
    ValidationResult result = check("{\"team\":{\"minProbability\":0.55,\"minMargin\":0.15,\"onNoMatch\":\"review\"},"
        + "\"urgent\":{\"acceptAbove\":0.7},\"sentiment\":{\"acceptLevels\":[\"1\",2],\"reviewLevels\":[\"0\"]}}");
    assertTrue(result.isValid(), result.getErrors().toString());
    assertTrue(result.getWarnings().isEmpty(), result.getWarnings().toString());
  }

  @Test
  void absentPolicyIsValid() {
    assertTrue(validator.validate(null, QUESTIONS).isValid());
  }

  @Test
  void policyThatIsNotAnObjectIsAnError() {
    assertFalse(validator.validate(Json.read("[]"), QUESTIONS).isValid());
  }

  @Test
  void ruleForAMissingQuestionIsAnError() {
    ValidationResult result = check("{\"teem\":{\"minProbability\":0.5}}");
    assertEquals(1, result.getErrors().size());
    assertTrue(result.getErrors().get(0).startsWith("policy.teem: no question with this id"));
  }

  @Test
  void misspeltKeyIsAnError() {
    assertTrue(
        mentions(check("{\"team\":{\"minProbabilty\":0.5}}").getErrors(), "policy.team: unknown key 'minProbabilty'"));
  }

  @Test
  void keyForAnotherQuestionTypeIsAnError() {
    assertTrue(mentions(check("{\"team\":{\"acceptAbove\":0.7}}").getErrors(),
        "keys [acceptAbove] do not apply to a choice question"));
  }

  @Test
  void thresholdOutsideZeroToOneIsAnError() {
    assertTrue(mentions(check("{\"team\":{\"minProbability\":55}}").getErrors(), "minProbability must be a number"));
    assertTrue(mentions(check("{\"team\":{\"minMargin\":\"0.1\"}}").getErrors(), "minMargin must be a number"));
  }

  @Test
  void unknownActionIsAnError() {
    assertTrue(mentions(check("{\"team\":{\"onNoMatch\":\"ESCALATE\"}}").getErrors(), "onNoMatch must be one of"));
  }

  @Test
  void onNoMatchWithoutANoMatchOptionIsAWarning() {
    JsonNode questions = Json
        .read("{\"team\":{\"type\":\"choice\",\"instructions\":\"T\",\"criteria\":{\"a\":\"a\"}}}");
    ValidationResult result = validator.validate(Json.read("{\"team\":{\"onNoMatch\":\"REVIEW\"}}"), questions);
    assertTrue(result.isValid());
    assertTrue(mentions(result.getWarnings(), "onNoMatch never applies"));
  }

  @Test
  void invertedNoulThresholdsAreAnError() {
    assertTrue(mentions(check("{\"urgent\":{\"acceptAbove\":0.3,\"rejectBelow\":0.7}}").getErrors(),
        "rejectBelow must not be greater than acceptAbove"));
  }

  @Test
  void rejectBelowIsAWarning() {
    ValidationResult result = check("{\"urgent\":{\"acceptAbove\":0.7,\"rejectBelow\":0.3}}");
    assertTrue(result.isValid());
    assertTrue(mentions(result.getWarnings(), "rejects the whole decision"));
  }

  @Test
  void scoreRuleWithNoLevelsIsAnError() {
    assertTrue(mentions(check("{\"sentiment\":{\"minConfidence\":0.5}}").getErrors(), "every level is rejected"));
  }

  @Test
  void scoreLevelOutOfRangeIsAnError() {
    assertTrue(mentions(check("{\"sentiment\":{\"acceptLevels\":[\"3\"]}}").getErrors(),
        "level 3 is out of range; the question has levels 0 to 2"));
    assertTrue(mentions(check("{\"sentiment\":{\"acceptLevels\":[\"high\"]}}").getErrors(), "is not a level number"));
  }

  @Test
  void scoreLevelInBothListsIsAnError() {
    assertTrue(mentions(check("{\"sentiment\":{\"acceptLevels\":[\"1\",\"2\"],\"reviewLevels\":[\"1\"]}}").getErrors(),
        "levels [1] are in both"));
  }

  @Test
  void unlistedScoreLevelsAreAWarning() {
    ValidationResult result = check("{\"sentiment\":{\"acceptLevels\":[\"2\"]}}");
    assertTrue(result.isValid());
    assertTrue(mentions(result.getWarnings(), "levels [0, 1] are neither accepted nor reviewed"));
  }

  @Test
  void inlinePolicyIsCheckedForStructureOnly() {
    assertTrue(validator.validate(Json.read("{\"anything\":{\"minProbability\":0.5}}"), null).isValid());
    assertTrue(
        mentions(validator.validate(Json.read("{\"x\":{\"minMargin\":0.1,\"acceptAbove\":0.5}}"), null).getErrors(),
            "mixes keys from different question types"));
    assertTrue(mentions(validator.validate(Json.read("{\"x\":{\"minMargn\":0.1}}"), null).getErrors(),
        "unknown key 'minMargn'"));
  }

  @Test
  void shippedTicketTriageSampleIsValidWithNoWarnings() {
    QuestionSet set = QuestionSetLoader.load("questions/", "ticket-triage");
    ValidationResult result = validator.validate(set.policy(), set.questions());
    assertTrue(result.isValid(), result.getErrors().toString());
    assertTrue(result.getWarnings().isEmpty(), result.getWarnings().toString());
  }

  @Test
  void threeBandAndLegacyNoulMustNotMix() {
    assertTrue(mentions(check("{\"urgent\":{\"yesAbove\":0.7,\"acceptAbove\":0.7}}").getErrors(),
        "use either yesAbove/noBelow"));
  }

  @Test
  void perOptionUnknownKeyIsAnError() {
    assertTrue(mentions(check("{\"team\":{\"options\":{\"billing\":{\"minProbabilty\":0.5}}}}").getErrors(),
        "options.billing: unknown key 'minProbabilty'"));
  }

  @Test
  void otherOptionsMustBeAnAction() {
    assertTrue(
        mentions(check("{\"team\":{\"otherOptions\":\"ESCALATE\"}}").getErrors(), "otherOptions must be one of"));
  }
}
