package com.duodian.admin.service;

import com.duodian.admin.entity.User;
import com.duodian.admin.repository.ShopRepository;
import com.duodian.admin.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final PasswordService passwordService;

    public UserService(UserRepository userRepository, ShopRepository shopRepository, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.passwordService = passwordService;
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

    @Transactional
    public User refreshShopStats(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setShopCount(Math.toIntExact(shopRepository.countRealShopsByUserId(userId)));
        user.setPlatformCount(Math.toIntExact(shopRepository.countRealPlatformsByUserId(userId)));
        return userRepository.save(user);
    }

    public User create(User user) {
        if (userRepository.existsByPhone(user.getPhone())) {
            throw new RuntimeException("手机号已存在");
        }
        user.setPassword(passwordService.encode(user.getPassword()));
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
        if (!passwordService.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        if (!passwordService.isBcrypt(user.getPassword())) {
            user.setPassword(passwordService.encode(password));
        }
        user.setLastLoginAt(java.time.LocalDateTime.now());
        return userRepository.save(user);
    }

    public boolean matchesPassword(User user, String rawPassword) {
        return user != null && passwordService.matches(rawPassword, user.getPassword());
    }

    public void updatePassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setPassword(passwordService.encode(newPassword));
        userRepository.save(user);
    }
}
