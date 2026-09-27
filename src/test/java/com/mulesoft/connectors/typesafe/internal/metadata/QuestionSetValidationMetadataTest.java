package com.mulesoft.connectors.typesafe.internal.metadata;

import org.mule.metadata.api.model.ArrayType;
import org.mule.metadata.api.model.NullType;
import org.mule.metadata.api.model.ObjectType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Completeness check for Validate Question Set. The output payload is the whole JSON document the operation writes, and
 * both attribute parts are declared even though the operation does not use them.
 */
class QuestionSetValidationMetadataTest {

  @Test
  void outputPayloadIsValidErrorsAndWarnings() {
    ObjectType payload = assertInstanceOf(ObjectType.class,
        new QuestionSetValidationOutputResolver().getStaticMetadata());
    assertTrue(payload.getFieldByName("valid").isPresent());
    assertInstanceOf(ArrayType.class, payload.getFieldByName("errors").orElseThrow().getValue());
    assertInstanceOf(ArrayType.class, payload.getFieldByName("warnings").orElseThrow().getValue());
    assertEquals(3, payload.getFields().size());
  }

  @Test
  void inputPayloadIsAnOpenMapOfQuestions() {
    ObjectType questions = assertInstanceOf(ObjectType.class, new QuestionsInputResolver().getStaticMetadata());
    assertTrue(questions.isOpen());
    ObjectType question = assertInstanceOf(ObjectType.class, questions.getOpenRestriction().orElseThrow());
    assertTrue(question.getFieldByName("type").isPresent());
    assertTrue(question.getFieldByName("instructions").isPresent());
    assertTrue(question.getFieldByName("criteria").isPresent());
    assertTrue(question.getFieldByName("noMatchOption").isPresent());
  }

  @Test
  void attributesAreExplicitlyEmpty() {
    assertInstanceOf(NullType.class, new QuestionSetValidationInputAttributesResolver().getStaticMetadata());
    assertInstanceOf(NullType.class, new QuestionSetValidationAttributesResolver().getStaticMetadata());
  }
}
