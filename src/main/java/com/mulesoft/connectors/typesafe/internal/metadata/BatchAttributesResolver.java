package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.AttributesStaticTypeResolver;

/**
 * Complete DataSense type for batch output attributes.
 */
public class BatchAttributesResolver extends AttributesStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ObjectTypeBuilder attributes = BaseTypeBuilder.create(MetadataFormat.JSON).objectType()
        .id("typesafe-batch-attributes");
    attributes.addField().key("total").value().numberType();
    attributes.addField().key("succeeded").value().numberType();
    attributes.addField().key("failed").value().numberType();
    attributes.addField().key("skippedBudget").value().numberType();
    attributes.addField().key("cached").value().numberType();
    ObjectTypeBuilder usage = attributes.addField().key("usage").value().objectType().id("typesafe-batch-token-usage");
    usage.addField().key("inputTokens").value().numberType();
    usage.addField().key("outputTokens").value().numberType();
    attributes.addField().key("estimatedCostUsd").value().numberType();
    return attributes.build();
  }
}
