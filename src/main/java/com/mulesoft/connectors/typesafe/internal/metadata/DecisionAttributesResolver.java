package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.AttributesStaticTypeResolver;

/**
 * Complete DataSense type for decision output attributes.
 */
public class DecisionAttributesResolver extends AttributesStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ObjectTypeBuilder attributes = BaseTypeBuilder.create(MetadataFormat.JSON).objectType()
        .id("typesafe-decision-attributes");
    attributes.addField().key("provider").value().stringType();
    attributes.addField().key("requestedModel").value().stringType();
    attributes.addField().key("model").value().stringType();
    ObjectTypeBuilder usage = attributes.addField().key("usage").value().objectType().id("typesafe-token-usage");
    usage.addField().key("inputTokens").value().numberType();
    usage.addField().key("outputTokens").value().numberType();
    attributes.addField().key("estimatedCostUsd").value().numberType();
    attributes.addField().key("costSource").value().stringType();
    attributes.addField().key("latencyMs").value().numberType();
    attributes.addField().key("attempts").value().numberType();
    attributes.addField().key("failedOverFrom").value().arrayType().of().stringType();
    attributes.addField().key("cacheHit").value().booleanType();
    attributes.addField().key("questionSetId").value().stringType();
    attributes.addField().key("questionSetVersion").value().stringType();
    attributes.addField().key("stateHash").value().stringType();
    attributes.addField().key("providerRequestId").value().stringType();
    attributes.addField().key("rawResponse").value().stringType();
    ObjectTypeBuilder trace = attributes.addField().key("traceEntry").value().objectType().id("typesafe-trace-entry");
    trace.addField().key("step").value().stringType();
    trace.addField().key("provider").value().stringType();
    trace.addField().key("model").value().stringType();
    trace.addField().key("latencyMs").value().numberType();
    trace.addField().key("attempts").value().numberType();
    trace.addField().key("estimatedCostUsd").value().numberType();
    trace.addField().key("questionSetId").value().stringType();
    trace.addField().key("failedOverFrom").value().arrayType().of().stringType();
    return attributes.build();
  }
}
