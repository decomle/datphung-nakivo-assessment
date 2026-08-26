package com.nakivo.assessment.job.service;

import com.nakivo.assessment.job.entity.Job;
import com.nakivo.assessment.job.entity.JobStatus;
import com.nakivo.assessment.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobClaimService {
    private final JobRepository jobRepository;

    @Transactional
    public List<UUID> claimPendingJobIds(int limit) {
        List<Job> jobs = jobRepository.findPendingJobsForUpdate(JobStatus.PENDING.name(), limit);

        jobs.forEach(job -> job.setStatus(JobStatus.PROCESSING));

        return jobs.stream()
                .map(Job::getId)
                .toList();
    }
}
