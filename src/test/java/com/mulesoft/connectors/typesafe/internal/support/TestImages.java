package com.mulesoft.connectors.typesafe.internal.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/** Renders small real images in memory: a red rectangle on white, the same shape the live probe used. */
public final class TestImages {

  private TestImages() {
  }

  public static byte[] png(int width, int height) {
    return render(width, height, "png");
  }

  public static byte[] jpeg(int width, int height) {
    return render(width, height, "jpg");
  }

  private static byte[] render(int width, int height, String format) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, width, height);
    g.setColor(new Color(220, 30, 30));
    g.fillRect(width / 4, height / 3, width / 2, height / 3);
    g.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, format, out);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }
}
