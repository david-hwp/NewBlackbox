package com.duodian.admin.service;

import com.duodian.admin.controller.dto.AdvancedFeatureUpdateRequest;
import com.duodian.admin.entity.AdvancedFeature;
import com.duodian.admin.repository.AdvancedFeatureRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdvancedFeatureServiceTest {
    private final AdvancedFeatureRepository repository = mock(AdvancedFeatureRepository.class);
    private final AdvancedFeatureService service = new AdvancedFeatureService(repository);

    @Test
    void updateStoresOpenConfigAndDirectTextFields() {
        AdvancedFeature feature = feature("bad_review_location");
        when(repository.findByCodeAndDeleted("bad_review_location", (byte) 0)).thenReturn(Optional.of(feature));
        when(repository.save(any(AdvancedFeature.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdvancedFeatureUpdateRequest request = new AdvancedFeatureUpdateRequest();
        request.setOnline(true);
        request.setMonthlyComputeCost(6);
        request.setSupportedPlatformPackages(List.of("com.sankuai.meituan.takeoutnew", "com.jd.mrd"));
        request.setTitle("差评定位");
        request.setLine1("待处理 3");
        request.setLine2("今日新增 1");
        request.setOutboundEnabled(true);

        var response = service.update("bad_review_location", request);

        assertThat(response.getOnline()).isTrue();
        assertThat(response.getMonthlyComputeCost()).isEqualTo(6);
        assertThat(response.getSupportedPlatformPackages())
                .containsExactly("com.sankuai.meituan.takeoutnew", "com.jd.mrd");
        assertThat(response.getTitle()).isEqualTo("差评定位");
        assertThat(response.getLine1()).isEqualTo("待处理 3");
        assertThat(response.getLine2()).isEqualTo("今日新增 1");
        assertThat(response.getOutboundEnabled()).isTrue();
    }

    @Test
    void emptyPlatformListMeansAllPlatforms() {
        AdvancedFeature feature = feature("business_report");
        feature.setSupportedPlatformPackages(null);
        when(repository.findByDeletedOrderBySortOrderAscIdAsc((byte) 0)).thenReturn(List.of(feature));

        var response = service.findAll().getFirst();

        assertThat(response.getSupportedPlatformPackages()).isEmpty();
    }

    private AdvancedFeature feature(String code) {
        AdvancedFeature feature = new AdvancedFeature();
        feature.setId(1L);
        feature.setCode(code);
        feature.setName(code);
        feature.setOnline(true);
        feature.setMonthlyComputeCost(1);
        feature.setTitleCode("app.shop_feature." + code + ".label");
        feature.setLine1Code("app.shop_feature." + code + ".line1");
        feature.setLine2Code("app.shop_feature." + code + ".line2");
        feature.setTitle(code);
        feature.setLine1("-");
        feature.setLine2("-");
        feature.setOutboundEnabled(false);
        feature.setDeleted((byte) 0);
        return feature;
    }
}
