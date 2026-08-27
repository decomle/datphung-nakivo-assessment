package com.nakivo.assessment.job.service;

import com.nakivo.assessment.common.dto.PageResponse;
import com.nakivo.assessment.job.dto.CreateJobRequest;
import com.nakivo.assessment.job.dto.JobResponse;
import com.nakivo.assessment.job.dto.ProcessJobsResponse;
import com.nakivo.assessment.job.entity.Job;
import com.nakivo.assessment.job.entity.JobStatus;
import com.nakivo.assessment.job.exception.JobNotFoundException;
import com.nakivo.assessment.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobService {
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final JobClaimService jobClaimService;
    private final JobProcessor jobProcessor;

    @Value("${job.batch-size}")
    private int jobBatchSize;

    public UUID createJob(CreateJobRequest request) {
        Job job = Job.builder()
                .type(request.type())
                .payload(objectMapper.writeValueAsString(request.payload()))
                .status(JobStatus.PENDING)
                .retryCount(0)
            .build();

        job = jobRepository.save(job);
        return job.getId();
    }

    public PageResponse<JobResponse> getJobs(JobStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Job> jobs = status != null ? jobRepository.findByStatus(status, pageable) : jobRepository.findAll(pageable);

        Page<JobResponse> responses = jobs.map(this::toResponse);
        return new PageResponse<>(
                responses.getContent(),
                responses.getNumber(),
                responses.getSize(),
                responses.getTotalElements(),
                responses.getTotalPages()
        );
    }

    public JobResponse getJob(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));

        return toResponse(job);
    }

    public ProcessJobsResponse processPendingJobs() {
        int completed = 0, failed = 0, retries = 0;
        List<UUID> jobIds = jobClaimService.claimPendingJobIds(jobBatchSize);

        for (UUID jobId : jobIds) {
            ProcessingResult result = jobProcessor.process(jobId);

            if(result == ProcessingResult.COMPLETED) {
                completed++;
            } else if (result == ProcessingResult.RETRYING) {
                retries++;
            } else {
                failed++;
            }
        }

        return new ProcessJobsResponse(completed, failed, retries, jobIds.size());
    }

    private JobResponse toResponse(Job job) {
        return new JobResponse(
                job.getId(),
                job.getType(),
                objectMapper.readTree(job.getPayload()),
                job.getStatus(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getRetryCount(),
                job.getErrorMessage()
        );
    }
}
