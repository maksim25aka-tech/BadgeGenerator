// BadgeGenerator.java
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import com.google.gson.*;
import java.util.*;
import java.util.List;

public class BadgeGenerator {
    static class Template {
        int badgeWidth, badgeHeight;
        String background;
        int nameFontSize, titleFontSize, companyFontSize;
        int[] qrPosition;
        int[] textPosition;
        String textColor;
    }

    private static Template defaultTemplate() {
        Template t = new Template();
        t.badgeWidth = 600;
        t.badgeHeight = 400;
        t.background = "#FFFFFF";
        t.nameFontSize = 40;
        t.titleFontSize = 24;
        t.companyFontSize = 28;
        t.qrPosition = new int[]{380, 80};
        t.textPosition = new int[]{50, 100};
        t.textColor = "#000000";
        return t;
    }

    private static Template loadTemplate(String path) throws IOException {
        Gson gson = new Gson();
        try (Reader reader = Files.newBufferedReader(Paths.get(path))) {
            return gson.fromJson(reader, Template.class);
        }
    }

    private static int hexToRgb(String hex) {
        hex = hex.replace("#", "");
        return Integer.parseInt(hex, 16);
    }

    private static BufferedImage createQR(String data, int size, String color, String logoPath) throws WriterException, IOException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size);
        int qrColor = hexToRgb(color);
        MatrixToImageConfig config = new MatrixToImageConfig(qrColor, 0xFFFFFFFF);
        BufferedImage qr = MatrixToImageWriter.toBufferedImage(matrix, config);
        if (logoPath != null && !logoPath.isEmpty()) {
            BufferedImage logo = ImageIO.read(new File(logoPath));
            int logoSize = size / 4;
            Image scaled = logo.getScaledInstance(logoSize, logoSize, Image.SCALE_SMOOTH);
            BufferedImage logoImg = new BufferedImage(logoSize, logoSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = logoImg.createGraphics();
            g.drawImage(scaled, 0, 0, null);
            g.dispose();
            int x = (size - logoSize) / 2;
            int y = (size - logoSize) / 2;
            Graphics2D qrG = qr.createGraphics();
            qrG.drawImage(logoImg, x, y, null);
            qrG.dispose();
        }
        return qr;
    }

    public static void generateBadge(String data, String name, String title, String company,
                                     String output, int size, String color, String logoPath,
                                     Template template) throws Exception {
        BufferedImage qr = createQR(data, size, color, logoPath);
        // Create badge
        BufferedImage badge = new BufferedImage(template.badgeWidth, template.badgeHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = badge.createGraphics();
        g.setColor(Color.decode(template.background));
        g.fillRect(0, 0, template.badgeWidth, template.badgeHeight);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.decode(template.textColor));
        // Draw text
        Font nameFont = new Font("Arial", Font.PLAIN, template.nameFontSize);
        Font titleFont = new Font("Arial", Font.PLAIN, template.titleFontSize);
        Font companyFont = new Font("Arial", Font.PLAIN, template.companyFontSize);
        int tx = template.textPosition[0];
        int ty = template.textPosition[1];
        g.setFont(nameFont);
        g.drawString(name, tx, ty);
        if (title != null && !title.isEmpty()) {
            g.setFont(titleFont);
            g.drawString(title, tx, ty + template.nameFontSize + 10);
        }
        if (company != null && !company.isEmpty()) {
            g.setFont(companyFont);
            g.drawString(company, tx, ty + template.nameFontSize + template.titleFontSize + 20);
        }
        // Draw QR
        int qx = template.qrPosition[0];
        int qy = template.qrPosition[1];
        g.drawImage(qr, qx, qy, null);
        g.dispose();

        // Save
        String ext = output.substring(output.lastIndexOf('.') + 1).toLowerCase();
        if (ext.equals("png")) {
            ImageIO.write(badge, "png", new File(output));
        } else {
            // PDF not implemented for simplicity
            ImageIO.write(badge, "png", new File(output));
        }
        System.out.println("Badge saved to " + output);
    }

    public static void main(String[] args) {
        String data = null, name = null, title = null, company = null, output = "badge.png";
        int size = 200;
        String color = "#000000";
        String logo = null;
        String templatePath = null;
        String batch = null;
        String outputDir = ".";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--data": data = args[++i]; break;
                case "--name": name = args[++i]; break;
                case "--title": title = args[++i]; break;
                case "--company": company = args[++i]; break;
                case "--output": output = args[++i]; break;
                case "--size": size = Integer.parseInt(args[++i]); break;
                case "--color": color = args[++i]; break;
                case "--logo": logo = args[++i]; break;
                case "--template": templatePath = args[++i]; break;
                case "--batch": batch = args[++i]; break;
                case "--output-dir": outputDir = args[++i]; break;
            }
        }

        try {
            Template template = (templatePath != null) ? loadTemplate(templatePath) : defaultTemplate();

            if (batch != null) {
                // CSV parsing
                List<String[]> rows = new ArrayList<>();
                try (BufferedReader br = new BufferedReader(new FileReader(batch))) {
                    String line;
                    String[] header = null;
                    while ((line = br.readLine()) != null) {
                        if (header == null) {
                            header = line.split(",");
                        } else {
                            rows.add(line.split(","));
                        }
                    }
                }
                for (String[] row : rows) {
                    Map<String, String> map = new HashMap<>();
                    for (int i = 0; i < header.length; i++) {
                        if (i < row.length) map.put(header[i], row[i]);
                    }
                    String d = map.getOrDefault("data", "");
                    String n = map.getOrDefault("name", d);
                    String t = map.getOrDefault("title", "");
                    String c = map.getOrDefault("company", "");
                    String out = map.getOrDefault("output", n + ".png");
                    String outPath = outputDir + "/" + out;
                    generateBadge(d, n, t, c, outPath, size, color, logo, template);
                }
            } else {
                if (data == null) { System.err.println("Error: --data required"); System.exit(1); }
                if (name == null) name = data;
                generateBadge(data, name, title, company, output, size, color, logo, template);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
