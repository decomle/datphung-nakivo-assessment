package com.nakivo.assessment.job.service;

import com.nakivo.assessment.job.entity.Job;
import com.nakivo.assessment.job.entity.JobStatus;
import com.nakivo.assessment.job.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class JobProcessorIntegrationTest {
    @Autowired
    private JobProcessor jobProcessor;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
    }

    @Test
    void process_shouldCompleteSuccessfulJob() {
        Job job = createJob("""
                {"recipient":"test@test.com"}
                """);

        ProcessingResult result = jobProcessor.process(job.getId());

        assertEquals(ProcessingResult.COMPLETED, result);

        Job updated = jobRepository.findById(job.getId()).orElseThrow();

        assertEquals(JobStatus.COMPLETED, updated.getStatus());
        assertEquals(0, updated.getRetryCount());
        assertNull(updated.getErrorMessage());
    }

    @Test
    void process_shouldRetryFailedJob() {
        Job job = createJob("""
                {"fail":true}
                """);

        ProcessingResult result = jobProcessor.process(job.getId());

        assertEquals(ProcessingResult.RETRYING, result);

        Job updated = jobRepository.findById(job.getId()).orElseThrow();

        assertEquals(JobStatus.PENDING, updated.getStatus());
        assertEquals(1, updated.getRetryCount());
        assertEquals(
                "Processing failed. Attempt 1 of 3",
                updated.getErrorMessage()
        );
    }

    @Test
    void process_shouldFailJobAfterMaxRetries() {
        Job job = createJob("""
                {"fail":true}
                """);

        job.setRetryCount(2);
        jobRepository.save(job);

        ProcessingResult result = jobProcessor.process(job.getId());

        assertEquals(ProcessingResult.FAILED, result);

        Job updated = jobRepository.findById(job.getId()).orElseThrow();

        assertEquals(JobStatus.FAILED, updated.getStatus());
        assertEquals(3, updated.getRetryCount());
        assertEquals(
                "Processing failed. Attempt 3 of 3",
                updated.getErrorMessage()
        );
    }

    @Test
    void process_shouldReturnFailedWhenJobDoesNotExist() {
        UUID id = UUID.randomUUID();

        ProcessingResult result = jobProcessor.process(id);

        assertEquals(ProcessingResult.FAILED, result);

        assertTrue(jobRepository.findById(id).isEmpty());
    }

    private Job createJob(String payload) {
        Job job = Job.builder()
                .type("EMAIL")
                .payload(payload)
                .status(JobStatus.PENDING)
                .retryCount(0)
                .build();

        return jobRepository.save(job);
    }
}
