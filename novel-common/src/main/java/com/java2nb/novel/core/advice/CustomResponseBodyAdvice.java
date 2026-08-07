package com.java2nb.novel.core.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Objects;

/**
 * Khi tuần tự hóa JSON từ RestController, chuyển Long thành String để tránh mất độ chính xác ở frontend
 * Thay cấu hình spring.jackson.generator.write-numbers-as-strings=true để không ảnh hưởng ObjectMapper toàn cục
 *
 * @author xiongxiaoyang
 * */
@RestControllerAdvice
public class CustomResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper customObjectMapper;

    public CustomResponseBodyAdvice(Jackson2ObjectMapperBuilder builder) {
        customObjectMapper = builder.createXmlMapper(false).build();
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
        simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        customObjectMapper.registerModule(simpleModule);
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Chỉ chuẩn hóa response do Jackson JSON converter xử lý; String/byte[] phải giữ nguyên kiểu.
        return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        // Dùng ObjectMapper tùy chỉnh để tuần tự hóa phần thân phản hồi
        if(Objects.nonNull(body)) {
            return customObjectMapper.valueToTree(body);
        }else{
            return null;
        }
    }

}

