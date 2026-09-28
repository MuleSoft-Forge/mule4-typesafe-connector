package com.mulesoft.connectors.typesafe.internal.metadata;

/**
 * Shared constants for Evaluate DataSense. The question-set parameter uses {@code @MetadataKeyId} plus
 * {@code @OfValues(QuestionSetValueProvider)} rather than a {@code TypeKeysResolver}: Studio's tooling serializes SDK
 * {@code MetadataKey} instances as {@code MuleMetadataKeyAdapter}, which Gson cannot write
 * ({@code Couldn't serialize MetadataKey}). Value Providers avoid that path; {@link DecisionOutputResolver} still types
 * {@code payload.answers.*} from the selected file name.
 */
public final class QuestionSetTypeKeysResolver {

  static final String CATEGORY = "TypeSafeQuestionSets";
  static final String DEFAULT_LOCATION = "questions/";

  private QuestionSetTypeKeysResolver() {
  }
}
