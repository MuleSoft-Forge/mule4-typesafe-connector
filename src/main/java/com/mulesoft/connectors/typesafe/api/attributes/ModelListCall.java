package com.mulesoft.connectors.typesafe.api.attributes;

import java.io.Serializable;

/**
 * One route's model-list call, as output attributes. A flow reads {@code attributes.calls[0].statusCode} and
 * {@code attributes.calls[0].requestId}.
 */
public class ModelListCall implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String route;
  private final int statusCode;
  private final String requestId;

  public ModelListCall(String route, int statusCode, String requestId) {
    this.route = route;
    this.statusCode = statusCode;
    this.requestId = requestId;
  }

  public String getRoute() {
    return route;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getRequestId() {
    return requestId;
  }
}
