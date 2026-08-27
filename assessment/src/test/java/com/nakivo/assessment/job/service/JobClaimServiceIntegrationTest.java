package com.nakivo.assessment.job.service;

import com.nakivo.assessment.job.entity.Job;
import com.nakivo.assessment.job.entity.JobStatus;
import com.nakivo.assessment.job.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class JobClaimServiceIntegrationTest {

    @Autowired
    private JobClaimService jobClaimService;

    @Autowired
    private JobRepository jobRepository;

    @Test
    void claimPendingJobIds_shouldClaimPendingJobs() {
        // Arrange
        Job job1 = createJob(JobStatus.PENDING);
        Job job2 = createJob(JobStatus.PENDING);

        jobRepository.saveAll(List.of(job1, job2));
        jobRepository.flush();

        // Act
        List<UUID> claimedIds = jobClaimService.claimPendingJobIds(2);

        // Assert
        assertEquals(2, claimedIds.size());
        assertTrue(claimedIds.contains(job1.getId()));
        assertTrue(claimedIds.contains(job2.getId()));

        Job claimedJob1 = jobRepository.findById(job1.getId()).orElseThrow();
        Job claimedJob2 = jobRepository.findById(job2.getId()).orElseThrow();

        assertEquals(JobStatus.PROCESSING, claimedJob1.getStatus());
        assertEquals(JobStatus.PROCESSING, claimedJob2.getStatus());
    }

    @Test
    void claimPendingJobIds_shouldRespectLimit() {
        // Arrange
        Job job1 = createJob(JobStatus.PENDING);
        Job job2 = createJob(JobStatus.PENDING);
        Job job3 = createJob(JobStatus.PENDING);

        jobRepository.saveAll(List.of(job1, job2, job3));
        jobRepository.flush();

        // Act
        List<UUID> claimedIds = jobClaimService.claimPendingJobIds(2);

        // Assert
        assertEquals(2, claimedIds.size());

        long processingCount = jobRepository.findAll()
                .stream()
                .filter(job -> job.getStatus() == JobStatus.PROCESSING)
                .count();

        assertEquals(2, processingCount);

        long pendingCount = jobRepository.findAll()
                .stream()
                .filter(job -> job.getStatus() == JobStatus.PENDING)
                .count();

        assertEquals(1, pendingCount);
    }

    @Test
    void claimPendingJobIds_shouldIgnoreNonPendingJobs() {
        // Arrange
        Job pendingJob = createJob(JobStatus.PENDING);
        Job completedJob = createJob(JobStatus.COMPLETED);
        Job failedJob = createJob(JobStatus.FAILED);

        jobRepository.saveAll(
                List.of(pendingJob, completedJob, failedJob)
        );
        jobRepository.flush();

        // Act
        List<UUID> claimedIds = jobClaimService.claimPendingJobIds(10);

        // Assert
        assertEquals(1, claimedIds.size());
        assertEquals(pendingJob.getId(), claimedIds.get(0));

        assertEquals(
                JobStatus.PROCESSING,
                jobRepository.findById(pendingJob.getId())
                        .orElseThrow()
                        .getStatus()
        );

        assertEquals(
                JobStatus.COMPLETED,
                jobRepository.findById(completedJob.getId())
                        .orElseThrow()
                        .getStatus()
        );

        assertEquals(
                JobStatus.FAILED,
                jobRepository.findById(failedJob.getId())
                        .orElseThrow()
                        .getStatus()
        );
    }

    private Job createJob(JobStatus status) {
        return Job.builder()
                .type("EMAIL")
                .payload("""
                        {"recipient":"test@test.com"}
                        """)
                .status(status)
                .retryCount(0)
                .build();
    }
}
