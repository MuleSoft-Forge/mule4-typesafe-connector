package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.InputStaticTypeResolver;

/**
 * The {@code questions} content shared by Validate, Evaluate, and Evaluate Batch: a JSON object of question id to
 * question, the map documented at https://docs.typesafe.ai/api. {@code instructions} and {@code criteria} are open
 * because a question may carry text or structure, and {@code criteria} is a map for choice and yes/no and an array for
 * score.
 */
public class QuestionsInputResolver extends InputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ObjectTypeBuilder questions = BaseTypeBuilder.create(MetadataFormat.JSON).objectType()
        .id("typesafe-questions-to-validate");
    ObjectTypeBuilder question = questions.openWith().objectType().id("typesafe-question");
    question.addField().key("type").value().stringType();
    question.addField().key("instructions").value().anyType();
    question.addField().key("criteria").value().anyType();
    question.addField().key("noMatchOption").value().stringType();
    return questions.build();
  }
}
