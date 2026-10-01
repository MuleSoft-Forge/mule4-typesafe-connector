package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.InputStaticTypeResolver;

/**
 * Input metadata for an inline Apply Policy policy: a map of question id to rule. A rule carries the keys of its
 * question's type, so every key is listed and none is required.
 */
public class PolicyInputResolver extends InputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ObjectTypeBuilder policy = BaseTypeBuilder.create(MetadataFormat.JSON).objectType().id("typesafe-policy");
    ObjectTypeBuilder rule = policy.openWith().objectType().id("typesafe-policy-rule");
    rule.addField().key("minProbability").required(false).value().numberType();
    rule.addField().key("minConfidence").required(false).value().numberType();
    rule.addField().key("minMargin").required(false).value().numberType();
    rule.addField().key("onNoMatch").required(false).value().stringType();
    rule.addField().key("acceptAbove").required(false).value().numberType();
    rule.addField().key("rejectBelow").required(false).value().numberType();
    rule.addField().key("acceptLevels").required(false).value().arrayType().of().stringType();
    rule.addField().key("reviewLevels").required(false).value().arrayType().of().stringType();
    return policy.build();
  }
}
