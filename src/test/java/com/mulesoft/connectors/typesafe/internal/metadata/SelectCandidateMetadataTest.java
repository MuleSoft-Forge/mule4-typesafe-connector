package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.model.ArrayType;
import org.mule.metadata.api.model.NullType;
import org.mule.metadata.api.model.ObjectType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Completeness check for Select Candidate. The output payload is the whole JSON document the operation writes; input
 * attributes are declared null and output attributes reuse {@link DecisionAttributesResolver}.
 */
class SelectCandidateMetadataTest {

  @Test
  void outputPayloadIsSelectedIdProbabilityConfidenceIsNoMatchAndRanking() {
    ObjectType payload = assertInstanceOf(ObjectType.class, new SelectCandidateOutputResolver().getStaticMetadata());
    assertTrue(payload.getFieldByName("selected").isPresent());
    assertTrue(payload.getFieldByName("id").isPresent());
    assertTrue(payload.getFieldByName("probability").isPresent());
    assertTrue(payload.getFieldByName("confidence").isPresent());
    assertTrue(payload.getFieldByName("isNoMatch").isPresent());
    assertInstanceOf(ArrayType.class, payload.getFieldByName("ranking").orElseThrow().getValue());
    assertEquals(6, payload.getFields().size());
  }

  @Test
  void candidatesInputIsAnArrayOfObjects() {
    assertInstanceOf(ArrayType.class, new SelectCandidateInputResolver().getStaticMetadata());
  }

  @Test
  void attributesAreDeclared() {
    assertInstanceOf(NullType.class, new NullInputAttributesResolver().getStaticMetadata());
    assertInstanceOf(ObjectType.class, new DecisionAttributesResolver().getStaticMetadata());
  }
}
