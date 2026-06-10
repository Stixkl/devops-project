package com.circleguard.file.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMetricsTest {
    private MeterRegistry registry;
    private FileMetrics fileMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        fileMetrics = new FileMetrics(registry);
    }

    @Test void shouldIncrementUploads() {
        fileMetrics.recordUpload();
        assertEquals(1.0, registry.counter("file.uploads.total").count()); }

    @Test void shouldRecordUploadSize() {
        fileMetrics.recordUploadSize(1024);
        double totalAmount = registry.find("file.uploads.size.bytes").summary().totalAmount();
        assertTrue(totalAmount > 0);
    }

    @Test void shouldRecordMultiple() {
        fileMetrics.recordUpload();
        fileMetrics.recordUpload();
        fileMetrics.recordUploadSize(2048);
        fileMetrics.recordUploadSize(4096);
        assertEquals(2.0, registry.counter("file.uploads.total").count());
    }
}
