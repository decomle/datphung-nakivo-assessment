package com.nakivo.assessment.job.dto;

public record ProcessJobsResponse(int completed, int failed, int retries, int total) {
}
