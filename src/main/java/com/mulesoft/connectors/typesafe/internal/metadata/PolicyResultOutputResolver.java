package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.OutputStaticTypeResolver;

/**
 * Static DataSense type for the Apply Policy JSON payload: {@code action}, {@code routeKey}, {@code reasons}, and
 * {@code perQuestion} keyed by question id.
 */
public class PolicyResultOutputResolver extends OutputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ObjectTypeBuilder result = BaseTypeBuilder.create(MetadataFormat.JSON).objectType().id("typesafe-policy-result");
    result.addField().key("action").value().stringType();
    result.addField().key("routeKey").value().stringType();
    result.addField().key("reasons").value().arrayType().of().stringType();
    ObjectTypeBuilder verdict = result.addField().key("perQuestion").value().objectType().openWith().objectType()
        .id("typesafe-policy-question-result");
    verdict.addField().key("action").value().stringType();
    verdict.addField().key("reasons").required(false).value().arrayType().of().stringType();
    return result.build();
  }
}
