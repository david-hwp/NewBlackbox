package com.duodian.admin.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入有效的11位手机号")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在6-64位之间")
    private String password;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 32, message = "用户名不能超过32字符")
    private String username;

    @Size(max = 64, message = "渠道标识不能超过64字符")
    private String apkChannel;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getApkChannel() {
        return apkChannel;
    }

    public void setApkChannel(String apkChannel) {
        this.apkChannel = apkChannel;
    }
}
