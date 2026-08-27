package com.nakivo.assessment.job.controller;

import com.nakivo.assessment.common.dto.PageResponse;
import com.nakivo.assessment.job.dto.CreateJobRequest;
import com.nakivo.assessment.job.dto.CreateJobResponse;
import com.nakivo.assessment.job.dto.JobResponse;
import com.nakivo.assessment.job.dto.ProcessJobsResponse;
import com.nakivo.assessment.job.entity.JobStatus;
import com.nakivo.assessment.job.service.JobService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Validated
public class JobController {
    private final JobService jobService;

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable UUID id) {
        return jobService.getJob(id);
    }

    @GetMapping
    public PageResponse<JobResponse> getJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return jobService.getJobs(status, page, size);
    }

    @PostMapping
    public ResponseEntity<CreateJobResponse> createJob(@Valid @RequestBody CreateJobRequest request) {
        UUID jobId = jobService.createJob(request);

        URI location = URI.create("/api/jobs/" + jobId);

        return ResponseEntity.created(location).body(new CreateJobResponse(jobId));
    }

    @PostMapping("/process")
    public ResponseEntity<ProcessJobsResponse> processJobs() {
        return ResponseEntity.ok(jobService.processPendingJobs());
    }
}
