package com.mulesoft.connectors.typesafe.internal.operation;

import org.mule.runtime.api.metadata.TypedValue;
import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.typesafe.internal.domain.ImageAsset;
import com.mulesoft.connectors.typesafe.internal.error.TypeSafeErrorType;
import com.mulesoft.connectors.typesafe.internal.support.TestImages;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageInputsTest {

  @SuppressWarnings("unchecked")
  private static TypedValue<InputStream> binary(byte[] bytes) {
    TypedValue<InputStream> value = mock(TypedValue.class);
    when(value.getValue()).thenReturn(new ByteArrayInputStream(bytes));
    return value;
  }

  @Test
  void readsBinariesInOrderAndDetectsTheirFormat() {
    byte[] png = TestImages.png(300, 300);
    byte[] jpeg = TestImages.jpeg(300, 300);
    Map<String, Object> images = new LinkedHashMap<>();
    images.put("ref-png", binary(png));
    images.put("ref-jpg", binary(jpeg));

    List<ImageAsset> assets = ImageInputs.read(images);

    assertEquals(2, assets.size());
    assertEquals("ref-png", assets.get(0).reference());
    assertEquals("png", assets.get(0).format());
    assertEquals("image/png", assets.get(0).contentType());
    assertArrayEquals(png, assets.get(0).bytes());
    assertEquals("jpg", assets.get(1).format());
    assertEquals("image/jpeg", assets.get(1).contentType());
  }

  @Test
  void decodesBase64TextWithOrWithoutADataPrefix() {
    byte[] png = TestImages.png(300, 300);
    String base64 = Base64.getEncoder().encodeToString(png);
    Map<String, Object> images = new LinkedHashMap<>();
    images.put("plain", binary(base64.getBytes(StandardCharsets.US_ASCII)));
    images.put("uri", binary(("data:image/png;base64," + base64).getBytes(StandardCharsets.US_ASCII)));

    List<ImageAsset> assets = ImageInputs.read(images);

    assertArrayEquals(png, assets.get(0).bytes());
    assertArrayEquals(png, assets.get(1).bytes());
  }

  @Test
  void rejectsAFileThatIsNotPngOrJpeg() {
    Map<String, Object> images = Map.of("gif", binary("GIF89a....".getBytes(StandardCharsets.US_ASCII)));

    ModuleException e = assertThrows(ModuleException.class, () -> ImageInputs.read(images));
    assertEquals(TypeSafeErrorType.INVALID_IMAGE, e.getType());
    assertTrue(e.getMessage().contains("not a PNG or JPEG"));
  }

  @Test
  void acceptsRawBytesAndStreamsAsDataWeaveMayHandThemOver() {
    byte[] png = TestImages.png(300, 300);
    Map<String, Object> images = new LinkedHashMap<>();
    images.put("bytes", png);
    images.put("stream", new ByteArrayInputStream(png));

    List<ImageAsset> assets = ImageInputs.read(images);

    assertArrayEquals(png, assets.get(0).bytes());
    assertArrayEquals(png, assets.get(1).bytes());
  }

  @Test
  void rejectsAValueThatIsNotBinary() {
    Map<String, Object> images = Map.of("num", 42);

    ModuleException e = assertThrows(ModuleException.class, () -> ImageInputs.read(images));
    assertEquals(TypeSafeErrorType.INVALID_IMAGE, e.getType());
  }

  @Test
  void absentImagesYieldAnEmptyList() {
    assertTrue(ImageInputs.read(null).isEmpty());
  }
}
