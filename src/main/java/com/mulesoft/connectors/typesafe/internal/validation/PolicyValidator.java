package com.mulesoft.connectors.typesafe.internal.validation;

import com.mulesoft.connectors.typesafe.internal.policy.PolicyEvaluator;
import com.mulesoft.connectors.typesafe.internal.policy.PolicyRules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Checks a policy block before it is trusted to gate a flow. A policy mistake does not fail loudly at run time: a
 * misspelt key is ignored and a misspelt question id judges nothing, so these are errors here rather than surprises in
 * production.
 *
 * <p>
 * With the question set's {@code questions} supplied, each rule is checked against its question: the id must exist, the
 * keys must fit its type, and Score levels must be in range. Without them (an inline policy) only the rule's own
 * structure is checked, and a rule's type is inferred from its keys. Messages are prefixed {@code policy.<id>}.
 */
public final class PolicyValidator {

  private static final Set<String> PROBABILITY_KEYS = Set.of("minProbability", "minConfidence", "minMargin",
      "acceptAbove", "rejectBelow", "yesAbove", "noBelow");
  private static final Set<String> ACTION_KEYS = Set.of("onNoMatch", "otherOptions", "onYes", "onNo", "onUncertain");
  private static final Set<String> LEVEL_KEYS = Set.of("acceptLevels", "reviewLevels");
  private static final Set<String> ACTIONS = Set.of(PolicyEvaluator.Action.ACCEPT.name(),
      PolicyEvaluator.Action.REVIEW.name(), PolicyEvaluator.Action.REJECT.name());

