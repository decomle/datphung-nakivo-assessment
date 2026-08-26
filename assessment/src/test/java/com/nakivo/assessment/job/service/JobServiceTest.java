package com.nakivo.assessment.job.service;

import com.nakivo.assessment.common.dto.PageResponse;
import com.nakivo.assessment.job.dto.CreateJobRequest;
import com.nakivo.assessment.job.dto.JobResponse;
import com.nakivo.assessment.job.dto.ProcessJobsResponse;
import com.nakivo.assessment.job.entity.Job;
import com.nakivo.assessment.job.entity.JobStatus;
import com.nakivo.assessment.job.exception.JobNotFoundException;
import com.nakivo.assessment.job.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JobServiceTest {
    @Mock
    private JobRepository jobRepository;

    private ObjectMapper objectMapper;

    @Mock
    private JobClaimService jobClaimService;

    @Mock
    private JobProcessor jobProcessor;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        jobService = new JobService(
                jobRepository,
                objectMapper,
                jobClaimService,
                jobProcessor
        );

        ReflectionTestUtils.setField(jobService, "jobBatchSize", 100);
    }

    @Test
    void createJob_shouldCreatePendingJob() {
        UUID id = UUID.randomUUID();

        CreateJobRequest request = new CreateJobRequest(
                "EMAIL",
                objectMapper.readTree("""
                {
                    "recipient": "test@test.com"
                }
                """)
        );

        Job savedJob = Job.builder()
                .id(id)
                .type("EMAIL")
                .payload("""
                {"recipient":"test@test.com"}
                """)
                .status(JobStatus.PENDING)
                .retryCount(0)
                .build();

        when(jobRepository.save(any(Job.class)))
                .thenReturn(savedJob);

        UUID result = jobService.createJob(request);

        assertEquals(id, result);

        verify(jobRepository).save(argThat(job ->
                job.getType().equals("EMAIL")
                        && job.getStatus() == JobStatus.PENDING
                        && job.getRetryCount() == 0
                        && job.getPayload().contains("test@test.com")
        ));
    }

    @Test
    void getJob_shouldReturnJobResponse() {
        UUID id = UUID.randomUUID();

        Job job = Job.builder()
                .id(id)
                .type("EMAIL")
                .payload("""
                {"recipient":"test@test.com"}
                """)
                .status(JobStatus.PENDING)
                .retryCount(0)
                .build();

        when(jobRepository.findById(id))
                .thenReturn(Optional.of(job));

        JobResponse result = jobService.getJob(id);

        assertEquals(id, result.id());
        assertEquals("EMAIL", result.type());
        assertEquals(JobStatus.PENDING, result.status());
        assertEquals(0, result.retryCount());

        verify(jobRepository).findById(id);
    }

    @Test
    void getJob_whenNotFound_shouldThrowException() {
        UUID id = UUID.randomUUID();

        when(jobRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                JobNotFoundException.class,
                () -> jobService.getJob(id)
        );

        verify(jobRepository).findById(id);
    }

    @Test
    void getJobs_shouldReturnPagedJobs() {
        UUID id = UUID.randomUUID();

        Job job = Job.builder()
                .id(id)
                .type("EMAIL")
                .payload("""
                {"recipient":"test@test.com"}
                """)
                .status(JobStatus.PENDING)
                .retryCount(0)
                .build();

        Page<Job> page = new PageImpl<>(
                List.of(job),
                PageRequest.of(0, 20),
                1
        );

        when(jobRepository.findByStatus(
                eq(JobStatus.PENDING),
                any(Pageable.class)
        )).thenReturn(page);

        PageResponse<JobResponse> result =
                jobService.getJobs(JobStatus.PENDING, 0, 20);

        assertEquals(1, result.content().size());
        assertEquals(id, result.content().get(0).id());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1, result.totalElements());
        assertEquals(1, result.totalPages());

        verify(jobRepository).findByStatus(
                eq(JobStatus.PENDING),
                any(Pageable.class)
        );
    }

    @Test
    void getJobs_withoutStatus_shouldFindAllJobs() {
        Page<Job> page = new PageImpl<>(
                List.of(),
                PageRequest.of(0, 20),
                0
        );

        when(jobRepository.findAll(any(Pageable.class)))
                .thenReturn(page);

        PageResponse<JobResponse> result =
                jobService.getJobs(null, 0, 20);

        assertTrue(result.content().isEmpty());
        assertEquals(0, result.totalElements());

        verify(jobRepository).findAll(any(Pageable.class));
        verify(jobRepository, never())
                .findByStatus(any(), any());
    }

    @Test
    void processPendingJobs_shouldReturnCompletedCount() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        when(jobClaimService.claimPendingJobIds(100))
                .thenReturn(List.of(id1, id2, id3));

        when(jobProcessor.process(id1))
                .thenReturn(ProcessingResult.COMPLETED);

        when(jobProcessor.process(id2))
                .thenReturn(ProcessingResult.COMPLETED);

        when(jobProcessor.process(id3))
                .thenReturn(ProcessingResult.COMPLETED);

        ProcessJobsResponse result =
                jobService.processPendingJobs();

        assertEquals(3, result.completed());
        assertEquals(0, result.failed());
        assertEquals(0, result.retries());
        assertEquals(3, result.total());

        verify(jobClaimService).claimPendingJobIds(100);

        verify(jobProcessor).process(id1);
        verify(jobProcessor).process(id2);
        verify(jobProcessor).process(id3);
    }

    @Test
    void processPendingJobs_shouldReturnProcessingSummary() {
        UUID completedId = UUID.randomUUID();
        UUID retryId = UUID.randomUUID();
        UUID failedId = UUID.randomUUID();

        when(jobClaimService.claimPendingJobIds(100))
                .thenReturn(List.of(
                        completedId,
                        retryId,
                        failedId
                ));

        when(jobProcessor.process(completedId))
                .thenReturn(ProcessingResult.COMPLETED);

        when(jobProcessor.process(retryId))
                .thenReturn(ProcessingResult.RETRYING);

        when(jobProcessor.process(failedId))
                .thenReturn(ProcessingResult.FAILED);

        ProcessJobsResponse result =
                jobService.processPendingJobs();

        assertEquals(1, result.completed());
        assertEquals(1, result.retries());
        assertEquals(1, result.failed());
        assertEquals(3, result.total());
    }

    @Test
    void processPendingJobs_whenNoJobs_shouldReturnZeroCounts() {
        when(jobClaimService.claimPendingJobIds(100))
                .thenReturn(List.of());

        ProcessJobsResponse result =
                jobService.processPendingJobs();

        assertEquals(0, result.completed());
        assertEquals(0, result.retries());
        assertEquals(0, result.failed());
        assertEquals(0, result.total());

        verify(jobProcessor, never()).process(any());
    }
}
