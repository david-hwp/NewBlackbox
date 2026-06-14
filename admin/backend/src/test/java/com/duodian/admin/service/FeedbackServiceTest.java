package com.duodian.admin.service;

import com.duodian.admin.entity.Feedback;
import com.duodian.admin.repository.FeedbackRepository;
import com.duodian.admin.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackServiceTest {

    private final FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FeedbackService feedbackService = new FeedbackService(feedbackRepository, userRepository);

    @Test
    void deleteMarksFeedbackDeletedInsteadOfHardDeleting() {
        Feedback feedback = new Feedback();
        feedback.setId(12L);
        feedback.setContent("问题反馈");
        feedback.setDeleted((byte) 0);
        when(feedbackRepository.findByIdAndDeleted(12L, (byte) 0)).thenReturn(Optional.of(feedback));

        feedbackService.delete(12L);

        assertThat(feedback.getDeleted()).isEqualTo((byte) 1);
        verify(feedbackRepository).save(feedback);
    }
}
