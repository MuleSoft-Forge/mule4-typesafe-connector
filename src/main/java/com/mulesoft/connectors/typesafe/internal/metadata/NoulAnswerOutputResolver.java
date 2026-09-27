package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.OutputStaticTypeResolver;

/**
 * Complete DataSense type for the Ask Yes/No output payload.
 */
public class NoulAnswerOutputResolver extends OutputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ObjectTypeBuilder answer = BaseTypeBuilder.create(MetadataFormat.JSON).objectType().id("typesafe-noul-answer");
    answer.addField().key("type").value().stringType();
    answer.addField().key("noul").value().numberType();
    return answer.build();
  }
}
