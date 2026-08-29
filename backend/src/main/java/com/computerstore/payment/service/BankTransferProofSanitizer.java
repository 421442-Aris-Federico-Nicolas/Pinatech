package com.computerstore.payment.service;

import com.computerstore.common.exception.InvalidRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Semaphore;

@Component
public class BankTransferProofSanitizer {
    public static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final int MAX_PAGES = 10;
    private static final long MAX_PIXELS = 4_000_000L;
    private static final long MAX_SANITIZED_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PDF_PAGE_PIXELS = 4_000_000L;
    private static final long MAX_PDF_TOTAL_PIXELS = 16_000_000L;
    private static final long MAX_PDF_PREVIEW_BYTES = 20L * 1024 * 1024;
    private static final long MAX_PDF_DECODED_STREAM_BYTES = 64L * 1024 * 1024;
    private static final int MAX_DIMENSION = 6000;
    private static final Set<COSName> FORBIDDEN = Set.of(
            COSName.JAVA_SCRIPT, COSName.JS, COSName.EMBEDDED_FILES, COSName.OPEN_ACTION,
            COSName.AA, COSName.getPDFName("Launch"), COSName.getPDFName("RichMedia"), COSName.getPDFName("EF"),
            COSName.getPDFName("XFA"), COSName.getPDFName("EmbeddedFile"));
    private static final Set<COSName> ACTIVE_ACTIONS = Set.of(
            COSName.JAVA_SCRIPT, COSName.getPDFName("Launch"), COSName.getPDFName("URI"),
            COSName.getPDFName("GoToR"), COSName.getPDFName("GoToE"), COSName.getPDFName("SubmitForm"),
            COSName.getPDFName("ImportData"), COSName.getPDFName("Rendition"), COSName.getPDFName("Sound"),
            COSName.getPDFName("Movie"));
    private final Semaphore pdfRenderSlots = new Semaphore(2, true);

