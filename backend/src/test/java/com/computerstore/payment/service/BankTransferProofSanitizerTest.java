package com.computerstore.payment.service;

import static org.junit.jupiter.api.Assertions.*;

import com.computerstore.common.exception.InvalidRequestException;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

class BankTransferProofSanitizerTest {
    private final BankTransferProofSanitizer sanitizer = new BankTransferProofSanitizer();

    @Test
    void reencodesImagesAndProducesAPrivatePngPreview() throws Exception {
        BufferedImage image = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", bytes);

        var result = sanitizer.sanitize(new MockMultipartFile(
                "file", "../receipt.jpg", "image/jpeg", bytes.toByteArray()));

        assertEquals("receipt.jpg", result.originalFilename());
        assertEquals("image/png", result.contentType());
        assertEquals(1, result.previews().size());
        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'},
                java.util.Arrays.copyOf(result.raw(), 4));
    }

    @Test
    void rejectsPdfActiveContent() throws Exception {
        byte[] bytes;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            COSDictionary javascript = new COSDictionary();
            javascript.setString(COSName.JS, "app.alert('x')");
            document.getDocumentCatalog().getCOSObject().setItem(COSName.OPEN_ACTION, javascript);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            bytes = output.toByteArray();
        }

        assertThrows(InvalidRequestException.class, () -> sanitizer.sanitize(
                new MockMultipartFile("file", "receipt.pdf", "application/pdf", bytes)));
    }

    @Test
    void rejectsFilesOverFiveMibibytes() {
        byte[] bytes = new byte[(int) BankTransferProofSanitizer.MAX_SIZE + 1];
        assertThrows(InvalidRequestException.class, () -> sanitizer.sanitize(
                new MockMultipartFile("file", "too-large.png", "image/png", bytes)));
    }
}
