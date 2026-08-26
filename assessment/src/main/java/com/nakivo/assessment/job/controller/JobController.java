package com.nakivo.assessment.job.controller;

import com.nakivo.assessment.common.dto.PageResponse;
import com.nakivo.assessment.job.dto.CreateJobRequest;
import com.nakivo.assessment.job.dto.JobResponse;
import com.nakivo.assessment.job.dto.ProcessJobsResponse;
import com.nakivo.assessment.job.entity.JobStatus;
import com.nakivo.assessment.job.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {
    private final JobService jobService;

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable UUID id) {
        return jobService.getJob(id);
    }

    @GetMapping
    public PageResponse<JobResponse> getJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return jobService.getJobs(status, page, size);
    }

    @PostMapping
    public ResponseEntity<Void> createJob(@Valid @RequestBody CreateJobRequest request) {
        UUID jobId = jobService.createJob(request);

        URI location = URI.create("/api/jobs/" + jobId);

        return ResponseEntity.created(location).build();
    }

    @PostMapping("/process")
    public ResponseEntity<ProcessJobsResponse> processJobs() {
        return ResponseEntity.ok(jobService.processPendingJobs());
    }
}
