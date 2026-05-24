package com.circleguard.file.controller;

import com.circleguard.file.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileUploadController.class)
class FileUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileStorageService storageService;

    @Test
    void shouldUploadFileSuccessfully() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "certificate.pdf", "application/pdf", "mock content".getBytes());

        Mockito.when(storageService.saveFile(Mockito.any())).thenReturn("certificate.pdf");

        mockMvc.perform(multipart("/api/v1/files/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("certificate.pdf"));
    }

    @Test
    void shouldReturnBadRequestWhenFileIsEmpty() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/v1/files/upload").file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes());

        doThrow(new RuntimeException("Could not store file"))
                .when(storageService).saveFile(Mockito.any());

        mockMvc.perform(multipart("/api/v1/files/upload").file(file))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldReturnBadRequestWhenNoFilePart() throws Exception {
        mockMvc.perform(multipart("/api/v1/files/upload"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldHandleVariousFileTypes() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "image data".getBytes());
        MockMultipartFile docFile = new MockMultipartFile(
                "file", "report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "doc data".getBytes());

        Mockito.when(storageService.saveFile(Mockito.any())).thenReturn("uploaded_file");

        mockMvc.perform(multipart("/api/v1/files/upload").file(imageFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").exists());

        mockMvc.perform(multipart("/api/v1/files/upload").file(docFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").exists());
    }
}
