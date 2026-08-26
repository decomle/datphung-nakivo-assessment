package com.nakivo.assessment.job.service;

import com.nakivo.assessment.job.entity.Job;
import com.nakivo.assessment.job.entity.JobStatus;
import com.nakivo.assessment.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobProcessor {
    private final ObjectMapper objectMapper;
    private final JobRepository jobRepository;

    @Value("${job.max-retries}")
    private int maxRetries;

    @Transactional
    public ProcessingResult process(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElse(null);
        if(job == null) {
            return ProcessingResult.FAILED;
        }
        if (shouldFail(job)) {
            return handleFailure(job);
        }
        complete(job);
        return ProcessingResult.COMPLETED;

    }

    private boolean shouldFail(Job job) {
        return job.getPayload() != null
                && objectMapper.readTree(job.getPayload())
                .path("failed")
                .asBoolean(false);

    }

    private ProcessingResult handleFailure(Job job) {
        int retryCount = job.getRetryCount() + 1;

        job.setRetryCount(retryCount);
        job.setErrorMessage(
                "Processing failed. Attempt " + retryCount + " of 3."
        );

        if (retryCount >= maxRetries) {
            job.setStatus(JobStatus.FAILED);
            return ProcessingResult.FAILED;
        }

        job.setStatus(JobStatus.PENDING);
        return ProcessingResult.RETRYING;
    }

    private void complete(Job job) {
        job.setStatus(JobStatus.COMPLETED);
    }
}
