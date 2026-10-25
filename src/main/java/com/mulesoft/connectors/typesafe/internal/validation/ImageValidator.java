package com.mulesoft.connectors.typesafe.internal.validation;

import com.mulesoft.connectors.typesafe.internal.domain.ImageAsset;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Enforces the documented image limits locally, before any billed call, so a request the API would reject with
 * {@code 400} or {@code 413} fails as {@code TYPESAFE:INVALID_IMAGE} instead. See
 * <a href="https://docs.typesafe.ai/images#limits">https://docs.typesafe.ai/images#limits</a>.
 */
public final class ImageValidator {

  public static final int MAX_IMAGE_BYTES = 16 * 1024 * 1024;
  public static final long MAX_TOTAL_IMAGE_BYTES = 32L * 1024 * 1024;
  public static final int MIN_SIDE = 280;
  public static final int MAX_SIDE = 1568;
  public static final long MIN_PIXELS = 78_400L;
  public static final long MAX_PIXELS = 2_458_624L;
  public static final int MAX_REFERENCE_BYTES = 1024;
  public static final int MAX_TOTAL_REFERENCE_BYTES = 65_536;

  private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

  /** {@code png} or {@code jpg} from the file signature, or {@code null} when the bytes are neither. */
  public static String detectFormat(byte[] bytes) {
    if (bytes == null) {
      return null;
    }
    if (bytes.length >= PNG_MAGIC.length) {
      boolean png = true;
      for (int i = 0; i < PNG_MAGIC.length; i++) {
        if (bytes[i] != PNG_MAGIC[i]) {
          png = false;
          break;
        }
      }
      if (png) {
        return "png";
      }
    }
    if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
      return "jpg";
    }
    return null;
  }

  /**
   * Checks every image and its reference against the request it travels with. {@code state} and {@code questions} are
   * searched for each reference in string values; object keys are never replaced by the API, so they do not count.
   */
  public ValidationResult validate(List<ImageAsset> images, JsonNode state, JsonNode questions,
      boolean hasBoxQuestion) {
    List<String> errors = new ArrayList<>();
    if (hasBoxQuestion && images.isEmpty()) {
      errors.add("a Box question needs an image in the request; supply one under Images");
    }

    List<String> strings = new ArrayList<>();
    collectStrings(state, strings);
    collectStrings(questions, strings);

    long totalBytes = 0;
    int totalReferenceBytes = 0;
    for (ImageAsset image : images) {
      String ref = image.reference();
      if (ref == null || ref.isBlank()) {
        errors.add("image references must not be empty or only whitespace");
        continue;
      }
      int refBytes = ref.getBytes(StandardCharsets.UTF_8).length;
      totalReferenceBytes += refBytes;
      if (refBytes > MAX_REFERENCE_BYTES) {
        errors.add(
            "image reference '" + abbreviate(ref) + "' is " + refBytes + " bytes; the limit is " + MAX_REFERENCE_BYTES);
      }
      if (!isReferenced(ref, strings)) {
        errors.add("image '" + abbreviate(ref)
            + "' is not referenced in the state, instructions or criteria; every image must be used");
      }
      totalBytes += image.bytes().length;
      validateImage(image, errors);
    }
    if (totalReferenceBytes > MAX_TOTAL_REFERENCE_BYTES) {
      errors.add("image references total " + totalReferenceBytes + " bytes; the limit is " + MAX_TOTAL_REFERENCE_BYTES);
    }
    if (totalBytes > MAX_TOTAL_IMAGE_BYTES) {
      errors.add("images total " + totalBytes + " bytes; the request limit is " + MAX_TOTAL_IMAGE_BYTES);
    }
    return new ValidationResult(errors, List.of());
  }

  private static void validateImage(ImageAsset image, List<String> errors) {
    String label = "image '" + abbreviate(image.reference()) + "'";
    byte[] bytes = image.bytes();
    if (bytes.length > MAX_IMAGE_BYTES) {
      errors.add(label + " is " + bytes.length + " bytes; the limit is " + MAX_IMAGE_BYTES + " per image");
      return;
    }
    if ("png".equals(image.format()) && isAnimatedPng(bytes)) {
      errors.add(label + " is an animated PNG; send a single still frame");
      return;
    }
    int[] size = dimensions(bytes);
    if (size == null) {
      return;
    }
    int width = size[0];
    int height = size[1];
    long pixels = (long) width * height;
    if (width < MIN_SIDE || height < MIN_SIDE || width > MAX_SIDE || height > MAX_SIDE) {
      errors.add(label + " is " + width + "x" + height + "; each side must be " + MIN_SIDE + " to " + MAX_SIDE
          + " pixels (the API rejects, never resizes)");
    } else if (pixels < MIN_PIXELS || pixels > MAX_PIXELS) {
      errors.add(label + " has " + pixels + " pixels; the range is " + MIN_PIXELS + " to " + MAX_PIXELS);
    }
  }

  /** Width and height from the image header, or {@code null} when no reader can parse it (the API then decides). */
  private static int[] dimensions(byte[] bytes) {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      if (in == null) {
        return null;
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) {
        return null;
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(in, true, true);
        return new int[]{reader.getWidth(0), reader.getHeight(0)};
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  /** An APNG declares an {@code acTL} chunk before its first {@code IDAT}. */
  private static boolean isAnimatedPng(byte[] bytes) {
    int offset = PNG_MAGIC.length;
    while (offset + 8 <= bytes.length) {
      long length = ((bytes[offset] & 0xFFL) << 24) | ((bytes[offset + 1] & 0xFFL) << 16)
          | ((bytes[offset + 2] & 0xFFL) << 8) | (bytes[offset + 3] & 0xFFL);
      String type = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
      if ("acTL".equals(type)) {
        return true;
      }
      if ("IDAT".equals(type)) {
        return false;
      }
      offset += 12 + (int) Math.min(length, Integer.MAX_VALUE - 12L);
      if (offset < 0) {
        return false;
      }
    }
    return false;
  }

  private static boolean isReferenced(String ref, List<String> strings) {
    for (String value : strings) {
      if (value.contains(ref)) {
        return true;
      }
    }
    return false;
  }

  private static void collectStrings(JsonNode node, List<String> out) {
    if (node == null) {
      return;
    }
    if (node.isTextual()) {
      out.add(node.asText());
    } else if (node.isContainerNode()) {
      node.elements().forEachRemaining(child -> collectStrings(child, out));
    }
  }

  private static String abbreviate(String ref) {
    return ref.length() <= 40 ? ref : ref.substring(0, 37) + "...";
  }
}
