package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.InputStaticTypeResolver;

import java.io.InputStream;

/**
 * Input metadata for the {@code images} parameter: an object whose keys are the reference strings written into the
 * state, instructions or criteria, and whose values are PNG or JPEG binaries.
 */
public class ImagesInputResolver extends InputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ObjectTypeBuilder images = BaseTypeBuilder.create(MetadataFormat.JAVA).objectType().id("typesafe-images");
    // The runtime resolves a map parameter's value class from this id; without it the flow fails to start.
    images.openWith().binaryType().id(InputStream.class.getName());
    return images.build();
  }
}
