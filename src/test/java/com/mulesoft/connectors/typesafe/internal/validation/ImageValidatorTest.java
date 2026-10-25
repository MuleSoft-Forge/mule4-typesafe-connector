package com.mulesoft.connectors.typesafe.internal.validation;

import com.mulesoft.connectors.typesafe.internal.domain.ImageAsset;
import com.mulesoft.connectors.typesafe.internal.support.TestImages;
import com.mulesoft.connectors.typesafe.internal.util.Json;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageValidatorTest {

  private static final String REF = "a3f1c2e8-door";

  private final ImageValidator validator = new ImageValidator();

  private static JsonNode questions(String instructions) {
    return Json.read("{\"q\":{\"type\":\"noul\",\"instructions\":\"" + instructions + "\"}}");
  }

  private static ImageAsset png(String ref, int width, int height) {
    return new ImageAsset(ref, TestImages.png(width, height), "png");
  }

  @Test
  void detectsPngAndJpegFromTheirSignatures() {
    assertEquals("png", ImageValidator.detectFormat(TestImages.png(300, 300)));
    assertEquals("jpg", ImageValidator.detectFormat(TestImages.jpeg(300, 300)));
    assertNull(ImageValidator.detectFormat("GIF89a".getBytes(StandardCharsets.US_ASCII)));
  }

  @Test
  void acceptsAnImageReferencedFromTheState() {
    ValidationResult result = validator.validate(List.of(png(REF, 320, 320)), Json.read("{\"door\":\"" + REF + "\"}"),
        questions("Is there a person in `door`?"), false);
    assertTrue(result.isValid(), result.getErrors().toString());
  }

  @Test
  void acceptsAReferenceInsideALongerInstruction() {
    ValidationResult result = validator.validate(List.of(png(REF, 320, 320)), Json.read("{}"),
        questions("Is this the same dog as in " + REF + "?"), false);
    assertTrue(result.isValid(), result.getErrors().toString());
  }

  @Test
  void rejectsAnUnreferencedImage() {
    ValidationResult result = validator.validate(List.of(png(REF, 320, 320)), Json.read("{\"door\":\"other\"}"),
        questions("Is there a person?"), false);
    assertFalse(result.isValid());
    assertTrue(result.getErrors().get(0).contains("not referenced"));
  }

  @Test
  void objectKeysDoNotCountAsReferences() {
    ValidationResult result = validator.validate(List.of(png(REF, 320, 320)), Json.read("{\"" + REF + "\":\"text\"}"),
        questions("Is there a person?"), false);
    assertFalse(result.isValid());
  }

  @Test
  void rejectsSidesOutsideTheDocumentedRange() {
    ValidationResult small = validator.validate(List.of(png(REF, 200, 400)), Json.read("{\"p\":\"" + REF + "\"}"),
        questions("?"), false);
    assertFalse(small.isValid());
    assertTrue(small.getErrors().get(0).contains("200x400"));

    ValidationResult large = validator.validate(List.of(png(REF, 1600, 400)), Json.read("{\"p\":\"" + REF + "\"}"),
        questions("?"), false);
    assertFalse(large.isValid());
  }

  @Test
  void requiresAnImageForABoxQuestion() {
    ValidationResult result = validator.validate(List.of(), Json.read("{}"), questions("?"), true);
    assertFalse(result.isValid());
    assertTrue(result.getErrors().get(0).contains("Box question needs an image"));
  }

  @Test
  void rejectsBlankAndOverlongReferences() {
    assertFalse(validator.validate(List.of(png(" ", 320, 320)), Json.read("{}"), questions("?"), false).isValid());

    char[] chars = new char[ImageValidator.MAX_REFERENCE_BYTES + 1];
    Arrays.fill(chars, 'r');
    String longRef = new String(chars);
    ValidationResult result = validator.validate(List.of(png(longRef, 320, 320)),
        Json.read("{\"p\":\"" + longRef + "\"}"), questions("?"), false);
    assertFalse(result.isValid());
    assertTrue(result.getErrors().get(0).contains("bytes; the limit is 1024"));
  }

  @Test
  void rejectsAnAnimatedPng() {
    byte[] still = TestImages.png(320, 320);
    byte[] animated = insertChunkAfterHeader(still, "acTL");
    ValidationResult result = validator.validate(List.of(new ImageAsset(REF, animated, "png")),
        Json.read("{\"p\":\"" + REF + "\"}"), questions("?"), false);
    assertFalse(result.isValid());
    assertTrue(result.getErrors().get(0).contains("animated PNG"));
  }

  /** Inserts an empty chunk of {@code type} straight after IHDR (signature 8 bytes + IHDR 25 bytes). */
  private static byte[] insertChunkAfterHeader(byte[] png, String type) {
    int at = 8 + 25;
    byte[] chunk = new byte[12];
    byte[] tag = type.getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(tag, 0, chunk, 4, 4);
    byte[] out = new byte[png.length + chunk.length];
    System.arraycopy(png, 0, out, 0, at);
    System.arraycopy(chunk, 0, out, at, chunk.length);
    System.arraycopy(png, at, out, at + chunk.length, png.length - at);
    return out;
  }
}
