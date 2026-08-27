package com.nakivo.assessment.job.controller;


import com.nakivo.assessment.job.entity.Job;
import com.nakivo.assessment.job.entity.JobStatus;
import com.nakivo.assessment.job.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
    }

    @Test
    void createJob_shouldPersistJob() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "type": "EMAIL",
                                    "payload": {
                                        "recipient": "test@test.com"
                                    }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        List<Job> jobs = jobRepository.findAll();

        assertEquals(1, jobs.size());

        Job job = jobs.get(0);

        assertNotNull(job.getId());
        assertEquals("EMAIL", job.getType());
        assertEquals(JobStatus.PENDING, job.getStatus());
        assertEquals(0, job.getRetryCount());
        assertTrue(job.getPayload().contains("test@test.com"));
    }

    @Test
    void getJob_shouldReturnPersistedJob() throws Exception {
        Job job = createJob(
                "EMAIL",
                """
                {"recipient":"test@test.com"}
                """,
                JobStatus.PENDING
        );

        mockMvc.perform(get("/api/jobs/{id}", job.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(job.getId().toString()))
                .andExpect(jsonPath("$.type").value("EMAIL"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.retryCount").value(0))
                .andExpect(jsonPath("$.payload.recipient")
                        .value("test@test.com"));
    }

    @Test
    void getJob_shouldReturn404WhenJobDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/jobs/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void getJobs_shouldReturnPaginatedJobs() throws Exception {
        createJob("EMAIL", "{}", JobStatus.PENDING);
        createJob("EMAIL", "{}", JobStatus.PENDING);
        createJob("SMS", "{}", JobStatus.COMPLETED);

        mockMvc.perform(get("/api/jobs")
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].status")
                        .value("PENDING"))
                .andExpect(jsonPath("$.content[1].status")
                        .value("PENDING"));
    }

    @Test
    void getJobs_shouldReturnAllJobsWhenStatusIsNotProvided()
            throws Exception {

        createJob("EMAIL", "{}", JobStatus.PENDING);
        createJob("SMS", "{}", JobStatus.COMPLETED);

        mockMvc.perform(get("/api/jobs")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getJobs_shouldRejectInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/jobs").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void processJobs_shouldProcessPendingJobs() throws Exception {
        createJob(
                "EMAIL",
                """
                {"recipient":"success@test.com"}
                """,
                JobStatus.PENDING
        );

        createJob(
                "EMAIL",
                """
                {"fail":true}
                """,
                JobStatus.PENDING
        );

        mockMvc.perform(post("/api/jobs/process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.retries").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        List<Job> jobs = jobRepository.findAll();

        assertEquals(2, jobs.size());

        Job completedJob = jobs.stream()
                .filter(job -> !job.getPayload().contains("\"fail\""))
                .findFirst()
                .orElseThrow();

        Job retryingJob = jobs.stream()
                .filter(job -> job.getPayload().contains("\"fail\""))
                .findFirst()
                .orElseThrow();

        assertEquals(JobStatus.COMPLETED, completedJob.getStatus());
        assertEquals(JobStatus.PENDING, retryingJob.getStatus());
        assertEquals(1, retryingJob.getRetryCount());
    }

    @Test
    void processJobs_calledConcurrently_shouldProcessJobOnlyOnce() throws Exception {
        Job job = createJob(
                "EMAIL",
                """
                {"recipient":"success@test.com"}
                """,
                JobStatus.PENDING
        );

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> firstResponse = submitProcessRequest(executor, start);
            Future<String> secondResponse = submitProcessRequest(executor, start);

            start.countDown();

            int firstTotal = objectMapper.readTree(
                    firstResponse.get(5, TimeUnit.SECONDS)
            ).path("total").asInt();

            int secondTotal = objectMapper.readTree(
                    secondResponse.get(5, TimeUnit.SECONDS)
            ).path("total").asInt();

            assertEquals(1, firstTotal + secondTotal);
            assertEquals(
                    JobStatus.COMPLETED,
                    jobRepository.findById(job.getId()).orElseThrow().getStatus()
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private Future<String> submitProcessRequest(ExecutorService executor, CountDownLatch start) {
        return executor.submit(() -> {
            start.await();
            return mockMvc.perform(post("/api/jobs/process"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
        });
    }

    private Job createJob(String type, String payload, JobStatus status) {
        Job job = Job.builder()
                .type(type)
                .payload(payload)
                .status(status)
                .retryCount(0)
                .build();

        return jobRepository.save(job);
    }
}
