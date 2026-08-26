package com.nakivo.assessment.job.controller;

import com.nakivo.assessment.common.dto.PageResponse;
import com.nakivo.assessment.job.dto.JobResponse;
import com.nakivo.assessment.job.dto.ProcessJobsResponse;
import com.nakivo.assessment.job.entity.JobStatus;
import com.nakivo.assessment.job.exception.JobNotFoundException;
import com.nakivo.assessment.job.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobController.class)
public class JobControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobService jobService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getJob_shouldReturn200() throws Exception {
        UUID id = UUID.randomUUID();

        JobResponse response = new JobResponse(
                id,
                "EMAIL",
                objectMapper.readTree("{\"recipient\": \"test@test.com\"}"),
                JobStatus.PENDING,
                Instant.parse("2026-08-26T08:00:00Z"),
                Instant.parse("2026-08-26T08:00:00Z"),
                0,
                null
        );

        when(jobService.getJob(id)).thenReturn(response);

        mockMvc.perform(get("/api/jobs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.type").value("EMAIL"))
                .andExpect(jsonPath("$.status").value(JobStatus.PENDING.name()));

        verify(jobService).getJob(id);
    }

    @Test
    void getJob_whenNotFound_shouldReturn404() throws Exception {
        UUID id = UUID.randomUUID();

        when(jobService.getJob(id))
                .thenThrow(new JobNotFoundException(id));

        mockMvc.perform(get("/api/jobs/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void createJob_shouldReturn201() throws Exception {
        UUID id = UUID.randomUUID();

        when(jobService.createJob(any()))
                .thenReturn(id);

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "type": "EMAIL",
                                "payload": {
                                    "name": "test"
                                }
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(content().string(""))
                .andExpect(header().string("Location", "/api/jobs/" + id));

        verify(jobService).createJob(any());
    }

    @Test
    void getJobs_shouldReturn200() throws Exception {
        UUID id = UUID.randomUUID();

        JobResponse job = new JobResponse(
                id,
                "EMAIL",
                objectMapper.readTree("{\"recipient\":\"test@test.com\"}"),
                JobStatus.PENDING,
                Instant.parse("2026-08-26T08:00:00Z"),
                Instant.parse("2026-08-26T08:00:00Z"),
                0,
                null
        );
        PageResponse<JobResponse> response = new PageResponse<>(
                List.of(job),
                0,
                20,
                1,
                1
        );

        when(jobService.getJobs(JobStatus.PENDING,0,20)).thenReturn(response);

        mockMvc.perform(get("/api/jobs")
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].type").value("EMAIL"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(jobService).getJobs(
                JobStatus.PENDING,
                0,
                20
        );
    }

    @Test
    void processJobs_shouldReturnProcessingSummary() throws Exception {
        when(jobService.processPendingJobs())
                .thenReturn(new ProcessJobsResponse(100, 10, 5, 115));

        mockMvc.perform(post("/api/jobs/process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(100))
                .andExpect(jsonPath("$.retries").value(5))
                .andExpect(jsonPath("$.failed").value(10))
                .andExpect(jsonPath("$.total").value(115));

        verify(jobService).processPendingJobs();
    }
}
