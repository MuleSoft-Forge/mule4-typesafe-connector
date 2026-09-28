package com.mulesoft.connectors.typesafe.internal.provider;

/**
 * One entry from {@code GET /{apiVersion}/models}, projected onto the TypeSafe model-list contract: {@code name},
 * {@code description}, and {@code release_date}. OpenRouter may also carry {@code display_name} when its catalog label
 * differs from the callable id.
 */
public final class ModelCard {

  private final String name;
  private final String description;
  private final String releaseDate;
  private final String displayName;

  public ModelCard(String name, String description, String releaseDate) {
    this(name, description, releaseDate, null);
  }

  public ModelCard(String name, String description, String releaseDate, String displayName) {
    this.name = name;
    this.description = description;
    this.releaseDate = releaseDate;
    this.displayName = displayName;
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

  /**
   * OpenRouter catalog label ({@code data[].name}) when it differs from the callable id. Absent for TypeSafe cards.
   */
  public String displayName() {
    return displayName;
  }
}
