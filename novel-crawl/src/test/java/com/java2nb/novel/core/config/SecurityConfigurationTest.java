package com.java2nb.novel.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = SecurityConfigurationTest.TestController.class,
    properties = {
        "admin.username=crawler-admin",
        "admin.password=strong-test-password",
        "security.csrf.cookie-secure=true",
        "novel.common.web-advice.enabled=false"
    }
)
@ContextConfiguration(classes = {
    SecurityConfiguration.class,
    SecurityConfigurationTest.TestController.class
})
class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsAuthenticatedWriteWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/test-write")
                .with(httpBasic("crawler-admin", "strong-test-password")))
            .andExpect(status().isForbidden());
    }

    @Test
    void acceptsAuthenticatedWriteWithCsrfToken() throws Exception {
        mockMvc.perform(post("/test-write")
                .with(httpBasic("crawler-admin", "strong-test-password"))
                .with(csrf()))
            .andExpect(status().isOk());
    }

    @RestController
    static class TestController {

        @PostMapping("/test-write")
        ResponseEntity<Void> write() {
            return ResponseEntity.ok().build();
        }
    }
}
