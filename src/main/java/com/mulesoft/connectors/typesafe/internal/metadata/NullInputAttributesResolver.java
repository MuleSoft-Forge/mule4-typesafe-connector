package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.InputStaticTypeResolver;

/**
 * Input attributes metadata for operations that do not read incoming message attributes.
 */
public class NullInputAttributesResolver extends InputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    return BaseTypeBuilder.create(MetadataFormat.JSON).nullType().build();
  }
}
