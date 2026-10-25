package com.mulesoft.connectors.typesafe.internal.domain;

import com.mulesoft.connectors.typesafe.internal.util.Json;

/**
 * One image sent with a decision. {@code reference} is the placeholder string the caller wrote into {@code state},
 * {@code instructions} or {@code criteria}; the API swaps each occurrence for the image. The bytes travel as a
 * multipart file part, never as base64 in the JSON.
 */
public final class ImageAsset {

  private final String reference;
  private final byte[] bytes;
  private final String format;
  private final String sha256;

  public ImageAsset(String reference, byte[] bytes, String format) {
    this.reference = reference;
    this.bytes = bytes;
    this.format = format;
    this.sha256 = Json.sha256Bytes(bytes);
  }

  public String reference() {
    return reference;
  }

  public byte[] bytes() {
    return bytes;
  }

  /** {@code png} or {@code jpg}, detected from the bytes. */
  public String format() {
    return format;
  }

  /** The multipart content type for this image's file part. */
  public String contentType() {
    return "png".equals(format) ? "image/png" : "image/jpeg";
  }

  /** SHA-256 of the image bytes, lowercase hex. Used in cache keys so a different image is a different decision. */
  public String sha256() {
    return sha256;
  }
}
