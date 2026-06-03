package com.example.validator.controller;

import com.example.validator.domain.ProgressSnapshot;
import com.example.validator.service.ValidationJobLauncher;
import com.example.validator.service.ValidationJobService;
import java.util.Collections;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 核验作业控制接口。
 *
 * <p>职责：提供启动作业、查看进度、暂停和恢复作业的 HTTP API。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
@RestController
@RequestMapping("/api/validation/jobs")
public class ValidationJobController {
    private final ValidationJobLauncher launcher;
    private final ValidationJobService jobService;

    /**
     * 创建作业控制接口。
     *
     * @param launcher 作业启动服务
     * @param jobService 作业控制服务
     */
    public ValidationJobController(ValidationJobLauncher launcher, ValidationJobService jobService) {
        this.launcher = launcher;
        this.jobService = jobService;
    }

    /**
     * 启动一个新的核验作业。
     *
     * @return 包含 batchId 的响应
     */
    @PostMapping("/start")
    public Map<String, String> start() {
        return Collections.singletonMap("batchId", launcher.startAsync());
    }

    /**
     * 查询作业进度。
     *
     * @param batchId 批次号
     * @return 作业进度快照
     */
    @GetMapping("/{batchId}/progress")
    public ProgressSnapshot progress(@PathVariable String batchId) {
        return jobService.progress(batchId);
    }

    /**
     * 暂停作业。
     *
     * @param batchId 批次号
     * @return 作业进度快照
     */
    @PostMapping("/{batchId}/pause")
    public ProgressSnapshot pause(@PathVariable String batchId) {
        jobService.pause(batchId);
        return jobService.progress(batchId);
    }

    /**
     * 恢复作业。
     *
     * @param batchId 批次号
     * @return 作业进度快照
     */
    @PostMapping("/{batchId}/resume")
    public ProgressSnapshot resume(@PathVariable String batchId) {
        jobService.resume(batchId);
        return jobService.progress(batchId);
    }
}
