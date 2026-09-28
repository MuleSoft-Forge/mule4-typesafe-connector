package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.ArrayTypeBuilder;
import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.OutputStaticTypeResolver;

/**
 * Static DataSense type for the List Models JSON payload: {@code name}, {@code description}, {@code release_date},
 * {@code route}, and optional {@code display_name} (OpenRouter catalog label when it differs from the callable id).
 */
public class ModelListOutputResolver extends OutputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ArrayTypeBuilder array = BaseTypeBuilder.create(MetadataFormat.JSON).arrayType().id("typesafe-listed-models");
    ObjectTypeBuilder item = array.of().objectType().id("typesafe-listed-model");
    item.addField().key("name").value().stringType();
    item.addField().key("description").value().stringType();
    item.addField().key("release_date").value().stringType();
    item.addField().key("route").value().stringType();
    item.addField().key("display_name").value().stringType();
    return array.build();
  }
}
