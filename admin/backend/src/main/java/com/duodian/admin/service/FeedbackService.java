package com.duodian.admin.service;

import com.duodian.admin.entity.Feedback;
import com.duodian.admin.repository.FeedbackRepository;
import com.duodian.admin.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FeedbackService {
    private static final byte ACTIVE = 0;
    private static final byte DELETED = 1;

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    public FeedbackService(FeedbackRepository feedbackRepository, UserRepository userRepository) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
    }

    public List<Feedback> findAll() {
        return feedbackRepository.findByDeletedOrderByCreatedAtDesc(ACTIVE);
    }

    public List<Feedback> findByChannelId(Long channelId) {
        return feedbackRepository.findByChannelIdAndDeletedOrderByCreatedAtDesc(channelId, ACTIVE);
    }

    public Optional<Feedback> findById(Long id) {
        return feedbackRepository.findByIdAndDeleted(id, ACTIVE);
    }

    public List<Feedback> findByUserId(Long userId) {
        return feedbackRepository.findByUserIdAndDeletedOrderByCreatedAtDesc(userId, ACTIVE);
    }

    public List<Feedback> findByStatus(String status) {
        return feedbackRepository.findByStatusAndDeletedOrderByCreatedAtDesc(status, ACTIVE);
    }

    public Feedback create(Feedback feedback) {
        feedback.setDeleted(ACTIVE);
        if (feedback.getChannelId() == null && feedback.getUserId() != null) {
            userRepository.findByIdAndDeleted(feedback.getUserId(), ACTIVE)
                    .ifPresent(user -> feedback.setChannelId(user.getChannelId()));
        }
        return feedbackRepository.save(feedback);
    }

    public Feedback updateStatus(Long id, String status) {
        Feedback feedback = feedbackRepository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("反馈不存在"));
        feedback.setStatus(status);
        return feedbackRepository.save(feedback);
    }

    public void delete(Long id) {
        Feedback feedback = feedbackRepository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("反馈不存在"));
        feedback.setDeleted(DELETED);
        feedbackRepository.save(feedback);
    }
}
