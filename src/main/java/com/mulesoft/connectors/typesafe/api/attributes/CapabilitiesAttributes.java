package com.mulesoft.connectors.typesafe.api.attributes;

import java.io.Serializable;

/**
 * Output attributes for Get Capabilities. A flow reads {@code attributes.count}, the number of routes reported.
 */
public class CapabilitiesAttributes implements Serializable {

  private static final long serialVersionUID = 1L;

  private final int count;

  public CapabilitiesAttributes(int count) {
    this.count = count;
  }

  public int getCount() {
    return count;
  }
}
