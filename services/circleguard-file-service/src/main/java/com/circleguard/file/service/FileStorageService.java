package com.circleguard.file.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path root;

    public FileStorageService(@Value("${FILE_UPLOAD_DIR:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage", e);
        }
    }

    public String saveFile(MultipartFile file) {
        String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        try {
            Files.copy(file.getInputStream(), this.root.resolve(filename));
            return filename;
        } catch (Exception e) {
            throw new RuntimeException("Could not store file", e);
        }
    }

    public Resource loadFile(String filename) {
        return null;
    }
}
interface Resource {}
