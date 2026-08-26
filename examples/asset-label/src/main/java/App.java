import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

public class App {

    static final ObjectMapper M = new ObjectMapper();
    static final String[] FONT_PATHS = {
            "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
            "/usr/share/fonts/opentype/nanum/NanumGothic.ttf",
            "/usr/share/fonts/truetype/nanum/NanumBarunGothic.ttf",
    };

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/healthz", ex -> respond(ex, 200, "application/json",
                "{\"status\":\"ok\",\"service\":\"asset-label\"}".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/api/labels", App::handleLabels);
        server.createContext("/", App::handleRoot);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("asset-label listening on :" + port);
    }

    static void handleRoot(HttpExchange ex) throws java.io.IOException {
        if (!ex.getRequestURI().getPath().equals("/")) {
            respond(ex, 404, "text/plain", "not found".getBytes());
            return;
        }
        try (InputStream in = App.class.getResourceAsStream("/index.html")) {
            respond(ex, 200, "text/html; charset=utf-8", in.readAllBytes());
        }
    }

    static void handleLabels(HttpExchange ex) throws java.io.IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            error(ex, 405, "METHOD", "POST 만 허용합니다.");
            return;
        }
        try {
            JsonNode body = M.readTree(ex.getRequestBody().readAllBytes());
            List<String[]> assets = new ArrayList<>();
            JsonNode arr = body.get("assets");
            if (arr != null && arr.isArray()) {
                for (JsonNode a : arr) {
                    String code = txt(a.get("code"));
                    String name = txt(a.get("name"));
                    if (code.isEmpty() && name.isEmpty()) continue;
                    assets.add(new String[]{code, name});
                }
            }
            if (assets.isEmpty()) {
                error(ex, 400, "VALIDATION", "비품 목록을 1개 이상 입력하세요.");
                return;
            }
            if (assets.size() > 500) {
                error(ex, 400, "VALIDATION", "한 번에 최대 500개까지 생성할 수 있습니다.");
                return;
            }
            int cols = clamp(body.has("cols") ? body.get("cols").asInt() : 3, 1, 5);
            int rows = clamp(body.has("rows") ? body.get("rows").asInt() : 8, 1, 12);
            String title = txt(body.get("title"));
            boolean includeName = !body.has("qrContent") || "codename".equals(txt(body.get("qrContent")));

            byte[] pdf = buildPdf(assets, cols, rows, title, includeName);
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Disposition", "attachment; filename*=UTF-8''" + url("비품라벨.pdf"));
            respond(ex, 200, "application/pdf", pdf, headers);
        } catch (Exception e) {
            error(ex, 400, "PDF", "라벨 생성 중 오류: " + e.getMessage());
        }
    }

    static byte[] buildPdf(List<String[]> assets, int cols, int rows, String title, boolean includeName) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDFont font = loadKoreanFont(doc);
            PDFont ascii = PDType1Font.HELVETICA;
            float pw = PDRectangle.A4.getWidth(), ph = PDRectangle.A4.getHeight();
            float margin = 28f;
            float topOffset = title.isEmpty() ? margin : margin + 22f;
            float usableW = pw - margin * 2;
            float usableH = ph - topOffset - margin;
            float cellW = usableW / cols;
            float cellH = usableH / rows;
            int perPage = cols * rows;

            PDPageContentStream cs = null;
            for (int i = 0; i < assets.size(); i++) {
                int pos = i % perPage;
                if (pos == 0) {
                    if (cs != null) cs.close();
                    PDPage page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    if (!title.isEmpty()) {
                        cs.beginText();
                        cs.setFont(font, 13);
                        cs.newLineAtOffset(margin, ph - margin - 2);
                        cs.showText(safe(font, title));
                        cs.endText();
                    }
                }
                int r = pos / cols, c = pos % cols;
                float cellX = margin + c * cellW;
                float cellTopY = ph - topOffset - r * cellH;
                float cellBottomY = cellTopY - cellH;

                // 테두리
                cs.setLineWidth(0.5f);
                cs.setStrokingColor(180, 187, 195);
                cs.addRect(cellX + 3, cellBottomY + 3, cellW - 6, cellH - 6);
                cs.stroke();

                String code = assets.get(i)[0];
                String name = assets.get(i)[1];
                String qrText = includeName && !name.isEmpty() ? (code + " " + name) : (code.isEmpty() ? name : code);

                float qrSize = Math.min(cellH - 18, cellW * 0.42f);
                float qx = cellX + 10;
                float qy = cellBottomY + (cellH - qrSize) / 2;
                PDImageXObject qr = LosslessFactory.createFromImage(doc, qrImage(qrText, 220));
                cs.drawImage(qr, qx, qy, qrSize, qrSize);

                float tx = qx + qrSize + 10;
                float tw = cellX + cellW - 8 - tx;
                float ty = cellTopY - 26;
                drawText(cs, font, ascii, clip(name.isEmpty() ? code : name, 12), tx, ty, 11);
                if (!code.isEmpty() && !name.isEmpty()) {
                    cs.setNonStrokingColor(100, 116, 139);
                    drawText(cs, font, ascii, clip(code, 16), tx, ty - 16, 9);
                    cs.setNonStrokingColor(0, 0, 0);
                }
            }
            if (cs != null) cs.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    static void drawText(PDPageContentStream cs, PDFont font, PDFont ascii, String text, float x, float y, float size) throws java.io.IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(safe(font, text));
        cs.endText();
    }

    static BufferedImage qrImage(String text, int size) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix matrix = writer.encode(text.isEmpty() ? " " : text, BarcodeFormat.QR_CODE, size, size, hints);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    static PDFont loadKoreanFont(PDDocument doc) {
        for (String p : FONT_PATHS) {
            File f = new File(p);
            if (f.exists()) {
                try {
                    return PDType0Font.load(doc, f);
                } catch (Exception ignore) {
                }
            }
        }
        return PDType1Font.HELVETICA; // 폰트 없으면 ASCII 폴백
    }

    // 한글 폰트가 아니면 비ASCII 제거(폴백 시 예외 방지)
    static String safe(PDFont font, String s) {
        if (font instanceof PDType0Font) return s;
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(ch < 128 ? ch : '?');
        return b.toString();
    }

    static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    static String txt(JsonNode n) { return n == null || n.isNull() ? "" : n.asText().trim(); }

    static String url(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static void error(HttpExchange ex, int code, String ecode, String msg) throws java.io.IOException {
        String json = "{\"error\":{\"code\":\"" + ecode + "\",\"message\":\"" + msg.replace("\"", "'") + "\"}}";
        respond(ex, code, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    static void respond(HttpExchange ex, int code, String ctype, byte[] body) throws java.io.IOException {
        respond(ex, code, ctype, body, null);
    }

    static void respond(HttpExchange ex, int code, String ctype, byte[] body, Map<String, String> headers) throws java.io.IOException {
        ex.getResponseHeaders().set("Content-Type", ctype);
        if (headers != null) headers.forEach((k, v) -> ex.getResponseHeaders().set(k, v));
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
