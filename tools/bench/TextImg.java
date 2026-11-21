import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class TextImg {
    public static void main(String[] args) throws Exception {
        int w = 1200, h = 800;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("SansSerif", Font.BOLD, 52));
        String[] lines = {
                "JavaVision Plus OCR Benchmark",
                "The quick brown fox jumps",
                "over the lazy dog 1234567890",
                "Invoice No. A-2026-1113",
                "Total: 1280.00 CNY",
                "Please verify the signature"
        };
        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], 60, 120 + i * 110);
        }
        g.setFont(new Font("Serif", Font.PLAIN, 40));
        g.drawString("Benchmark fixture generated at build time", 60, 760);
        g.dispose();
        File out = new File(args.length > 0 ? args[0] : "image/ocr-bench.png");
        ImageIO.write(img, "png", out);
        System.out.println("written " + out.getAbsolutePath() + " " + out.length() + " bytes");
    }
}
