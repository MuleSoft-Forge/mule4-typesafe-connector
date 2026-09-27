package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.ArrayTypeBuilder;
import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.OutputStaticTypeResolver;

/**
 * Static DataSense type for the Get Capabilities JSON payload.
 */
public class CapabilitiesOutputResolver extends OutputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ArrayTypeBuilder array = BaseTypeBuilder.create(MetadataFormat.JSON).arrayType().id("typesafe-route-capabilities");
    ObjectTypeBuilder item = array.of().objectType().id("typesafe-route-capability");
    item.addField().key("route").value().stringType();
    item.addField().key("primary").value().booleanType();
    ObjectTypeBuilder capabilities = item.addField().key("capabilities").value().objectType()
        .id("typesafe-capabilities");
    capabilities.addField().key("supportsNoul").value().booleanType();
    capabilities.addField().key("supportsChoice").value().booleanType();
    capabilities.addField().key("supportsScore").value().booleanType();
    capabilities.addField().key("returnsConfidence").value().booleanType();
    capabilities.addField().key("supportsModelList").value().booleanType();
    capabilities.addField().key("supportsStructuredInstructions").value().booleanType();
    capabilities.addField().key("maxChoiceOptions").value().numberType();
    capabilities.addField().key("maxScoreLevels").value().numberType();
    return array.build();
  }
}
