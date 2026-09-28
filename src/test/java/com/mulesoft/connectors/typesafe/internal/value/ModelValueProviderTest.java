package com.mulesoft.connectors.typesafe.internal.value;

import com.mulesoft.connectors.typesafe.internal.connection.RouteType;
import com.mulesoft.connectors.typesafe.internal.provider.ModelCard;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelValueProviderTest {

  @Test
  void openRouterLiveSamplePrefersTypesafeAndCapsSize() {
    List<ModelCard> models = new ArrayList<>();
    models.add(new ModelCard("openai/gpt-4", null, null));
    models.add(new ModelCard("typesafe/jev-router", null, null));
    models.add(new ModelCard("anthropic/claude-3", null, null));
    models.add(new ModelCard("~typesafe/jev-latest", null, null));
    for (int i = 0; i < 40; i++) {
      models.add(new ModelCard("vendor/model-" + i, null, null));
    }

    List<String> selected = ModelValueProvider.selectLiveIds(RouteType.OPENROUTER, models);

    assertEquals(ModelValueProvider.MAX_LIVE_MODELS, selected.size());
    assertEquals("typesafe/jev-router", selected.get(0));
    assertEquals("~typesafe/jev-latest", selected.get(1));
    assertTrue(selected.indexOf("typesafe/jev-router") < selected.indexOf("openai/gpt-4"));
  }

  @Test
  void rejectsBlankAndControlCharacterIds() {
    assertFalse(ModelValueProvider.isStudioSafeModelId(null));
    assertFalse(ModelValueProvider.isStudioSafeModelId(" "));
    assertFalse(ModelValueProvider.isStudioSafeModelId("bad\nid"));
    assertTrue(ModelValueProvider.isStudioSafeModelId("typesafe/jev-router"));
  }

  @Test
  void typesafeLiveSampleKeepsOrderUpToCap() {
    List<ModelCard> models = List.of(new ModelCard("jev-latest", null, null), new ModelCard("jev-preview", null, null));

    assertEquals(List.of("jev-latest", "jev-preview"), ModelValueProvider.selectLiveIds(RouteType.TYPESAFE, models));
  }
}
