package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.AttributesStaticTypeResolver;

/**
 * Output attributes for Apply Policy. The operation makes no HTTP call and writes no attributes, so the type is null.
 */
public class PolicyResultAttributesResolver extends AttributesStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    return BaseTypeBuilder.create(MetadataFormat.JSON).nullType().build();
  }
}
