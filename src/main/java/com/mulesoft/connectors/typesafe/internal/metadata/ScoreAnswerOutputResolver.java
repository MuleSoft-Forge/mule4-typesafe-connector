package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.OutputStaticTypeResolver;

/**
 * Complete DataSense type for the Score output payload.
 */
public class ScoreAnswerOutputResolver extends OutputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ObjectTypeBuilder answer = BaseTypeBuilder.create(MetadataFormat.JSON).objectType().id("typesafe-score-answer");
    answer.addField().key("type").value().stringType();
    answer.addField().key("score").value().numberType();
    answer.addField().key("legend").value().objectType().openWith().stringType();
    answer.addField().key("probabilities").value().objectType().openWith().numberType();
    answer.addField().key("confidence").value().numberType();
    ObjectTypeBuilder derived = answer.addField().key("derived").value().objectType().id("typesafe-score-derived");
    derived.addField().key("level").value().anyType();
    derived.addField().key("levelLabel").value().stringType();
    return answer.build();
  }
}