    public SanitizedProof sanitize(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new InvalidRequestException("A proof file is required.");
        if (file.getSize() > MAX_SIZE) throw new InvalidRequestException("Proof files must not exceed 5 MiB.");
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0 || bytes.length > MAX_SIZE) {
                throw new InvalidRequestException("Proof files must contain data and not exceed 5 MiB.");
            }
            String filename = filename(file.getOriginalFilename());
            if (isPdf(bytes)) {
                if (!pdfRenderSlots.tryAcquire()) {
                    throw new InvalidRequestException("Proof processing is busy. Try again shortly.");
                }
                try {
                    return pdf(bytes, filename, bytes.length);
                } finally {
                    pdfRenderSlots.release();
                }
            }
            return image(bytes, filename, bytes.length);
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new InvalidRequestException("The proof file is invalid or cannot be safely processed.");
        }
    }

    private SanitizedProof image(byte[] bytes, String filename, long uploadedSize) throws IOException {
        BufferedImage decoded;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new InvalidRequestException("Only JPEG, PNG and PDF proofs are allowed.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new InvalidRequestException("Only JPEG, PNG and PDF proofs are allowed.");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toUpperCase(Locale.ROOT);
                if (!format.equals("JPEG") && !format.equals("JPG") && !format.equals("PNG")) {
                    throw new InvalidRequestException("Only JPEG, PNG and PDF proofs are allowed.");
                }
                reader.setInput(input, true, true);
                validateDimensions(reader.getWidth(0), reader.getHeight(0));
                decoded = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        if (decoded == null) throw new InvalidRequestException("The image proof could not be decoded.");
        byte[] sanitized = png(decoded);
        if (sanitized.length > MAX_SANITIZED_IMAGE_BYTES) {
            throw new InvalidRequestException("The sanitized image exceeds the allowed storage limit.");
        }
        return new SanitizedProof(sanitized, filename, "image/png", uploadedSize,
                List.of(new Preview(sanitized, decoded.getWidth(), decoded.getHeight())));
    }

    private SanitizedProof pdf(byte[] bytes, String filename, long uploadedSize) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) throw new InvalidRequestException("Encrypted PDFs are not allowed.");
            if (document.getNumberOfPages() < 1 || document.getNumberOfPages() > MAX_PAGES) {
                throw new InvalidRequestException("PDF proofs must contain between 1 and 10 pages.");
            }
            rejectForbiddenContent(document.getDocument().getTrailer());
            PDFRenderer renderer = new PDFRenderer(document);
            List<Preview> previews = new ArrayList<>();
            Set<COSBase> checkedResources = Collections.newSetFromMap(new IdentityHashMap<>());
            int[] resourceCount = {0};
            long totalPixels = 0;
            long totalPreviewBytes = 0;
            for (int index = 0; index < document.getNumberOfPages(); index++) {
                var box = document.getPage(index).getCropBox();
                if (box.getWidth() <= 0 || box.getHeight() <= 0 || box.getWidth() > 2000 || box.getHeight() > 2000) {
                    throw new InvalidRequestException("PDF page dimensions exceed the allowed limit.");
                }
                long estimatedPixels = Math.round(box.getWidth() * 120 / 72.0)
                        * Math.round(box.getHeight() * 120 / 72.0);
                if (estimatedPixels > MAX_PDF_PAGE_PIXELS
                        || (totalPixels += estimatedPixels) > MAX_PDF_TOTAL_PIXELS) {
                    throw new InvalidRequestException("PDF rendering exceeds the allowed resource limit.");
                }
                validateResources(document.getPage(index).getResources(), checkedResources, resourceCount, 0);
                BufferedImage rendered = renderer.renderImageWithDPI(index, 120, ImageType.RGB);
                validateDimensions(rendered.getWidth(), rendered.getHeight());
                byte[] preview = png(rendered);
                totalPreviewBytes += preview.length;
                if (totalPreviewBytes > MAX_PDF_PREVIEW_BYTES) {
                    throw new InvalidRequestException("PDF previews exceed the allowed resource limit.");
                }
                previews.add(new Preview(preview, rendered.getWidth(), rendered.getHeight()));
                rendered.flush();
            }
            return new SanitizedProof(bytes, filename, "application/pdf", uploadedSize, previews);
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new InvalidRequestException("The PDF proof is invalid or cannot be safely processed.");
        }
    }

    private void validateResources(PDResources resources, Set<COSBase> visited, int[] count, int depth)
            throws IOException {
        if (depth > 20) throw new InvalidRequestException("PDF resource nesting exceeds the allowed limit.");
        if (resources == null || !visited.add(resources.getCOSObject())) return;
        for (COSName name : resources.getXObjectNames()) {
            if (++count[0] > 1000) throw new InvalidRequestException("PDF resources exceed the allowed limit.");
            PDXObject object = resources.getXObject(name);
            if (object instanceof PDImageXObject image) {
                validateDimensions(image.getWidth(), image.getHeight());
            } else if (object instanceof PDFormXObject form) {
                validateResources(form.getResources(), visited, count, depth + 1);
            }
        }
    }

    private void rejectForbiddenContent(COSBase root) throws IOException {
        Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<COSBase> pending = new ArrayDeque<>();
        pending.push(root);
        int objects = 0;
        long[] decodedBytes = {0};
        while (!pending.isEmpty()) {
            COSBase value = pending.pop();
            if (value instanceof COSObject object) value = object.getObject();
            if (value == null || !visited.add(value)) continue;
            if (++objects > 50_000) throw new InvalidRequestException("PDF structure exceeds the allowed limit.");
            if (value instanceof COSStream stream) validateDecodedStream(stream, decodedBytes);
            if (value instanceof COSDictionary dictionary) {
                if (ACTIVE_ACTIONS.contains(dictionary.getCOSName(COSName.S))) {
                    throw new InvalidRequestException("PDFs with active or embedded content are not allowed.");
                }
                for (COSName key : dictionary.keySet()) {
                    if (FORBIDDEN.contains(key)) {
                        throw new InvalidRequestException("PDFs with active or embedded content are not allowed.");
                    }
                    COSBase child = dictionary.getItem(key);
                    if (child != null) pending.push(child);
                }
            } else if (value instanceof COSArray array) {
                for (int index = 0; index < array.size(); index++) {
                    COSBase child = array.get(index);
                    if (child != null) pending.push(child);
                }
            }
        }
    }

    private void validateDecodedStream(COSStream stream, long[] total) throws IOException {
        try (InputStream input = stream.createInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total[0] += read;
                if (total[0] > MAX_PDF_DECODED_STREAM_BYTES) {
                    throw new InvalidRequestException("PDF decoded content exceeds the allowed resource limit.");
                }
            }
        }
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                || (long) width * height > MAX_PIXELS) {
            throw new InvalidRequestException("Proof image dimensions exceed the allowed limit.");
        }
    }

    private byte[] png(BufferedImage source) throws IOException {
        BufferedImage clean = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = clean.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, clean.getWidth(), clean.getHeight());
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(clean, "PNG", output)) throw new IOException("PNG encoder unavailable");
        clean.flush();
        return output.toByteArray();
    }

    private boolean isPdf(byte[] bytes) {
        return bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D'
                && bytes[3] == 'F' && bytes[4] == '-';
    }

    private String filename(String value) {
        String name = value == null ? "proof" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) name = "proof";
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    public record SanitizedProof(byte[] raw, String originalFilename, String contentType, long uploadedSize,
                                 List<Preview> previews) {}
    public record Preview(byte[] content, int width, int height) {}
}
