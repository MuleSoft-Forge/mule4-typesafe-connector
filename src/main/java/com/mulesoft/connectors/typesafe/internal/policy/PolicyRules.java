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

  /**
   * Rule keys each question type reads. Choice may also nest per-option thresholds under {@code options}. Noul accepts
   * either the three-band form ({@code yesAbove}/{@code noBelow}/{@code onYes}/{@code onNo}/{@code onUncertain}) or the
   * legacy {@code acceptAbove}/{@code rejectBelow} form.
   */
  public static final Map<String, Set<String>> KEYS = Map.of(CHOICE,
      Set.of("minProbability", "minConfidence", "minMargin", "onNoMatch", "options", "otherOptions"), NOUL,
      Set.of("acceptAbove", "rejectBelow", "yesAbove", "noBelow", "onYes", "onNo", "onUncertain"), SCORE,
      Set.of("acceptLevels", "reviewLevels", "minConfidence"));

  /** Threshold keys allowed on a Choice {@code options.<id>} entry (alongside {@code action}). */
  public static final Set<String> OPTION_KEYS = Set.of("action", "minProbability", "minConfidence", "minMargin");

  private PolicyRules() {
  }

  /** True when the rule uses the three-band Noul form rather than legacy {@code acceptAbove}/{@code rejectBelow}. */
  public static boolean isBandedNoul(JsonNode rule) {
    return rule != null && (rule.has("yesAbove") || rule.has("noBelow") || rule.has("onYes") || rule.has("onNo")
        || rule.has("onUncertain"));
  }

  /** True when the rule uses the legacy Noul thresholds. */
  public static boolean isLegacyNoul(JsonNode rule) {
    return rule != null && (rule.has("acceptAbove") || rule.has("rejectBelow"));
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
