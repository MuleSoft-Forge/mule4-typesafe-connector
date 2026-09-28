package com.mulesoft.connectors.typesafe.internal.provider;

import com.mulesoft.connectors.typesafe.internal.http.HttpTransport;

import java.util.Map;

/**
 * OpenRouter's System One adapter. Its {@code GET /{apiVersion}/models} endpoint is a public multi-vendor catalog, so
 * only the TypeSafe vendor namespace is relevant to this connector. Runtime decisions and catalog normalization remain
 * in {@link SystemOneAdapter}; this class supplies only OpenRouter's catalog scope.
 */
public final class OpenRouterAdapter extends SystemOneAdapter {

  private static final String TYPESAFE_VENDOR_PREFIX = "typesafe/";

  public OpenRouterAdapter(String routeName, String baseUrl, String apiVersion, String defaultModel,
      Capabilities capabilities, String apiKey, Map<String, String> extraHeaders, CostExtractor costExtractor,
      RequestIdExtractor requestIdExtractor, HttpTransport transport) {
    super(routeName, baseUrl, apiVersion, defaultModel, capabilities, apiKey, extraHeaders, costExtractor,
        requestIdExtractor, transport);
  }

  @Override
  protected boolean includeListedModel(String id) {
    return id.startsWith(TYPESAFE_VENDOR_PREFIX);
  }
}
