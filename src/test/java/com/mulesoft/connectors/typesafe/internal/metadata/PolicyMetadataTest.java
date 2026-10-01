package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.model.ArrayType;
import org.mule.metadata.api.model.NullType;
import org.mule.metadata.api.model.ObjectType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Completeness check for Apply Policy. The output payload is the whole JSON document the operation writes, and both
 * attribute parts are declared even though the operation does not use them.
 */
class PolicyMetadataTest {

  @Test
  void outputPayloadIsActionRouteKeyReasonsAndPerQuestion() {
    ObjectType payload = assertInstanceOf(ObjectType.class, new PolicyResultOutputResolver().getStaticMetadata());
    assertTrue(payload.getFieldByName("action").isPresent());
    assertTrue(payload.getFieldByName("routeKey").isPresent());
    assertInstanceOf(ArrayType.class, payload.getFieldByName("reasons").orElseThrow().getValue());
    ObjectType perQuestion = assertInstanceOf(ObjectType.class,
        payload.getFieldByName("perQuestion").orElseThrow().getValue());
    assertTrue(perQuestion.isOpen());
    ObjectType verdict = assertInstanceOf(ObjectType.class, perQuestion.getOpenRestriction().orElseThrow());
    assertTrue(verdict.getFieldByName("action").isPresent());
    assertTrue(verdict.getFieldByName("reasons").isPresent());
    assertEquals(4, payload.getFields().size());
  }

  @Test
  void inlinePolicyIsAnOpenMapOfOptionalRuleKeys() {
    ObjectType policy = assertInstanceOf(ObjectType.class, new PolicyInputResolver().getStaticMetadata());
    assertTrue(policy.isOpen());
    ObjectType rule = assertInstanceOf(ObjectType.class, policy.getOpenRestriction().orElseThrow());
    assertTrue(rule.getFieldByName("options").isPresent());
    assertTrue(rule.getFieldByName("yesAbove").isPresent());
    assertTrue(rule.getFieldByName("otherOptions").isPresent());
    rule.getFields().forEach(field -> assertFalse(field.isRequired(), field.getKey().getName().getLocalPart()));
  }

  @Test
  void attributesAreExplicitlyEmpty() {
    assertInstanceOf(NullType.class, new NullInputAttributesResolver().getStaticMetadata());
    assertInstanceOf(NullType.class, new PolicyResultAttributesResolver().getStaticMetadata());
  }
}
