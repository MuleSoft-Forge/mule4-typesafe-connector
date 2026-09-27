package com.mulesoft.connectors.typesafe.internal.provider;

/**
 * One entry from {@code GET /{apiVersion}/models}. The fields are the ones that call returns: {@code name},
 * {@code description}, and {@code release_date}.
 */
public final class ModelCard {

  private final String name;
  private final String description;
  private final String releaseDate;

  public ModelCard(String name, String description, String releaseDate) {
    this.name = name;
    this.description = description;
    this.releaseDate = releaseDate;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  /** The response field {@code release_date}. */
  public String releaseDate() {
    return releaseDate;
  }
}
