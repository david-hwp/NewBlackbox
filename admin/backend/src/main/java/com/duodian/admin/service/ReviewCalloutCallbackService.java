package com.duodian.admin.service;

import com.duodian.admin.controller.dto.GookiCalloutCallbackRequest;
import com.duodian.admin.entity.ShopOrder;
import com.duodian.admin.entity.ShopOrderReviewCallout;
import com.duodian.admin.entity.ShopOrderReviewCalloutResult;
import com.duodian.admin.repository.ShopOrderRepository;
import com.duodian.admin.repository.ShopOrderReviewCalloutRepository;
import com.duodian.admin.repository.ShopOrderReviewCalloutResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReviewCalloutCallbackService {
    private static final byte ACTIVE = 0;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1\\d{10})(?!\\d)");

    private final ShopOrderReviewCalloutRepository calloutRepository;
    private final ShopOrderReviewCalloutResultRepository resultRepository;
    private final ShopOrderRepository shopOrderRepository;
    private final ReviewCalloutBillingService billingService;
    private final ComputeService computeService;
    private final ObjectMapper objectMapper;

    public ReviewCalloutCallbackService(
            ShopOrderReviewCalloutRepository calloutRepository,
            ShopOrderReviewCalloutResultRepository resultRepository,
            ShopOrderRepository shopOrderRepository,
            ReviewCalloutBillingService billingService,
            ComputeService computeService,
            ObjectMapper objectMapper
    ) {
        this.calloutRepository = calloutRepository;
        this.resultRepository = resultRepository;
        this.shopOrderRepository = shopOrderRepository;
        this.billingService = billingService;
        this.computeService = computeService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ShopOrderReviewCalloutResult handle(GookiCalloutCallbackRequest request) {
        ParsedCallback parsed = parse(request);
        Optional<ShopOrderReviewCalloutResult> existing = resultRepository
                .findByCallbackIdempotencyKeyAndDeleted(parsed.idempotencyKey(), ACTIVE);
        if (existing.isPresent()) {
            return existing.get();
        }

        ShopOrderReviewCalloutResult result = new ShopOrderReviewCalloutResult();
        result.setExternalCdrId(parsed.externalCdrId());
        result.setExternalTaskId(parsed.externalTaskId());
        result.setCallbackIdempotencyKey(parsed.idempotencyKey());
        result.setPhoneMasked(maskPhone(parsed.phoneForAudit()));
        result.setPhoneHash(hashNullable(parsed.phoneForAudit()));
        result.setCallState(parsed.callState());
        result.setCallStateText(parsed.callStateText());
        result.setConnected(parsed.connected());
        result.setCallTime(parsed.callTime());
        result.setDurationSeconds(parsed.durationSeconds());
        result.setBilledMinutes(parsed.billedMinutes());
        result.setExternalMoneyCent(parsed.externalMoneyCent());
        result.setGrade(truncate(parsed.grade(), 255));
        result.setRemark(truncate(parsed.remark(), 512));
        result.setParamsSummary(safeJson(sanitizeForAudit(parsed.params())));
        result.setRawSummary(sanitizeSummary(request));

        Optional<ShopOrderReviewCallout> callout = resolveCallout(parsed);
        if (callout.isEmpty()) {
            result.setBillingStatus("UNMATCHED");
            result.setLastError("未匹配到本地外呼记录");
            return resultRepository.save(result);
        }
        applyCallout(result, callout.get());

        Optional<ShopOrder> order = shopOrderRepository.findById(result.getShopOrderId())
                .filter(o -> o.getDeleted() == null || o.getDeleted() == ACTIVE);
        order.ifPresent(o -> applyOrderSnapshot(result, o));

        ReviewCalloutBillingService.BillingDecision decision = billingService.decide(result);
        result.setBillableReason(decision.reason());
        if (!decision.billable()) {
            result.setBillingStatus("NOT_BILLABLE");
            return resultRepository.save(result);
        }

        result.setBilledMinutes(decision.billedMinutes());
        result.setDeductionAmount(decision.amount());
        result.setBillingRateUnitsPerMinute(decision.rateUnitsPerMinute());
        result.setMinimumChargeUnits(decision.minimumChargeUnits());

        String remark = buildRemark(result, decision);
        ComputeService.DeductionResult deduction = computeService.consumePhoneMinutesForReviewCallout(
                new ComputeService.PhoneConsumeRequest(
                        result.getUserId(),
                        decision.amount(),
                        result.getPlatform(),
                        result.getShopName(),
                        result.getShopOrderId(),
                        result.getReviewCalloutId(),
                        result.getExternalTaskId(),
                        result.getExternalCdrId(),
                        result.getCallTime(),
                        decision.rateUnitsPerMinute(),
                        remark
                )
        );
        if (!deduction.isSuccess()) {
            result.setBillingStatus("INSUFFICIENT_BALANCE");
            result.setLastError("话费余额不足");
            return resultRepository.save(result);
        }
        result.setBillingStatus("DEDUCTED");
        result.setTransactionLogId(deduction.getTransactionLogId());
        result.setDeductedAt(LocalDateTime.now());
        return resultRepository.save(result);
    }

    private Optional<ShopOrderReviewCallout> resolveCallout(ParsedCallback parsed) {
        Long localCalloutId = firstLong(parsed.params(), "本地外呼记录ID", "reviewCalloutId", "review_callout_id");
        if (localCalloutId != null) {
            Optional<ShopOrderReviewCallout> callout = calloutRepository.findByIdAndDeleted(localCalloutId, ACTIVE);
            if (callout.isPresent()) {
                return callout;
            }
        }
        Long shopOrderId = firstLong(parsed.params(), "系统订单ID", "shopOrderId", "shop_order_id");
        if (shopOrderId != null) {
            return calloutRepository.findByShopOrderIdAndDeleted(shopOrderId, ACTIVE);
        }
        return Optional.empty();
    }

    private void applyCallout(ShopOrderReviewCalloutResult result, ShopOrderReviewCallout callout) {
        result.setReviewCalloutId(callout.getId());
        result.setShopOrderId(callout.getShopOrderId());
        result.setShopId(callout.getShopId());
        result.setUserId(callout.getUserId());
        result.setChannelId(callout.getChannelId());
        result.setPlatform(coalesce(result.getPlatform(), callout.getPlatform()));
        result.setPlatformShopId(coalesce(result.getPlatformShopId(), callout.getPlatformShopId()));
        result.setPlatformOrderId(coalesce(result.getPlatformOrderId(), callout.getPlatformOrderId()));
        result.setShopName(coalesce(result.getShopName(), callout.getShopName()));
        if (result.getExternalTaskId() == null) {
            result.setExternalTaskId(callout.getExternalTaskId());
        }
    }

    private void applyOrderSnapshot(ShopOrderReviewCalloutResult result, ShopOrder order) {
        result.setShopId(coalesce(result.getShopId(), order.getShopId()));
        result.setUserId(coalesce(result.getUserId(), order.getUserId()));
        result.setChannelId(coalesce(result.getChannelId(), order.getChannelId()));
        result.setPlatform(coalesce(result.getPlatform(), order.getPlatform()));
        result.setPlatformShopId(coalesce(result.getPlatformShopId(), order.getPlatformShopId()));
        result.setPlatformOrderId(coalesce(result.getPlatformOrderId(), order.getPlatformOrderId()));
        result.setShopName(coalesce(result.getShopName(), order.getShopName()));
    }

    private ParsedCallback parse(GookiCalloutCallbackRequest request) {
        Map<String, Object> params = parseParams(request.getParams());
        String rawExternalCdrId = blankToNull(request.getCdrId());
        String rawExternalTaskId = blankToNull(request.getTaskId());
        String externalCdrId = truncate(rawExternalCdrId, 128);
        String externalTaskId = truncate(rawExternalTaskId, 128);
        String phone = blankToNull(coalesce(request.getRealPhone(), request.getPhone()));
        LocalDateTime callTime = parseCallTime(request.getCallTime());
        String callState = objectToString(request.getCallState());
        String idempotencySource = rawExternalCdrId != null
                ? rawExternalCdrId
                : coalesce(rawExternalTaskId, "-") + ":" + coalesce(objectToString(params.get("本地外呼记录ID")), "-")
                + ":" + coalesce(hashNullable(phone), "-") + ":" + (callTime == null ? "-" : callTime);
        String idempotencyKey = (rawExternalCdrId != null ? "cdr:" : "fallback:") + hashNullable(idempotencySource);
        Boolean connected = "2".equals(callState) || containsAny(request.getCallStateText(), "接通", "已接");
        return new ParsedCallback(
                externalCdrId,
                externalTaskId,
                idempotencyKey,
                phone,
                callState,
                blankToNull(request.getCallStateText()),
                connected,
                callTime,
                parseInteger(request.getDuration()),
                parseInteger(request.getMinute()),
                parseInteger(request.getMoney()),
                blankToNull(request.getGrade()),
                blankToNull(request.getRemark()),
                params
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) result.put(String.valueOf(k), v);
            });
            return result;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return new LinkedHashMap<>();
        }
        for (String candidate : new String[]{text, decodeUrlSafely(text)}) {
            Optional<Map<String, Object>> parsed = tryParseParamsJson(candidate);
            if (parsed.isPresent()) {
                return parsed.get();
            }
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("raw", truncate(text, 512));
        return fallback;
    }

    private Optional<Map<String, Object>> tryParseParamsJson(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return Optional.empty();
        }
        try {
            Object parsed = objectMapper.readValue(candidate, Object.class);
            if (parsed instanceof Map<?, ?> parsedMap) {
                return Optional.of(parseParams(parsedMap));
            }
            if (parsed instanceof String parsedString && !parsedString.equals(candidate)) {
                return Optional.of(parseParams(parsedString));
            }
        } catch (Exception ignored) {
            // keep the callback endpoint tolerant of third-party payload drift
        }
        return Optional.empty();
    }

    private String decodeUrlSafely(String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private LocalDateTime parseCallTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            long epoch = number.longValue();
            if (epoch > 10_000_000_000L) {
                epoch = epoch / 1000;
            }
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), SHANGHAI);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        Integer numeric = parseInteger(text);
        if (numeric != null && text.matches("\\d{10,13}")) {
            long epoch = Long.parseLong(text);
            if (epoch > 10_000_000_000L) {
                epoch = epoch / 1000;
            }
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), SHANGHAI);
        }
        for (DateTimeFormatter formatter : new DateTimeFormatter[]{
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        }) {
            try {
                return LocalDateTime.parse(text.replace('T', ' '), formatter);
            } catch (Exception ignored) {
                // try next
            }
        }
        return null;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(text));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long firstLong(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object value = params.get(key);
            Long parsed = parseLong(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private String buildRemark(ShopOrderReviewCalloutResult result, ReviewCalloutBillingService.BillingDecision decision) {
        return "好评外呼扣费: 店铺=" + coalesce(result.getShopName(), "-")
                + ", 订单=" + coalesce(result.getPlatformOrderId(), String.valueOf(result.getShopOrderId()))
                + ", 外呼时间=" + (result.getCallTime() == null ? "-" : result.getCallTime())
                + ", 话单=" + coalesce(result.getExternalCdrId(), "-")
                + ", 计费分钟=" + decision.billedMinutes()
                + ", 费率=" + decision.rateUnitsPerMinute()
                + ", 扣费=" + decision.amount();
    }

    private String sanitizeSummary(GookiCalloutCallbackRequest request) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("cdrId", request.getCdrId());
        summary.put("taskId", request.getTaskId());
        summary.put("phoneMasked", maskPhone(coalesce(request.getRealPhone(), request.getPhone())));
        summary.put("callTime", request.getCallTime());
        summary.put("callState", request.getCallState());
        summary.put("duration", request.getDuration());
        summary.put("minute", request.getMinute());
        summary.put("money", request.getMoney());
        summary.put("grade", request.getGrade());
        summary.put("remark", truncate(request.getRemark(), 128));
        summary.put("params", sanitizeForAudit(parseParams(request.getParams())));
        return safeJson(summary);
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeForAudit(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                if (key == null) {
                    return;
                }
                String name = String.valueOf(key);
                sanitized.put(name, shouldRedactField(name) ? "[REDACTED]" : sanitizeForAudit(nested));
            });
            return sanitized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::sanitizeForAudit).toList();
        }
        if (value instanceof String text) {
            return maskSensitiveText(truncate(text, 512));
        }
        return value;
    }

    private boolean shouldRedactField(String name) {
        String lower = name.toLowerCase();
        return lower.contains("token")
                || lower.contains("secret")
                || lower.contains("authorization")
                || lower.contains("cookie")
                || lower.contains("password")
                || lower.contains("profile")
                || lower.contains("storage")
                || lower.contains("debugport")
                || lower.contains("realphone")
                || lower.equals("phone")
                || lower.contains("手机号")
                || lower.contains("电话");
    }

    private String maskSensitiveText(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = PHONE_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String phone = matcher.group(1);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(phone.substring(0, 3) + "****" + phone.substring(7)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String safeJson(Object value) {
        try {
            return truncate(objectMapper.writeValueAsString(value), 4096);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String maskPhone(String phone) {
        String value = blankToNull(phone);
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() >= 11) {
            return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
        }
        if (digits.length() >= 4) {
            return "****" + digits.substring(digits.length() - 4);
        }
        return "****";
    }

    private String hashNullable(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private boolean containsAny(String value, String... needles) {
        if (value == null) {
            return false;
        }
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String objectToString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private <T> T coalesce(T first, T second) {
        return first != null ? first : second;
    }

    private record ParsedCallback(
            String externalCdrId,
            String externalTaskId,
            String idempotencyKey,
            String phoneForAudit,
            String callState,
            String callStateText,
            Boolean connected,
            LocalDateTime callTime,
            Integer durationSeconds,
            Integer billedMinutes,
            Integer externalMoneyCent,
            String grade,
            String remark,
            Map<String, Object> params
    ) {}
}
