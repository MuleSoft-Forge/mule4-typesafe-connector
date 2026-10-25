package com.mulesoft.connectors.typesafe.internal.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Canonical, provider-agnostic decision request. Operations build one of these and hand it to the engine; route
 * differences live only in the adapters.
 *
 * <p>
 * {@code questions} is already cleaned for the wire: connector-side annotations such as {@code noMatchOption} have been
 * stripped and captured in {@link #noMatchOptions()} so they can drive {@code derived.isNoMatch} without being sent to
 * the provider.
 */
public final class DecisionRequest {

  public static final String BOX = "box";

  private final JsonNode state;
  private final String requestedModel;
  private final ObjectNode questions;
  private final Map<String, String> noMatchOptions;
  private final String questionSetId;
  private final String questionSetVersion;
  private final List<ImageAsset> images;

  public DecisionRequest(JsonNode state, String requestedModel, ObjectNode questions,
      Map<String, String> noMatchOptions, String questionSetId, String questionSetVersion) {
    this(state, requestedModel, questions, noMatchOptions, questionSetId, questionSetVersion, List.of());
  }

  public DecisionRequest(JsonNode state, String requestedModel, ObjectNode questions,
      Map<String, String> noMatchOptions, String questionSetId, String questionSetVersion, List<ImageAsset> images) {
    this.state = state;
    this.requestedModel = requestedModel;
    this.questions = questions;
    this.noMatchOptions = Collections.unmodifiableMap(new HashMap<>(noMatchOptions));
    this.questionSetId = questionSetId;
    this.questionSetVersion = questionSetVersion;
    this.images = images == null ? List.of() : List.copyOf(images);
  }

  public JsonNode state() {
    return state;
  }

  /** The model the caller asked for, or {@code null} to use the route's configured default. */
  public String requestedModel() {
    return requestedModel;
  }

  public ObjectNode questions() {
    return questions;
  }

  public Map<String, String> noMatchOptions() {
    return noMatchOptions;
  }

  public String questionSetId() {
    return questionSetId;
  }

  public String questionSetVersion() {
    return questionSetVersion;
  }

  /** Images referenced from the state, instructions or criteria, in the order the caller supplied them. */
  public List<ImageAsset> images() {
    return images;
  }

  /** True when any question is a Box question. */
  public boolean hasBoxQuestion() {
    if (questions == null) {
      return false;
    }
    Iterator<JsonNode> it = questions.elements();
    while (it.hasNext()) {
      if (BOX.equals(it.next().path("type").asText(""))) {
        return true;
      }
    }
    return false;
  }

  /**
   * True when the request needs the image feature: it carries images or asks a Box question. Such requests must send
   * {@code TypeSafe-Danger-Zone-Images: true} and can only run on routes that support images.
   */
  public boolean usesImages() {
    return !images.isEmpty() || hasBoxQuestion();
  }
}
