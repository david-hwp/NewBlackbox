package com.duodian.admin.service;

import com.duodian.admin.entity.Feedback;
import com.duodian.admin.repository.FeedbackRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    public FeedbackService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    public List<Feedback> findAll() {
        return feedbackRepository.findAll();
    }

    public Optional<Feedback> findById(Long id) {
        return feedbackRepository.findById(id);
    }

    public List<Feedback> findByUserId(Long userId) {
        return feedbackRepository.findByUserId(userId);
    }

    public List<Feedback> findByStatus(String status) {
        return feedbackRepository.findByStatus(status);
    }

    public Feedback create(Feedback feedback) {
        return feedbackRepository.save(feedback);
    }

    public Feedback updateStatus(Long id, String status) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("反馈不存在"));
        feedback.setStatus(status);
        return feedbackRepository.save(feedback);
    }

    public void delete(Long id) {
        feedbackRepository.deleteById(id);
    }
}
