package com.inkserve.web;

import com.inkserve.dto.RecognizeRequest;
import com.inkserve.dto.RecognizeResponse;
import com.inkserve.service.MnistService;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.translate.TranslateException;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RecognitionController {
    private final MnistService mnistService;

    public RecognitionController(MnistService mnistService) {
        this.mnistService = mnistService;
    }

    @PostMapping(value = "/recognize", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public RecognizeResponse recognize(@Valid @RequestBody RecognizeRequest request) throws IOException, TranslateException {
        byte[] imageBytes = decodeBase64Image(request.getImageBase64());
        byte[] normalizedBytes = normalizeToPng(imageBytes);
        Image image = mnistService.loadImage(normalizedBytes);
        Classifications classifications = mnistService.predict(image);

        List<RecognizeResponse.Score> topScores = mnistService.topK(classifications, 3).stream()
                .map(item -> new RecognizeResponse.Score(item.getClassName(), item.getProbability()))
                .collect(Collectors.toList());

        String normalized = "data:image/png;base64," + Base64.getEncoder().encodeToString(normalizedBytes);

        return new RecognizeResponse(
                classifications.best().getClassName(),
                topScores,
                normalized
        );
    }

    private byte[] decodeBase64Image(String input) {
        String payload = input;
        int commaIndex = input.indexOf(',');
        if (commaIndex >= 0) {
            payload = input.substring(commaIndex + 1);
        }
        return Base64.getDecoder().decode(payload);
    }

    private byte[] normalizeToPng(byte[] imageBytes) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
        BufferedImage gray = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g0 = gray.createGraphics();
        g0.drawImage(source, 0, 0, null);
        g0.dispose();

        // Find bounding box of non-black pixels.
        int minX = gray.getWidth();
        int minY = gray.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < gray.getHeight(); y++) {
            for (int x = 0; x < gray.getWidth(); x++) {
                int v = gray.getRaster().getSample(x, y, 0);
                if (v > 18) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }

        // If nothing drawn, return a blank 28x28 image.
        if (maxX < minX || maxY < minY) {
            BufferedImage blank = new BufferedImage(28, 28, BufferedImage.TYPE_BYTE_GRAY);
            return toPng(blank);
        }

        // Add padding around the bounding box to preserve loops.
        int pad = 4;
        minX = Math.max(0, minX - pad);
        minY = Math.max(0, minY - pad);
        maxX = Math.min(gray.getWidth() - 1, maxX + pad);
        maxY = Math.min(gray.getHeight() - 1, maxY + pad);

        int boxW = maxX - minX + 1;
        int boxH = maxY - minY + 1;
        BufferedImage cropped = gray.getSubimage(minX, minY, boxW, boxH);

        // Scale longest side to 20px, keep aspect.
        int targetSize = 18;
        int newW = targetSize;
        int newH = targetSize;
        if (boxW > boxH) {
            newW = targetSize;
            newH = Math.max(1, (int) Math.round((double) boxH * targetSize / boxW));
        } else {
            newH = targetSize;
            newW = Math.max(1, (int) Math.round((double) boxW * targetSize / boxH));
        }

        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g1 = scaled.createGraphics();
        g1.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g1.drawImage(cropped, 0, 0, newW, newH, null);
        g1.dispose();

        // Compute center of mass on the scaled image.
        double sum = 0;
        double sumX = 0;
        double sumY = 0;
        for (int y = 0; y < scaled.getHeight(); y++) {
            for (int x = 0; x < scaled.getWidth(); x++) {
                int v = scaled.getRaster().getSample(x, y, 0);
                double w = v / 255.0;
                sum += w;
                sumX += x * w;
                sumY += y * w;
            }
        }
        double cx = sum == 0 ? (scaled.getWidth() / 2.0) : (sumX / sum);
        double cy = sum == 0 ? (scaled.getHeight() / 2.0) : (sumY / sum);

        // Paste into 28x28 centered by mass.
        BufferedImage normalized = new BufferedImage(28, 28, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2 = normalized.createGraphics();
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, 28, 28);
        int offsetX = (int) Math.round(14 - cx);
        int offsetY = (int) Math.round(14 - cy);
        // Clamp offsets to keep the digit within bounds.
        offsetX = Math.max(-2, Math.min(28 - newW + 2, offsetX));
        offsetY = Math.max(-2, Math.min(28 - newH + 2, offsetY));
        g2.drawImage(scaled, offsetX, offsetY, null);
        g2.dispose();

        BufferedImage deskewed = deskew(normalized);
        return toPng(deskewed);
    }

    private byte[] toPng(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private BufferedImage deskew(BufferedImage input) {
        int w = input.getWidth();
        int h = input.getHeight();
        double sum = 0;
        double meanX = 0;
        double meanY = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = input.getRaster().getSample(x, y, 0);
                double wgt = v / 255.0;
                sum += wgt;
                meanX += x * wgt;
                meanY += y * wgt;
            }
        }
        if (sum == 0) {
            return input;
        }
        meanX /= sum;
        meanY /= sum;

        double mu11 = 0;
        double mu02 = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = input.getRaster().getSample(x, y, 0);
                double wgt = v / 255.0;
                double dx = x - meanX;
                double dy = y - meanY;
                mu11 += dx * dy * wgt;
                mu02 += dy * dy * wgt;
            }
        }
        if (Math.abs(mu02) < 1e-6) {
            return input;
        }
        double skew = mu11 / mu02;

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        java.awt.geom.AffineTransform transform = new java.awt.geom.AffineTransform();
        transform.translate(-skew * meanY, 0);
        transform.shear(skew, 0);
        g.drawImage(input, transform, null);
        g.dispose();
        return out;
    }

}
