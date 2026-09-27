package com.mulesoft.connectors.typesafe.internal.value;

import org.mule.sdk.api.values.Value;
import org.mule.sdk.api.values.ValueBuilder;
import org.mule.sdk.api.values.ValueProvider;
import org.mule.sdk.api.values.ValueResolvingException;

import com.mulesoft.connectors.typesafe.internal.questionset.QuestionSetLoader;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Fills the Question set combo from the {@code questions/} classpath folder. Listing is best-effort: an unreadable
 * folder yields an empty combo rather than an error.
 */
public class QuestionSetValueProvider implements ValueProvider {

  static final String DEFAULT_LOCATION = "questions/";

  @Override
  public Set<Value> resolve() throws ValueResolvingException {
    Set<Value> values = new LinkedHashSet<>();
    for (String name : QuestionSetLoader.list(DEFAULT_LOCATION)) {
      values.add(ValueBuilder.newValue(name).build());
    }
    return values;
  }

  @Override
  public String getId() {
    return "typesafe-question-set-values";
  }
}