  /**
   * Validates {@code policy}. {@code questions} is the question set's questions object, or {@code null} for an inline
   * policy with no questions to check against.
   */
  public ValidationResult validate(JsonNode policy, JsonNode questions) {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    if (policy == null || policy.isNull()) {
      return new ValidationResult(errors, warnings);
    }
    if (!policy.isObject()) {
      errors.add("policy must be an object mapping question id to rule");
      return new ValidationResult(errors, warnings);
    }
    validateRouteQuestion(policy, questions, errors);
    Iterator<Map.Entry<String, JsonNode>> it = policy.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> entry = it.next();
      if (PolicyRules.ROUTE_QUESTION.equals(entry.getKey())) {
        continue;
      }
      validateRule(entry.getKey(), entry.getValue(), questions, errors, warnings);
    }
    return new ValidationResult(errors, warnings);
  }

  private static void validateRouteQuestion(JsonNode policy, JsonNode questions, List<String> errors) {
    JsonNode routeQuestion = policy.get(PolicyRules.ROUTE_QUESTION);
    if (routeQuestion == null || routeQuestion.isNull()) {
      return;
    }
    if (!routeQuestion.isTextual() || routeQuestion.asText().isBlank()) {
      errors.add("policy.routeQuestion must be the id of a Choice question");
      return;
    }
    if (questions == null) {
      return;
    }
    String id = routeQuestion.asText();
    JsonNode question = questions.get(id);
    if (question == null || !question.isObject()) {
      errors.add("policy.routeQuestion '" + id + "' does not match a question id");
    } else if (!PolicyRules.CHOICE.equals(question.path("type").asText(""))) {
      errors.add("policy.routeQuestion '" + id + "' must name a choice question");
    }
  }

  private void validateRule(String id, JsonNode rule, JsonNode questions, List<String> errors, List<String> warnings) {
    String prefix = "policy." + id + ": ";
    if (rule == null || !rule.isObject()) {
      errors.add(prefix + "rule must be an object");
      return;
    }

    JsonNode question = questions == null ? null : questions.get(id);
    if (questions != null && (question == null || !question.isObject())) {
      errors.add(prefix + "no question with this id, so every decision would be REVIEW");
      return;
    }

    Set<String> known = new HashSet<>();
    PolicyRules.KEYS.values().forEach(known::addAll);
    boolean unknownKey = false;
    Iterator<String> names = rule.fieldNames();
    while (names.hasNext()) {
      String key = names.next();
      if (!known.contains(key)) {
        errors.add(prefix + "unknown key '" + key + "'");
        unknownKey = true;
      }
    }

    String type = question == null ? inferType(rule) : question.path("type").asText(null);
    if (question != null && PolicyRules.KEYS.containsKey(type)) {
      Set<String> misplaced = new TreeSet<>();
      rule.fieldNames().forEachRemaining(key -> {
        if (known.contains(key) && !PolicyRules.KEYS.get(type).contains(key)) {
          misplaced.add(key);
        }
      });
      if (!misplaced.isEmpty()) {
        errors.add(prefix + "keys " + misplaced + " do not apply to a " + type + " question");
      }
    } else if (question == null && !unknownKey && PolicyRules.typesFor(rule).isEmpty()) {
      errors.add(prefix + "rule mixes keys from different question types");
    }

    checkValues(prefix, rule, errors);
    if (PolicyRules.CHOICE.equals(type)) {
      checkChoice(prefix, rule, question, errors, warnings);
    } else if (PolicyRules.NOUL.equals(type)) {
      checkNoul(prefix, rule, errors, warnings);
    } else if (PolicyRules.SCORE.equals(type)) {
      checkScore(prefix, rule, question, errors, warnings);
    }
  }

  /** The single type an inline rule's keys fit, or {@code null} when they fit several or none. */
  private static String inferType(JsonNode rule) {
    Set<String> types = PolicyRules.typesFor(rule);
    return types.size() == 1 ? types.iterator().next() : null;
  }

  private static void checkValues(String prefix, JsonNode rule, List<String> errors) {
    for (String key : PROBABILITY_KEYS) {
      JsonNode value = rule.get(key);
      if (value != null && (!value.isNumber() || value.asDouble() < 0.0 || value.asDouble() > 1.0)) {
        errors.add(prefix + key + " must be a number from 0 to 1");
      }
    }
    for (String key : ACTION_KEYS) {
      JsonNode value = rule.get(key);
      if (value != null && (!value.isTextual() || !ACTIONS.contains(value.asText().trim().toUpperCase()))) {
        errors.add(prefix + key + " must be one of ACCEPT, REVIEW, REJECT");
      }
    }
    for (String key : LEVEL_KEYS) {
      JsonNode value = rule.get(key);
      if (value != null && !value.isArray()) {
        errors.add(prefix + key + " must be an array of level numbers");
      }
    }
  }

  private static void checkChoice(String prefix, JsonNode rule, JsonNode question, List<String> errors,
      List<String> warnings) {
    if (rule.has("onNoMatch") && question != null && !question.hasNonNull("noMatchOption")) {
      warnings.add(prefix + "onNoMatch never applies; the question declares no noMatchOption");
    }
    JsonNode options = rule.get("options");
    if (options == null) {
      return;
    }
    if (!options.isObject()) {
      errors.add(prefix + "options must be an object mapping option id to thresholds");
      return;
    }
    Set<String> knownOptions = new HashSet<>();
    if (question != null && question.path("criteria").isObject()) {
      question.get("criteria").fieldNames().forEachRemaining(knownOptions::add);
    }
    Iterator<Map.Entry<String, JsonNode>> it = options.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> entry = it.next();
      String optionId = entry.getKey();
      String optionPrefix = prefix + "options." + optionId + ": ";
      if (!knownOptions.isEmpty() && !knownOptions.contains(optionId)) {
        errors.add(optionPrefix + "not a criteria option on this question");
      }
      JsonNode optionRule = entry.getValue();
      if (optionRule == null || !optionRule.isObject()) {
        errors.add(optionPrefix + "must be an object");
        continue;
      }
      Iterator<String> keys = optionRule.fieldNames();
      while (keys.hasNext()) {
        String key = keys.next();
        if (!PolicyRules.OPTION_KEYS.contains(key)) {
          errors.add(optionPrefix + "unknown key '" + key + "'");
        }
      }
      checkValues(optionPrefix, optionRule, errors);
    }
  }

  private static void checkNoul(String prefix, JsonNode rule, List<String> errors, List<String> warnings) {
    boolean banded = PolicyRules.isBandedNoul(rule);
    boolean legacy = PolicyRules.isLegacyNoul(rule);
    if (banded && legacy) {
      errors.add(prefix + "use either yesAbove/noBelow (three-band) or acceptAbove/rejectBelow (legacy), not both");
      return;
    }
    if (banded) {
      JsonNode yesAbove = rule.get("yesAbove");
      JsonNode noBelow = rule.get("noBelow");
      if (yesAbove != null && noBelow != null && yesAbove.isNumber() && noBelow.isNumber()
          && noBelow.asDouble() > yesAbove.asDouble()) {
        errors.add(prefix + "noBelow must not be greater than yesAbove");
      }
      return;
    }
    JsonNode acceptAbove = rule.get("acceptAbove");
    JsonNode rejectBelow = rule.get("rejectBelow");
    if (acceptAbove != null && rejectBelow != null && acceptAbove.isNumber() && rejectBelow.isNumber()
        && rejectBelow.asDouble() > acceptAbove.asDouble()) {
      errors.add(prefix + "rejectBelow must not be greater than acceptAbove");
    }
    if (rejectBelow != null) {
      warnings.add(prefix + "a 'no' below rejectBelow rejects the whole decision; prefer yesAbove/noBelow with onNo "
          + "ACCEPT when 'no' is a normal answer");
    }
  }

  private static void checkScore(String prefix, JsonNode rule, JsonNode question, List<String> errors,
      List<String> warnings) {
    if (!rule.has("acceptLevels") && !rule.has("reviewLevels")) {
      errors.add(prefix + "rule has no acceptLevels or reviewLevels, so every level is rejected");
      return;
    }
    int levels = question != null && question.path("criteria").isArray() ? question.get("criteria").size() : -1;
    Set<Integer> accept = levels(prefix, "acceptLevels", rule.get("acceptLevels"), levels, errors);
    Set<Integer> review = levels(prefix, "reviewLevels", rule.get("reviewLevels"), levels, errors);

    Set<Integer> both = new TreeSet<>(accept);
    both.retainAll(review);
    if (!both.isEmpty()) {
      errors.add(prefix + "levels " + both + " are in both acceptLevels and reviewLevels");
    }
    if (levels > 0) {
      Set<Integer> rejected = new TreeSet<>();
      for (int level = 0; level < levels; level++) {
        if (!accept.contains(level) && !review.contains(level)) {
          rejected.add(level);
        }
      }
      if (!rejected.isEmpty()) {
        warnings.add(prefix + "levels " + rejected + " are neither accepted nor reviewed, so they reject the decision");
      }
    }
  }

  private static Set<Integer> levels(String prefix, String key, JsonNode array, int levels, List<String> errors) {
    Set<Integer> parsed = new HashSet<>();
    if (array == null || !array.isArray()) {
      return parsed;
    }
    for (JsonNode node : array) {
      Integer level = null;
      if (node.isIntegralNumber()) {
        level = node.asInt();
      } else if (node.isTextual()) {
        try {
          level = Integer.parseInt(node.asText().trim());
        } catch (NumberFormatException e) {
          level = null;
        }
      }
      if (level == null || level < 0) {
        errors.add(prefix + key + " entry '" + node.asText() + "' is not a level number");
      } else if (levels > 0 && level >= levels) {
        errors.add(prefix + key + " level " + level + " is out of range; the question has levels 0 to " + (levels - 1));
      } else {
        parsed.add(level);
      }
    }
    return parsed;
  }
}
