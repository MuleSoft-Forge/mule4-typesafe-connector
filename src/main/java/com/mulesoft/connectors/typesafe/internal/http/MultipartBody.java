package com.mulesoft.connectors.typesafe.internal.http;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A {@code multipart/form-data} body in the shape System One requires: the JSON request first, in a part named
 * {@code request}, then one file part per image, named by the asset's {@code part}. See
 * <a href="https://docs.typesafe.ai/images#over-http">https://docs.typesafe.ai/images#over-http</a>.
 */
public final class MultipartBody {

  public static final String REQUEST_PART = "request";

  private final String boundary;
  private final byte[] json;
  private final List<FilePart> files = new ArrayList<>();

  private MultipartBody(String boundary, byte[] json) {
    this.boundary = boundary;
    this.json = json;
  }

  /** Starts a body whose first part is the JSON request. */
  public static MultipartBody withRequest(String requestJson) {
    return new MultipartBody("typesafe-" + UUID.randomUUID().toString().replace("-", ""),
        requestJson.getBytes(StandardCharsets.UTF_8));
  }

  /** Adds one image file part. {@code name} must be unique and not {@code request}. */
  public MultipartBody file(String name, String contentType, byte[] content) {
    files.add(new FilePart(name, contentType, content));
    return this;
  }

  /** The {@code Content-Type} header value, including the boundary. */
  public String contentType() {
    return "multipart/form-data; boundary=" + boundary;
  }

  public byte[] toBytes() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    writeHeader(out,
        "Content-Disposition: form-data; name=\"" + REQUEST_PART + "\"\r\n" + "Content-Type: application/json\r\n");
    out.writeBytes(json);
    out.writeBytes(crlf());
    for (FilePart file : files) {
      writeHeader(out, "Content-Disposition: form-data; name=\"" + file.name + "\"; filename=\"" + file.name + "\"\r\n"
          + "Content-Type: " + file.contentType + "\r\n");
      out.writeBytes(file.content);
      out.writeBytes(crlf());
    }
    out.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
    return out.toByteArray();
  }

  private void writeHeader(ByteArrayOutputStream out, String headers) {
    out.writeBytes(("--" + boundary + "\r\n" + headers + "\r\n").getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] crlf() {
    return new byte[]{'\r', '\n'};
  }

  private static final class FilePart {

    private final String name;
    private final String contentType;
    private final byte[] content;

    private FilePart(String name, String contentType, byte[] content) {
      this.name = name;
      this.contentType = contentType;
      this.content = content;
    }
  }
}
