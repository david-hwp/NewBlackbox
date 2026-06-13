package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.entity.Announcement;
import com.duodian.admin.repository.AnnouncementRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnnouncementControllerTest {

    private final AnnouncementRepository repository = mock(AnnouncementRepository.class);
    private final AnnouncementController controller = new AnnouncementController(repository);

    @Test
    void listNormalizesScrollingTickerType() {
        when(repository.findByPublishedAndTypeAndDeletedOrderByCreatedAtDesc(
                eq(true),
                eq("SCROLLING_TICKER"),
                eq((byte) 0)
        )).thenReturn(List.of(announcement("滚动播报", "播报内容", "SCROLLING_TICKER")));

        ApiResponse<?> response = controller.list(true, " scrolling_ticker ", null, null, null);

        assertThat(response.getCode()).isEqualTo(200);
        verify(repository).findByPublishedAndTypeAndDeletedOrderByCreatedAtDesc(
                true,
                "SCROLLING_TICKER",
                (byte) 0
        );
    }

    @Test
    void createKeepsScrollingTickerTitle() {
        Announcement request = announcement("首页提示", "今天营业数据已更新", "scrolling_ticker");
        when(repository.save(request)).thenReturn(request);

        ApiResponse<Announcement> response = controller.create(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getType()).isEqualTo("SCROLLING_TICKER");
        assertThat(response.getData().getTitle()).isEqualTo("首页提示");
    }

    private Announcement announcement(String title, String content, String type) {
        Announcement announcement = new Announcement();
        announcement.setTitle(title);
        announcement.setContent(content);
        announcement.setType(type);
        announcement.setPublished(true);
        return announcement;
    }
}
