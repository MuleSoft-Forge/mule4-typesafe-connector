package com.mulesoft.connectors.typesafe.internal.questionset;

import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.typesafe.internal.error.TypeSafeErrorType;
import com.mulesoft.connectors.typesafe.internal.util.Json;
import com.mulesoft.connectors.typesafe.internal.validation.QuestionSetValidator;
import com.mulesoft.connectors.typesafe.internal.validation.ValidationResult;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionSetLoaderTest {

  @Test
  void loadsBundledQuestionSetByBareName() {
    QuestionSet set = QuestionSetLoader.load("questions/", "ticket-triage");
    assertEquals("ticket-triage", set.id());
    assertEquals("1.0.0", set.version());
    assertTrue(set.questions().has("team"));
    assertTrue(set.questions().has("urgent"));
    assertNotNull(set.policy());
    assertTrue(set.policy().has("team"));
  }

  @Test
  void bundledQuestionSetSpeaksTypeSafeShape() {
    QuestionSet set = QuestionSetLoader.load("questions/", "ticket-triage");
    ValidationResult result = new QuestionSetValidator().validate(set.questions(), Map.of("team", "other"));
    assertTrue(result.isValid(), "ticket-triage.json must pass validation: " + result.getErrors());
  }

  @Test
  void toleratesJsonSuffixAndMissingTrailingSlash() {
    QuestionSet set = QuestionSetLoader.load("questions", "ticket-triage.json");
    assertEquals("ticket-triage", set.id());
  }

  @Test
  void listsBundledFiles() {
    Set<String> names = QuestionSetLoader.list("questions/");
    assertTrue(names.contains("ticket-triage.json"), "expected ticket-triage.json in " + names);
  }

  @Test
  void listsBundledFilesWhenContextClassLoaderCannotSeeThem() {
    ClassLoader previous = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
    });
    try {
      Set<String> names = QuestionSetLoader.list("questions/");
      assertTrue(names.contains("ticket-triage.json"), "expected ticket-triage.json in " + names);
      assertEquals("ticket-triage", QuestionSetLoader.load("questions/", "ticket-triage").id());
    } finally {
      Thread.currentThread().setContextClassLoader(previous);
    }
  }

  @Test
  void missingFileRaisesInvalidQuestionSet() {
    ModuleException e = assertThrows(ModuleException.class,
        () -> QuestionSetLoader.load("questions/", "does-not-exist"));
    assertEquals(TypeSafeErrorType.INVALID_QUESTION_SET, e.getType());
  }

  @Test
  void parseUsesFallbackIdAndTreatsMissingPolicyAsNull() {
    QuestionSet set = QuestionSetLoader.parse("fallback",
        Json.read("{\"questions\":{\"q\":{\"type\":\"noul\",\"instructions\":\"ok?\"}}}"));
    assertEquals("fallback", set.id());
    assertNull(set.policy());
    assertNull(set.version());
  }

  @Test
  void parseRejectsEmptyQuestions() {
    ModuleException e = assertThrows(ModuleException.class,
        () -> QuestionSetLoader.parse("x", Json.read("{\"questions\":{}}")));
    assertEquals(TypeSafeErrorType.INVALID_QUESTION_SET, e.getType());
  }
}
