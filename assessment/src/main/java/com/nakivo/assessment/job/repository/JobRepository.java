package com.nakivo.assessment.job.repository;

import com.nakivo.assessment.job.entity.Job;
import com.nakivo.assessment.job.entity.JobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    @Query(value = """
        SELECT *
        FROM jobs
        WHERE status = :status
        ORDER BY created_at ASC
        LIMIT :limit
        FOR UPDATE
        """, nativeQuery = true)
    List<Job> findPendingJobsForUpdate( @Param("status") String status, @Param("limit") int limit);
}
