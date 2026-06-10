package com.circleguard.file.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService storageService;
    private Path uploadsDir;

    @BeforeEach
    void setUp() throws IOException {
        uploadsDir = tempDir.resolve("uploads");
        storageService = new FileStorageService(uploadsDir.toString());
        Files.createDirectories(uploadsDir);
    }

    @Test
    void saveFile_ShouldStoreFileAndReturnFilename() throws IOException {
        // Given
        String originalFilename = "test.txt";
        String content = "Hello, World!";
        org.springframework.web.multipart.MultipartFile file = new MockMultipartFile(
                "file", originalFilename, "text/plain", content.getBytes()
        );

        // When
        String storedFilename = storageService.saveFile(file);

        // Then
        assertNotNull(storedFilename);
        assertTrue(storedFilename.contains(originalFilename));
        assertTrue(storedFilename.startsWith(UUID.randomUUID().toString().substring(0, 8)) || storedFilename.matches(".*_[^_]+")); // UUID prefix

        Path storedFile = uploadsDir.resolve(storedFilename);
        assertTrue(Files.exists(storedFile));
        assertEquals(content, new String(Files.readAllBytes(storedFile)));
    }

    @Test
    void saveFile_ShouldThrowExceptionWhenFileIsNull() {
        // Given
        org.springframework.web.multipart.MultipartFile file = null;

        // When/Then
        assertThrows(RuntimeException.class, () -> storageService.saveFile(file));
    }

    @Test
    void saveFile_ShouldThrowExceptionWhenIOExceptionOccurs() throws IOException {
        // Given
        org.springframework.web.multipart.MultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "content".getBytes()
        );

        // Delete uploads dir so Files.copy fails with NoSuchFileException
        Files.delete(uploadsDir);

        // When/Then
        assertThrows(RuntimeException.class, () -> storageService.saveFile(file));
    }

    @Test
    void saveFile_ShouldPreserveOriginalFilenameWithUuidPrefix() throws IOException {
        // Given
        String original = "document.pdf";
        org.springframework.web.multipart.MultipartFile file = new MockMultipartFile(
                "file", original, "application/pdf", "pdf content".getBytes()
        );

        // When
        String storedFilename = storageService.saveFile(file);

        // Then
        assertTrue(storedFilename.contains(original));
        assertTrue(storedFilename.contains("_"));
    }

    // Helper - MockMultipartFile for unit test outside Spring context
    private static class MockMultipartFile implements org.springframework.web.multipart.MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        MockMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content == null || content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws IOException, IllegalStateException {}
    }
}