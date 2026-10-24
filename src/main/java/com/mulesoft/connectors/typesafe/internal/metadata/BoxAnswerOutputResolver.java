package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.OutputStaticTypeResolver;

/**
 * Complete DataSense type for the Locate output payload: the System One {@code BoxAnswer} ({@code type}, {@code box}
 * with {@code top}/{@code bottom}/{@code left}/{@code right}, {@code confidence}) plus the connector's {@code derived}
 * geometry.
 */
public class BoxAnswerOutputResolver extends OutputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ObjectTypeBuilder answer = BaseTypeBuilder.create(MetadataFormat.JSON).objectType().id("typesafe-box-answer");
    answer.addField().key("type").value().stringType();
    DecisionTypeBuilder.addBoxFields(answer);
    return answer.build();
  }
}
