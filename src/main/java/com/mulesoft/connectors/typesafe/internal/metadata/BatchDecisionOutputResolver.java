package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.ArrayTypeBuilder;
import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.OutputStaticTypeResolver;

/**
 * Complete DataSense type for the Evaluate Batch output payload.
 */
public class BatchDecisionOutputResolver extends OutputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ArrayTypeBuilder array = BaseTypeBuilder.create(MetadataFormat.JSON).arrayType().id("typesafe-batch-results");
    ObjectTypeBuilder item = array.of().objectType().id("typesafe-batch-result");
    item.addField().key("index").value().numberType();
    item.addField().key("key").value().stringType();
    item.addField().key("status").value().stringType();
    item.addField().key("cached").value().booleanType();
    item.addField().key("answers").value().objectType().openWith().anyType();
    ObjectTypeBuilder error = item.addField().key("error").value().objectType().id("typesafe-batch-error");
    error.addField().key("type").value().stringType();
    error.addField().key("message").value().stringType();
    return array.build();
  }
}
