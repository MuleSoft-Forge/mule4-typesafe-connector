package com.mulesoft.connectors.typesafe.internal.connection;

import org.mule.sdk.api.annotation.Alias;
import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.Parameter;
import org.mule.sdk.api.annotation.param.display.DisplayName;
import org.mule.sdk.api.annotation.param.display.Placement;
import org.mule.sdk.api.annotation.param.display.Summary;
import org.mule.sdk.api.annotation.semantics.security.Password;

import com.mulesoft.connectors.typesafe.internal.provider.Capabilities;
import com.mulesoft.connectors.typesafe.internal.provider.CostExtractor;
import com.mulesoft.connectors.typesafe.internal.provider.RequestIdExtractor;
import com.mulesoft.connectors.typesafe.internal.provider.SystemOneAdapter;

/**
 * A generic {@code systemOne}-compatible gateway: point it at any endpoint that implements the canonical contract. The
 * caller declares the base URL and, optionally, a key; capabilities default to the safe minimum since they cannot be
 * inferred, and cost is estimated from tokens.
 */
@Alias("compatible")
@DisplayName("Compatible Gateway")
public class CompatibleConnectionProvider extends AbstractRouteConnectionProvider {

  @Parameter
  @Placement(order = 1)
  @Summary("Base URL of the compatible endpoint.")
  private String baseUrl;

  @Parameter
  @Optional(defaultValue = "v1")
  @DisplayName("API version")
  @Placement(order = 2)
  @Summary("Path version for this route. List Models calls GET /{apiVersion}/models.")
  private String apiVersion;

  @Parameter
  @Optional
  @Password
  @Placement(order = 3)
  @Summary("Optional API key, sent as a Bearer token when present.")
  private String apiKey;

  @Parameter
  @Optional
  @Placement(order = 4)
  @Summary("Default model used when an operation does not specify one.")
  private String model;

  @Parameter
  @Optional(defaultValue = "false")
  @Placement(order = 5)
  @Summary("Whether this gateway can enumerate models.")
  private boolean supportsModelList;

  @Override
  public TypeSafeConnection connect() {
    SystemOneAdapter adapter = new SystemOneAdapter("compatible", baseUrl, apiVersion, model,
        Capabilities.full(supportsModelList), apiKey, customHeaders(), CostExtractor.NONE,
        RequestIdExtractor.header("x-typesafe-request-id"), transport());
    return connection(adapter);
  }
}
