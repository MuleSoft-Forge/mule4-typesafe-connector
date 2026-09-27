package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.model.ArrayType;
import org.mule.metadata.api.model.NullType;
import org.mule.metadata.api.model.ObjectType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Completeness check for List Models: the output payload keeps every field {@code GET /v1/models} returns, and both
 * input and output attributes are declared.
 */
class ModelListMetadataTest {

  @Test
  void outputPayloadMatchesModelsResponse() {
    ArrayType array = assertInstanceOf(ArrayType.class, new ModelListOutputResolver().getStaticMetadata());
    ObjectType item = assertInstanceOf(ObjectType.class, array.getType());
    assertTrue(item.getFieldByName("name").isPresent());
    assertTrue(item.getFieldByName("description").isPresent());
    assertTrue(item.getFieldByName("release_date").isPresent());
    assertTrue(item.getFieldByName("route").isPresent());
    assertEquals(4, item.getFields().size());
  }

  @Test
  void outputAttributesCarryCountAndEachCall() {
    ObjectType attributes = assertInstanceOf(ObjectType.class, new ModelListAttributesResolver().getStaticMetadata());
    assertTrue(attributes.getFieldByName("count").isPresent());
    ArrayType calls = assertInstanceOf(ArrayType.class, attributes.getFieldByName("calls").orElseThrow().getValue());
    ObjectType call = assertInstanceOf(ObjectType.class, calls.getType());
    assertTrue(call.getFieldByName("route").isPresent());
    assertTrue(call.getFieldByName("statusCode").isPresent());
    assertTrue(call.getFieldByName("requestId").isPresent());
  }

  @Test
  void inputPayloadAndAttributesAreExplicitlyEmpty() {
    assertInstanceOf(NullType.class, new ModelListInputResolver().getStaticMetadata());
    assertInstanceOf(NullType.class, new ModelListInputAttributesResolver().getStaticMetadata());
  }
}
