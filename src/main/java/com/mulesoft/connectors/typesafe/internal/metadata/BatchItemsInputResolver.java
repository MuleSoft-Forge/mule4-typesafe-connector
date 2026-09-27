package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.ArrayTypeBuilder;
import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.InputStaticTypeResolver;

/**
 * Input payload metadata for a JSON array of arbitrary decision-state items.
 */
public class BatchItemsInputResolver extends InputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ArrayTypeBuilder array = BaseTypeBuilder.create(MetadataFormat.JSON).arrayType().id("typesafe-batch-items");
    array.of().anyType();
    return array.build();
  }
}
