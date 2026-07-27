package com.java2nb.novel.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorControllerMappingTest {

    @Test
    void penNameValidationEndpointRemainsMapped() throws Exception {
        Method method = AuthorController.class.getDeclaredMethod("checkPenName", String.class);
        GetMapping mapping = method.getAnnotation(GetMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("checkPenName");
    }
}
