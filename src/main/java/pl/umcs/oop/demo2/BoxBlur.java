package pl.umcs.oop.demo2;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;

public class BoxBlur {
    // Przekształca obraz algorytmem box blur z równoległością
    public static void blur(File input, File output, int kernelSize) throws IOException, InterruptedException {
        BufferedImage src = ImageIO.read(input);
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());

        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(cores);

        int height = src.getHeight();
        int chunk = (height + cores - 1) / cores;

        CountDownLatch latch = new CountDownLatch(cores);

        for (int t = 0; t < cores; t++) {
            int startY = t * chunk;
            int endY = Math.min((t + 1) * chunk, height);
            executor.submit(() -> {
                boxBlurSection(src, dst, kernelSize, startY, endY);
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        ImageIO.write(dst, "png", output);
    }

    // Przekształca fragment obrazu
    private static void boxBlurSection(BufferedImage src, BufferedImage dst, int kernel, int startY, int endY) {
        int w = src.getWidth();
        int h = src.getHeight();
        int k = kernel / 2;

        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0, count = 0;
                for (int dy = -k; dy <= k; dy++) {
                    for (int dx = -k; dx <= k; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                            int rgb = src.getRGB(nx, ny);
                            r += (rgb >> 16) & 0xFF;
                            g += (rgb >> 8) & 0xFF;
                            b += rgb & 0xFF;
                            count++;
                        }
                    }
                }
                int nr = r / count, ng = g / count, nb = b / count;
                int rgb = (0xFF << 24) | (nr << 16) | (ng << 8) | nb;
                dst.setRGB(x, y, rgb);
            }
        }
    }
}