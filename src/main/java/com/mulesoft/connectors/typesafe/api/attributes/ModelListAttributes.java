package com.mulesoft.connectors.typesafe.api.attributes;

import java.io.Serializable;
import java.util.List;

/**
 * Output attributes for List Models. {@code count} is the number of cards. {@code calls} is one entry per route that
 * answered, with that response's status and request id.
 */
public class ModelListAttributes implements Serializable {

  private static final long serialVersionUID = 1L;

  private final int count;
  private final List<ModelListCall> calls;

  public ModelListAttributes(int count, List<ModelListCall> calls) {
    this.count = count;
    this.calls = List.copyOf(calls);
  }

  public int getCount() {
    return count;
  }

  public List<ModelListCall> getCalls() {
    return calls;
  }
}
