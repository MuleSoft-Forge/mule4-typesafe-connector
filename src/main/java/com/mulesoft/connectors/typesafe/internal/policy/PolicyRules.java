package com.mulesoft.connectors.typesafe.internal.policy;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The policy rule vocabulary, per question type. Shared by {@link PolicyEvaluator} and the policy validator so the keys
 * the evaluator reads and the keys the validator accepts cannot drift apart.
 */
public final class PolicyRules {

  public static final String CHOICE = "choice";
  public static final String NOUL = "noul";
  public static final String SCORE = "score";

  /** Rule keys each question type reads. {@code minConfidence} is shared by Choice and Score. */
  public static final Map<String, Set<String>> KEYS = Map.of(CHOICE,
      Set.of("minProbability", "minConfidence", "minMargin", "onNoMatch"), NOUL, Set.of("acceptAbove", "rejectBelow"),
      SCORE, Set.of("acceptLevels", "reviewLevels", "minConfidence"));

  private PolicyRules() {
  }

  /**
   * The question types whose vocabulary covers every key in {@code rule}. An empty rule fits every type; a rule with an
   * unknown key, or keys from two types, fits none.
   */
  public static Set<String> typesFor(JsonNode rule) {
    Set<String> types = new LinkedHashSet<>();
    for (String type : new String[]{CHOICE, NOUL, SCORE}) {
      if (coveredBy(rule, KEYS.get(type))) {
        types.add(type);
      }
    }
    return types;
  }

  private static boolean coveredBy(JsonNode rule, Set<String> keys) {
    Iterator<String> names = rule.fieldNames();
    while (names.hasNext()) {
      if (!keys.contains(names.next())) {
        return false;
      }
    }
    return true;
  }
}
