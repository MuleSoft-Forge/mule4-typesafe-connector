package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.InputStaticTypeResolver;

/**
 * Input payload for List Models. {@code GET /{apiVersion}/models} sends no body, so the type is null.
 */
public class ModelListInputResolver extends InputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    return BaseTypeBuilder.create(MetadataFormat.JSON).nullType().build();
  }
}
