package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.OutputStaticTypeResolver;

/**
 * Complete DataSense type for the Filter output payload.
 */
public class FilterOutputResolver extends OutputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ObjectTypeBuilder output = BaseTypeBuilder.create(MetadataFormat.JSON).objectType().id("typesafe-filter-result");
    output.addField().key("kept").value().arrayType().of().anyType();
    output.addField().key("dropped").value().arrayType().of().anyType();
    output.addField().key("uncertain").value().arrayType().of().anyType();
    ObjectTypeBuilder score = output.addField().key("scores").value().arrayType().of().objectType()
        .id("typesafe-filter-score");
    score.addField().key("index").value().numberType();
    score.addField().key("noul").value().numberType();
    score.addField().key("band").value().stringType();
    score.addField().key("kept").value().booleanType();
    return output.build();
  }
}
