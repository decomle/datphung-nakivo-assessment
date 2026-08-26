package com.nakivo.assessment.job.dto;

import com.nakivo.assessment.job.entity.JobStatus;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String type,
        JsonNode payload,
        JobStatus status,
        Instant createdAt,
        Instant updatedAt,
        int retryCount,
        String errorMessage
) {
}
