package com.duodian.admin.service;

import com.duodian.admin.entity.User;
import com.duodian.admin.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByPhone(String phone) {
        return userRepository.findByPhone(phone);
    }

    public User create(User user) {
        if (userRepository.existsByPhone(user.getPhone())) {
            throw new RuntimeException("手机号已存在");
        }
        return userRepository.save(user);
    }

    public User update(Long id, User user) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        existing.setUsername(user.getUsername());
        existing.setComputeBalance(user.getComputeBalance());
        existing.setShopCount(user.getShopCount());
        existing.setPlatformCount(user.getPlatformCount());
        return userRepository.save(existing);
    }

    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    public User login(String phone, String password) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (!user.getPassword().equals(password)) {
            throw new RuntimeException("密码错误");
        }
        user.setLastLoginAt(java.time.LocalDateTime.now());
        return userRepository.save(user);
    }
}
