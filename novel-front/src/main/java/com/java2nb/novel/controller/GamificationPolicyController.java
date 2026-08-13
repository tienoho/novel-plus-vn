package com.java2nb.novel.controller;

import com.java2nb.novel.dto.gamification.GamificationPublicPolicyResponse;
import com.java2nb.novel.service.gamification.GamificationPublicPolicyRow;
import com.java2nb.novel.service.gamification.GamificationPublicPolicyService;
import io.github.xxyopen.model.resp.RestResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GamificationPolicyController {

    private final GamificationPublicPolicyService service;

    @GetMapping("gamification/policy")
    public RestResult<GamificationPublicPolicyResponse> getPublishedPolicy() {
        GamificationPublicPolicyRow policy = service.getPublished();
        return RestResult.ok(policy == null ? null : GamificationPublicPolicyResponse.from(policy));
    }
}
