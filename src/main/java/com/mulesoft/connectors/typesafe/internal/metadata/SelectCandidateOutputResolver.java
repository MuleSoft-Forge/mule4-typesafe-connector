package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.sdk.api.metadata.resolving.OutputStaticTypeResolver;

/**
 * Static DataSense type for the Select Candidate JSON payload: the selected row, its id, probability, confidence,
 * whether it was the no-match option, and the full ranking.
 */
public class SelectCandidateOutputResolver extends OutputStaticTypeResolver {

  @Override
  public MetadataType getStaticMetadata() {
    ObjectTypeBuilder payload = BaseTypeBuilder.create(MetadataFormat.JSON).objectType()
        .id("typesafe-select-candidate");
    payload.addField().key("selected").value().objectType().openWith().anyType();
    payload.addField().key("id").value().stringType();
    payload.addField().key("probability").value().numberType();
    payload.addField().key("confidence").value().numberType();
    payload.addField().key("isNoMatch").value().booleanType();
    ObjectTypeBuilder rank = payload.addField().key("ranking").value().arrayType().of().objectType()
        .id("typesafe-select-candidate-rank");
    rank.addField().key("id").value().stringType();
    rank.addField().key("probability").value().numberType();
    return payload.build();
  }
}
