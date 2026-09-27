package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.OutputStaticTypeResolver;

/**
 * Static DataSense type for the Validate Question Set JSON payload: {@code valid}, {@code errors}, and
 * {@code warnings}.
 */
public class QuestionSetValidationOutputResolver extends OutputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ObjectTypeBuilder object = BaseTypeBuilder.create(MetadataFormat.JSON).objectType()
        .id("typesafe-question-set-validation");
    object.addField().key("valid").value().booleanType();
    object.addField().key("errors").value().arrayType().of().stringType();
    object.addField().key("warnings").value().arrayType().of().stringType();
    return object.build();
  }
}
