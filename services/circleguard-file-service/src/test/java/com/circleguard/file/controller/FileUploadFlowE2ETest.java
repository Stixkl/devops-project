package com.circleguard.file.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FileUploadFlowE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void uploadFile_ValidPdf_ReturnsFilename() {
        ResponseEntity<Map> response = uploadFile("test-cert.pdf", "application/pdf", "PDF content here".getBytes());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("filename");
        String filename = (String) response.getBody().get("filename");
        assertThat(filename).endsWith("test-cert.pdf");
    }

    @Test
    void uploadFile_ValidImage_ReturnsFilename() {
        ResponseEntity<Map> response = uploadFile("photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("filename");
    }

    @Test
    void uploadFile_ValidTextFile_ReturnsFilename() {
        ResponseEntity<Map> response = uploadFile("report.txt", "text/plain", "Health report data".getBytes());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("filename")).isNotNull();
    }

    @Test
    void uploadFile_EmptyFile_Returns400() {
        ResponseEntity<Map> response = uploadFile("empty.txt", "text/plain", new byte[0]);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("error");
    }

    @Test
    void uploadFile_NoFilePart_Returns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/files/upload",
                HttpMethod.POST,
                new HttpEntity<>(new LinkedMultiValueMap<>(), headers),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Map> uploadFile(String filename, String contentType, byte[] content) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));

        ByteArrayResource fileResource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(fileResource, fileHeaders));

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        return restTemplate.exchange(
                "/api/v1/files/upload",
                HttpMethod.POST,
                new HttpEntity<>(body, requestHeaders),
                Map.class
        );
    }
}
