package com.duodian.admin.service;

import com.duodian.admin.entity.Channel;
import com.duodian.admin.entity.SystemParameter;
import com.duodian.admin.repository.ChannelRepository;
import com.duodian.admin.repository.SystemParameterRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemParameterServiceTest {
    private final SystemParameterRepository repository = mock(SystemParameterRepository.class);
    private final ChannelRepository channelRepository = mock(ChannelRepository.class);
    private final ChannelScopeService channelScopeService = mock(ChannelScopeService.class);
    private final SystemParameterService service = new SystemParameterService(repository, channelRepository, channelScopeService);

    @Test
    void resolveAppParametersUsesChannelOverrideAfterMainDefaults() {
        Channel main = channel(1L, "main");
        Channel beta = channel(2L, "beta");
        when(channelScopeService.mainChannel()).thenReturn(main);
        when(repository.findByChannelIdAndDeletedOrderByUpdatedAtDesc(1L, (byte) 0)).thenReturn(List.of(
                parameter(1L, SystemParameterService.APP_MENU_GIFT_COMPUTE_LABEL, "算力赠送"),
                parameter(1L, SystemParameterService.APP_MENU_TRANSACTION_LOGS_LABEL, "交易日志")
        ));
        when(repository.findByChannelIdAndDeletedOrderByUpdatedAtDesc(2L, (byte) 0)).thenReturn(List.of(
                parameter(2L, SystemParameterService.APP_MENU_GIFT_COMPUTE_LABEL, "赠送算力")
        ));

        var result = service.resolveAppParameters(beta);

        assertThat(result).containsEntry(SystemParameterService.APP_MENU_GIFT_COMPUTE_LABEL, "赠送算力");
        assertThat(result).containsEntry(SystemParameterService.APP_MENU_TRANSACTION_LOGS_LABEL, "交易日志");
    }

    @Test
    void intValueFallsBackWhenParameterIsInvalid() {
        Channel main = channel(1L, "main");
        when(channelScopeService.mainChannel()).thenReturn(main);
        when(repository.findByChannelIdAndDeletedOrderByUpdatedAtDesc(1L, (byte) 0)).thenReturn(List.of(
                parameter(1L, SystemParameterService.REGISTER_TRIAL_SUBSCRIPTION_DAYS, "abc")
        ));

        int value = service.intValue(main, SystemParameterService.REGISTER_TRIAL_SUBSCRIPTION_DAYS, 30, 0, 3650);

        assertThat(value).isEqualTo(30);
    }

    @Test
    void createRejectsDuplicateCodeInSameChannel() {
        Channel main = channel(1L, "main");
        SystemParameter parameter = parameter(1L, "demo.code", "value");
        parameter.setName("参数");
        when(channelScopeService.mainChannel()).thenReturn(main);
        when(channelRepository.findByIdAndDeleted(1L, (byte) 0)).thenReturn(Optional.of(main));
        when(repository.existsByChannelIdAndCodeAndDeleted(1L, "demo.code", (byte) 0)).thenReturn(true);

        assertThatThrownBy(() -> service.create(parameter))
                .hasMessage("同渠道参数编码已存在");
    }

    @Test
    void deleteRejectsBuiltinParameter() {
        SystemParameter parameter = parameter(1L, "app.menu.gift_compute.label", "算力赠送");
        parameter.setId(8L);
        parameter.setBuiltin((byte) 1);
        when(repository.findByIdAndDeleted(8L, (byte) 0)).thenReturn(Optional.of(parameter));

        assertThatThrownBy(() -> service.delete(8L))
                .hasMessage("系统内置参数不能删除");
    }

    private Channel channel(Long id, String code) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setCode(code);
        channel.setName(code);
        channel.setStatus("ACTIVE");
        return channel;
    }

    private SystemParameter parameter(Long channelId, String code, String value) {
        SystemParameter parameter = new SystemParameter();
        parameter.setChannelId(channelId);
        parameter.setCode(code);
        parameter.setName(code);
        parameter.setValue(value);
        return parameter;
    }
}
