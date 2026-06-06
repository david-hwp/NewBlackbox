package com.duodian.admin.controller.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsInvalidPhoneNumber() {
        RegisterRequest request = validRequest();
        request.setPhone("12345678901");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("请输入有效的11位手机号");
    }

    @Test
    void acceptsValidPhoneNumber() {
        RegisterRequest request = validRequest();
        request.setPhone("13800138000");

        assertThat(validator.validate(request)).isEmpty();
    }

    private RegisterRequest validRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setPhone("13800138000");
        request.setPassword("123456");
        request.setUsername("测试用户");
        return request;
    }
}
