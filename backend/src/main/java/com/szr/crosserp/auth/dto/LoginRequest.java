package com.szr.crosserp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "请输入登录名")
    @Size(max = 100, message = "登录名长度不能超过100个字符")
    String loginName,
    @NotBlank(message = "请输入密码")
    @Size(max = 255, message = "密码长度不能超过255个字符")
    String password
) {
}

