package com.mulesoft.connectors.typesafe.internal.value;

import org.mule.sdk.api.annotation.param.Connection;
import org.mule.sdk.api.values.Value;
import org.mule.sdk.api.values.ValueBuilder;
import org.mule.sdk.api.values.ValueProvider;
import org.mule.sdk.api.values.ValueResolvingException;

import com.mulesoft.connectors.typesafe.internal.connection.RouteDefaults;
import com.mulesoft.connectors.typesafe.internal.connection.RouteType;
import com.mulesoft.connectors.typesafe.internal.connection.TypeSafeConnection;
import com.mulesoft.connectors.typesafe.internal.provider.ModelCard;
import com.mulesoft.connectors.typesafe.internal.provider.ModelListPage;
import com.mulesoft.connectors.typesafe.internal.provider.ProviderAdapter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Offers model ids for the {@code model} parameter at design time. It starts from the route's static defaults and, when
 * the connected route can enumerate models, merges a bounded live {@code list-models} sample. OpenRouter's full catalog
 * is hundreds of ids; dumping it into Studio breaks metadata serialization, so design-time keeps static defaults plus a
 * small preferred subset. Runtime {@code list-models} still returns the full catalog.
 */
public class ModelValueProvider implements ValueProvider {

  private static final long LIVE_TIMEOUT_SECONDS = 5L;

  /** Cap on live ids merged into the Studio dropdown (static defaults always win first). */
  static final int MAX_LIVE_MODELS = 25;

  @Connection
  private TypeSafeConnection connection;

  @Override
  public Set<Value> resolve() throws ValueResolvingException {
    ProviderAdapter primary = connection.primary();
    RouteType route = RouteType.fromRouteName(primary.routeName());
    Set<String> ids = new LinkedHashSet<>(RouteDefaults.models(route));
    if (primary.capabilities().isSupportsModelList()) {
      try {
        ModelListPage page = primary.listModels().get(LIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        for (String id : selectLiveIds(route, page.models())) {
          ids.add(id);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception ignored) {
        // A live listing failure falls back to the static defaults; design-time editing must not break.
      }
    }
    Set<Value> values = new LinkedHashSet<>();
    for (String id : ids) {
      values.add(ValueBuilder.newValue(id).build());
    }
    return values;
  }

  /**
   * Picks a Studio-safe sample from a live catalog: prefer TypeSafe/Jev ids, then fill up to {@link #MAX_LIVE_MODELS},
   * skipping blank or control-character ids.
   */
  static List<String> selectLiveIds(RouteType route, List<ModelCard> models) {
    List<String> preferred = new ArrayList<>();
    List<String> others = new ArrayList<>();
    for (ModelCard card : models) {
      String id = card == null ? null : card.name();
      if (!isStudioSafeModelId(id)) {
        continue;
      }
      if (isPreferredLiveId(route, id)) {
        preferred.add(id);
      } else {
        others.add(id);
      }
    }
    List<String> selected = new ArrayList<>(MAX_LIVE_MODELS);
    for (String id : preferred) {
      if (selected.size() >= MAX_LIVE_MODELS) {
        break;
      }
      selected.add(id);
    }
    for (String id : others) {
      if (selected.size() >= MAX_LIVE_MODELS) {
        break;
      }
      selected.add(id);
    }
    return selected;
  }

  private static boolean isPreferredLiveId(RouteType route, String id) {
    String lower = id.toLowerCase(Locale.ROOT);
    switch (route) {
      case OPENROUTER :
      case VERCEL :
      case CLOUDFLARE :
        return lower.contains("typesafe") || lower.contains("jev");
      case TYPESAFE :
      default :
        return true;
    }
  }

  /** Reject ids Studio cannot round-trip as metadata/value keys. */
  static boolean isStudioSafeModelId(String id) {
    if (id == null || id.isBlank()) {
      return false;
    }
    for (int i = 0; i < id.length(); i++) {
      char c = id.charAt(i);
      if (Character.isISOControl(c)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String getId() {
    return "typesafe-model-values";
  }
}
