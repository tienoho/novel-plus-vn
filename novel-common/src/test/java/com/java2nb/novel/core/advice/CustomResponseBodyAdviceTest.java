package com.java2nb.novel.core.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

class CustomResponseBodyAdviceTest {

    private final CustomResponseBodyAdvice advice =
        new CustomResponseBodyAdvice(new Jackson2ObjectMapperBuilder());

    @Test
    void onlyAppliesToJacksonJsonResponses() {
        MethodParameter returnType = mock(MethodParameter.class);

        assertThat(advice.supports(returnType, MappingJackson2HttpMessageConverter.class)).isTrue();
        assertThat(advice.supports(returnType, ByteArrayHttpMessageConverter.class)).isFalse();
        assertThat(advice.supports(returnType, StringHttpMessageConverter.class)).isFalse();
    }
}
