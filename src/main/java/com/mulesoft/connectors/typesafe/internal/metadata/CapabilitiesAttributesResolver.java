package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.AttributesStaticTypeResolver;

/**
 * Static DataSense type for Get Capabilities output attributes: {@code count}.
 */
public class CapabilitiesAttributesResolver extends AttributesStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ObjectTypeBuilder object = BaseTypeBuilder.create(MetadataFormat.JSON).objectType()
        .id("typesafe-capabilities-attributes");
    object.addField().key("count").value().numberType();
    return object.build();
  }
}
