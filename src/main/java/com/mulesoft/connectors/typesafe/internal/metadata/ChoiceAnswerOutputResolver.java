package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.OutputStaticTypeResolver;

/**
 * Complete DataSense type for the Choose output payload.
 */
public class ChoiceAnswerOutputResolver extends OutputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ObjectTypeBuilder answer = BaseTypeBuilder.create(MetadataFormat.JSON).objectType().id("typesafe-choice-answer");
    answer.addField().key("type").value().stringType();
    answer.addField().key("choice").value().stringType();
    answer.addField().key("probabilities").value().objectType().openWith().numberType();
    answer.addField().key("confidence").value().numberType();
    ObjectTypeBuilder derived = answer.addField().key("derived").value().objectType().id("typesafe-choice-derived");
    derived.addField().key("margin").value().numberType();
    derived.addField().key("runnerUp").value().stringType();
    derived.addField().key("isNoMatch").value().booleanType();
    return answer.build();
  }
}
