package com.nakivo.assessment.job.service;

import com.nakivo.assessment.job.entity.Job;
import com.nakivo.assessment.job.entity.JobStatus;
import com.nakivo.assessment.job.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobProcessorTest {

    @Mock
    private JobRepository jobRepository;

    private ObjectMapper objectMapper;

    @InjectMocks
    private JobProcessor jobProcessor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        jobProcessor = new JobProcessor(objectMapper, jobRepository);

        ReflectionTestUtils.setField(jobProcessor, "maxRetries", 3);
    }

    @Test
    void process_whenJobNotFound_shouldReturnFailed() {
        UUID id = UUID.randomUUID();

        when(jobRepository.findById(id))
                .thenReturn(Optional.empty());

        ProcessingResult result = jobProcessor.process(id);

        assertEquals(ProcessingResult.FAILED, result);

        verify(jobRepository).findById(id);
    }

    @Test
    void process_whenJobSucceeds_shouldMarkCompleted() {
        UUID id = UUID.randomUUID();

        Job job = Job.builder()
                .id(id)
                .payload("""
                        {"recipient":"test@test.com"}
                        """)
                .status(JobStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(jobRepository.findById(id))
                .thenReturn(Optional.of(job));

        ProcessingResult result = jobProcessor.process(id);

        assertEquals(ProcessingResult.COMPLETED, result);
        assertEquals(JobStatus.COMPLETED, job.getStatus());

        verify(jobRepository).findById(id);
    }

    @Test
    void process_whenProcessingFailsFirstTime_shouldRetry() {
        UUID id = UUID.randomUUID();

        Job job = Job.builder()
                .id(id)
                .payload("""
                        {"failed":true}
                        """)
                .status(JobStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(jobRepository.findById(id))
                .thenReturn(Optional.of(job));

        ProcessingResult result = jobProcessor.process(id);

        assertEquals(ProcessingResult.RETRYING, result);
        assertEquals(JobStatus.PENDING, job.getStatus());
        assertEquals(1, job.getRetryCount());
        assertEquals(
                "Processing failed. Attempt 1 of 3.",
                job.getErrorMessage()
        );
    }

    @Test
    void process_whenProcessingFailsSecondTime_shouldRetry() {
        UUID id = UUID.randomUUID();

        Job job = Job.builder()
                .id(id)
                .payload("""
                        {"failed":true}
                        """)
                .status(JobStatus.PROCESSING)
                .retryCount(1)
                .build();

        when(jobRepository.findById(id))
                .thenReturn(Optional.of(job));

        ProcessingResult result = jobProcessor.process(id);

        assertEquals(ProcessingResult.RETRYING, result);
        assertEquals(JobStatus.PENDING, job.getStatus());
        assertEquals(2, job.getRetryCount());
        assertEquals(
                "Processing failed. Attempt 2 of 3.",
                job.getErrorMessage()
        );
    }

    @Test
    void process_whenProcessingFailsThirdTime_shouldMarkFailed() {
        UUID id = UUID.randomUUID();

        Job job = Job.builder()
                .id(id)
                .payload("""
                        {"failed":true}
                        """)
                .status(JobStatus.PROCESSING)
                .retryCount(2)
                .build();

        when(jobRepository.findById(id))
                .thenReturn(Optional.of(job));

        ProcessingResult result = jobProcessor.process(id);

        assertEquals(ProcessingResult.FAILED, result);
        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals(3, job.getRetryCount());
        assertEquals(
                "Processing failed. Attempt 3 of 3.",
                job.getErrorMessage()
        );
    }

    @Test
    void process_whenPayloadDoesNotContainFailed_shouldComplete() {
        UUID id = UUID.randomUUID();

        Job job = Job.builder()
                .id(id)
                .payload("""
                        {"recipient":"test@test.com"}
                        """)
                .status(JobStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(jobRepository.findById(id))
                .thenReturn(Optional.of(job));

        ProcessingResult result = jobProcessor.process(id);

        assertEquals(ProcessingResult.COMPLETED, result);
        assertEquals(JobStatus.COMPLETED, job.getStatus());
    }

    @Test
    void process_whenPayloadIsNull_shouldComplete() {
        UUID id = UUID.randomUUID();

        Job job = Job.builder()
                .id(id)
                .payload(null)
                .status(JobStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(jobRepository.findById(id))
                .thenReturn(Optional.of(job));

        ProcessingResult result = jobProcessor.process(id);

        assertEquals(ProcessingResult.COMPLETED, result);
        assertEquals(JobStatus.COMPLETED, job.getStatus());
    }
}