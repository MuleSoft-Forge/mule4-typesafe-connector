package com.mulesoft.connectors.typesafe.internal.domain;

import com.mulesoft.connectors.typesafe.internal.util.Json;

import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerivedComputerTest {

  @Test
  void enrichesChoiceWithMarginRunnerUpAndNoMatch() {
    ObjectNode answers = (ObjectNode) Json
        .read("{\"q\":{\"type\":\"choice\",\"choice\":\"a\",\"probabilities\":{\"a\":0.7,\"b\":0.2,\"c\":0.1}}}");

    DerivedComputer.enrich(answers, Map.of("q", "c"));

    ObjectNode derived = (ObjectNode) answers.get("q").get("derived");
    assertEquals(0.5, derived.get("margin").asDouble(), 1e-9);
    assertEquals("b", derived.get("runnerUp").asText());
    assertFalse(derived.get("isNoMatch").asBoolean());
  }

  @Test
  void flagsNoMatchWhenChosenOptionIsTheNoMatchKey() {
    ObjectNode answers = (ObjectNode) Json
        .read("{\"q\":{\"type\":\"choice\",\"choice\":\"none\",\"probabilities\":{\"none\":0.9,\"a\":0.1}}}");

    DerivedComputer.enrich(answers, Map.of("q", "none"));

    assertTrue(answers.get("q").get("derived").get("isNoMatch").asBoolean());
  }

  @Test
  void enrichesScoreWithMostProbableLevelAndLabel() {
    ObjectNode answers = (ObjectNode) Json
        .read("{\"q\":{\"type\":\"score\",\"probabilities\":{\"1\":0.1,\"2\":0.2,\"3\":0.7},"
            + "\"legend\":{\"1\":\"low\",\"2\":\"mid\",\"3\":\"high\"}}}");

    DerivedComputer.enrich(answers, Map.of());

    ObjectNode derived = (ObjectNode) answers.get("q").get("derived");
    assertEquals(3, derived.get("level").asInt());
    assertEquals("high", derived.get("levelLabel").asText());
  }

  @Test
  void enrichesBoxWithGeometry() {
    ObjectNode answers = (ObjectNode) Json.read("{\"q\":{\"type\":\"box\","
        + "\"box\":{\"top\":0.2,\"bottom\":0.6,\"left\":0.1,\"right\":0.5},\"confidence\":null}}");

    DerivedComputer.enrich(answers, Map.of());

    ObjectNode derived = (ObjectNode) answers.get("q").get("derived");
    assertEquals(0.4, derived.get("width").asDouble(), 1e-9);
    assertEquals(0.4, derived.get("height").asDouble(), 1e-9);
    assertEquals(0.3, derived.get("centerX").asDouble(), 1e-9);
    assertEquals(0.4, derived.get("centerY").asDouble(), 1e-9);
    assertEquals(0.16, derived.get("area").asDouble(), 1e-9);
  }

  @Test
  void roundsBoxGeometrySoFloatNoiseDoesNotReachThePayload() {
    ObjectNode answers = (ObjectNode) Json.read(
        "{\"q\":{\"type\":\"box\",\"box\":{\"top\":0.29,\"bottom\":0.69,\"left\":0.24,\"right\":0.75},\"confidence\":0.78}}");
    DerivedComputer.enrich(answers, Map.of());
    assertEquals("{\"width\":0.51,\"height\":0.4,\"centerX\":0.495,\"centerY\":0.49,\"area\":0.204}",
        answers.get("q").get("derived").toString());
  }

  @Test
  void skipsBoxGeometryWhenAnEdgeIsMissing() {
    ObjectNode answers = (ObjectNode) Json.read("{\"q\":{\"type\":\"box\",\"box\":{\"top\":0.2}}}");
    DerivedComputer.enrich(answers, Map.of());
    assertFalse(answers.get("q").has("derived"));
  }

  @Test
  void leavesNoulUntouched() {
    ObjectNode answers = (ObjectNode) Json.read("{\"q\":{\"type\":\"noul\",\"noul\":0.8}}");
    DerivedComputer.enrich(answers, Map.of());
    assertFalse(answers.get("q").has("derived"));
  }
}
