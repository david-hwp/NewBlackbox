package com.duodian.admin.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    private static final String BCRYPT_PREFIX_2A = "$2a$";
    private static final String BCRYPT_PREFIX_2B = "$2b$";
    private static final String BCRYPT_PREFIX_2Y = "$2y$";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isBlank()) {
            return false;
        }
        if (isBcrypt(storedPassword)) {
            return encoder.matches(rawPassword, storedPassword);
        }
        return storedPassword.equals(rawPassword);
    }

    public boolean isBcrypt(String storedPassword) {
        if (storedPassword == null || storedPassword.length() < 60) {
            return false;
        }
        return storedPassword.startsWith(BCRYPT_PREFIX_2A)
                || storedPassword.startsWith(BCRYPT_PREFIX_2B)
                || storedPassword.startsWith(BCRYPT_PREFIX_2Y);
    }
}
