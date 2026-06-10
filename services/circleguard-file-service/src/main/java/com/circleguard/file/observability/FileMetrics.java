package com.circleguard.file.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class FileMetrics {

    private final Counter uploadsTotal;
    private final DistributionSummary uploadSizeBytes;

    public FileMetrics(MeterRegistry registry) {
        this.uploadsTotal = Counter.builder("file.uploads.total")
            .description("Total file uploads").register(registry);
        this.uploadSizeBytes = DistributionSummary.builder("file.uploads.size.bytes")
            .description("Distribution of file upload sizes")
            .baseUnit("bytes")
            .register(registry);
    }

    public void recordUpload() { uploadsTotal.increment(); }
    public void recordUploadSize(long bytes) { uploadSizeBytes.record(bytes); }
}
