package com.mulesoft.connectors.typesafe.internal.provider;

import java.util.List;

/**
 * One route's model-list response: the cards, the HTTP status, and the provider request id when the route reports one.
 */
public final class ModelListPage {

  private final List<ModelCard> models;
  private final int statusCode;
  private final String requestId;

  public ModelListPage(List<ModelCard> models, int statusCode, String requestId) {
    this.models = List.copyOf(models);
    this.statusCode = statusCode;
    this.requestId = requestId;
  }

  public List<ModelCard> models() {
    return models;
  }

  public int statusCode() {
    return statusCode;
  }

  public String requestId() {
    return requestId;
  }
}
