package com.nakivo.assessment.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record CreateJobRequest(
        @NotBlank
        @Size(max = 100)
        String type,
        JsonNode payload
) {
}
