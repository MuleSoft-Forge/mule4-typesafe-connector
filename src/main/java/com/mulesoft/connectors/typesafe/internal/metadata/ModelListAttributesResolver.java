package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.AttributesStaticTypeResolver;

/**
 * Static DataSense type for List Models output attributes: {@code count} and {@code calls[]} ({@code route},
 * {@code statusCode}, {@code requestId}).
 */
public class ModelListAttributesResolver extends AttributesStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ObjectTypeBuilder object = BaseTypeBuilder.create(MetadataFormat.JSON).objectType()
        .id("typesafe-model-list-attributes");
    object.addField().key("count").value().numberType();
    ObjectTypeBuilder call = object.addField().key("calls").value().arrayType().of().objectType()
        .id("typesafe-model-list-call");
    call.addField().key("route").value().stringType();
    call.addField().key("statusCode").value().numberType();
    call.addField().key("requestId").value().stringType();
    return object.build();
  }
}
