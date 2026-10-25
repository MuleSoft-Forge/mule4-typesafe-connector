package com.mulesoft.connectors.typesafe.internal.operation;

import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.streaming.bytes.CursorStreamProvider;
import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.typesafe.internal.domain.DecisionRequest;
import com.mulesoft.connectors.typesafe.internal.domain.ImageAsset;
import com.mulesoft.connectors.typesafe.internal.error.TypeSafeErrorType;
import com.mulesoft.connectors.typesafe.internal.validation.ImageValidator;
import com.mulesoft.connectors.typesafe.internal.validation.ValidationResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Turns the {@code images} parameter (reference → image binary) into validated {@link ImageAsset}s. Each value is the
 * raw PNG or JPEG file, typically {@code vars.photo} from a File read or an HTTP body. A value that is not a PNG or
 * JPEG file but is base64 text of one (with or without a {@code data:} prefix) is decoded, so flows that already hold
 * base64 need no extra step.
 */
final class ImageInputs {

  private ImageInputs() {
  }

  /** Reads and decodes every image. An empty or absent map yields an empty list. */
  static List<ImageAsset> read(Map<String, ?> images) {
    List<ImageAsset> assets = new ArrayList<>();
    if (images == null) {
      return assets;
    }
    for (Map.Entry<String, ?> entry : images.entrySet()) {
      String reference = entry.getKey();
      byte[] bytes = bytesOf(reference, entry.getValue());
      String format = ImageValidator.detectFormat(bytes);
      if (format == null) {
        byte[] decoded = decodeBase64(bytes);
        String decodedFormat = ImageValidator.detectFormat(decoded);
        if (decodedFormat == null) {
          throw new ModuleException(
              "image '" + reference + "' is not a PNG or JPEG file; convert GIF, WebP, HEIC and other formats first",
              TypeSafeErrorType.INVALID_IMAGE);
        }
        bytes = decoded;
        format = decodedFormat;
      }
      assets.add(new ImageAsset(reference, bytes, format));
    }
    return assets;
  }

  /** Raises {@code TYPESAFE:INVALID_IMAGE} when the images do not fit the request or the documented limits. */
  static void validate(DecisionRequest request) {
    ValidationResult result = new ImageValidator().validate(request.images(), request.state(), request.questions(),
        request.hasBoxQuestion());
    if (!result.isValid()) {
      throw new ModuleException("Invalid images: " + String.join("; ", result.getErrors()),
          TypeSafeErrorType.INVALID_IMAGE);
    }
  }

  /** Accepts the shapes a DataWeave map value can take at runtime: streams, repeatable streams, bytes and text. */
  private static byte[] bytesOf(String reference, Object value) {
    Object raw = value instanceof TypedValue ? ((TypedValue<?>) value).getValue() : value;
    if (raw instanceof CursorStreamProvider) {
      return readCapped(reference, ((CursorStreamProvider) raw).openCursor());
    }
    if (raw instanceof InputStream) {
      return readCapped(reference, (InputStream) raw);
    }
    if (raw instanceof byte[]) {
      return readCapped(reference, new ByteArrayInputStream((byte[]) raw));
    }
    if (raw instanceof String) {
      return readCapped(reference, new ByteArrayInputStream(((String) raw).getBytes(StandardCharsets.US_ASCII)));
    }
    if (raw == null) {
      throw new ModuleException("image '" + reference + "' has no content", TypeSafeErrorType.INVALID_IMAGE);
    }
    throw new ModuleException(
        "image '" + reference + "' must be binary or base64 text, not " + raw.getClass().getSimpleName(),
        TypeSafeErrorType.INVALID_IMAGE);
  }

  /** Reads the stream, refusing anything larger than base64 of the per-image limit so a huge stream fails fast. */
  private static byte[] readCapped(String reference, InputStream stream) {
    long cap = (ImageValidator.MAX_IMAGE_BYTES / 3L + 1) * 4L + 64;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[64 * 1024];
    long total = 0;
    try (InputStream in = stream) {
      int n;
      while ((n = in.read(buffer)) != -1) {
        total += n;
        if (total > cap) {
          throw new ModuleException("image '" + reference + "' is larger than the " + ImageValidator.MAX_IMAGE_BYTES
              + "-byte per-image limit", TypeSafeErrorType.INVALID_IMAGE);
        }
        out.write(buffer, 0, n);
      }
    } catch (IOException e) {
      throw new ModuleException("Could not read image '" + reference + "'", TypeSafeErrorType.INVALID_IMAGE, e);
    }
    return out.toByteArray();
  }

  private static byte[] decodeBase64(byte[] bytes) {
    String text = new String(bytes, StandardCharsets.US_ASCII).trim();
    if (text.startsWith("data:")) {
      int comma = text.indexOf(',');
      text = comma < 0 ? "" : text.substring(comma + 1);
    }
    try {
      return Base64.getMimeDecoder().decode(text);
    } catch (IllegalArgumentException e) {
      return new byte[0];
    }
  }
}
