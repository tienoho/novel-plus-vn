package com.java2nb.novel.controller;

import com.java2nb.novel.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class UserControllerMappingTest {

    @Test
    void registrationEndpointRemainsMapped() throws Exception {
        Method method = UserController.class.getDeclaredMethod(
            "register", User.class, String.class, HttpServletRequest.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("register");
    }
}
