package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.ArrayTypeBuilder;
import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.InputStaticTypeResolver;

/**
 * Input payload metadata for Select Candidate: a JSON array of candidate objects.
 */
public class SelectCandidateInputResolver extends InputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ArrayTypeBuilder array = BaseTypeBuilder.create(MetadataFormat.JSON).arrayType().id("typesafe-select-candidates");
    array.of().objectType().openWith().anyType();
    return array.build();
  }
}
