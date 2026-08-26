package com.nakivo.assessment.job.service;

import com.nakivo.assessment.job.entity.Job;
import com.nakivo.assessment.job.entity.JobStatus;
import com.nakivo.assessment.job.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobClaimServiceTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobClaimService jobClaimService;

    @Test
    void claimPendingJobIds_shouldClaimJobsAndReturnIds() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Job job1 = Job.builder()
                .id(id1)
                .status(JobStatus.PENDING)
                .build();

        Job job2 = Job.builder()
                .id(id2)
                .status(JobStatus.PENDING)
                .build();

        when(jobRepository.findPendingJobsForUpdate(
                JobStatus.PENDING.name(),
                10
        )).thenReturn(List.of(job1, job2));

        List<UUID> result = jobClaimService.claimPendingJobIds(10);

        assertEquals(List.of(id1, id2), result);

        assertEquals(JobStatus.PROCESSING, job1.getStatus());
        assertEquals(JobStatus.PROCESSING, job2.getStatus());

        verify(jobRepository).findPendingJobsForUpdate(
                JobStatus.PENDING.name(),
                10
        );

        verifyNoMoreInteractions(jobRepository);
    }

    @Test
    void claimPendingJobIds_whenNoPendingJobs_shouldReturnEmptyList() {
        when(jobRepository.findPendingJobsForUpdate(
                JobStatus.PENDING.name(),
                10
        )).thenReturn(List.of());

        List<UUID> result = jobClaimService.claimPendingJobIds(10);

        assertTrue(result.isEmpty());

        verify(jobRepository).findPendingJobsForUpdate(
                JobStatus.PENDING.name(),
                10
        );

        verifyNoMoreInteractions(jobRepository);
    }

    @Test
    void claimPendingJobIds_shouldPassLimitToRepository() {
        int limit = 5;

        when(jobRepository.findPendingJobsForUpdate(
                JobStatus.PENDING.name(),
                limit
        )).thenReturn(List.of());

        jobClaimService.claimPendingJobIds(limit);

        verify(jobRepository).findPendingJobsForUpdate(
                JobStatus.PENDING.name(),
                limit
        );
    }
}