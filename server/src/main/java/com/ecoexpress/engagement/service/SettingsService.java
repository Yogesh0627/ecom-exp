package com.ecoexpress.engagement.service;

import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.engagement.domain.Setting;
import com.ecoexpress.engagement.repository.SettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Typed access to runtime settings, with a sensible default for every read.
 *
 * <p>The design goal: no module should hardcode a delivery fee or an AI budget. They ask this
 * service, which reads the JSONB {@code settings} row and falls back to a supplied default if the
 * row is missing or malformed — so a deleted or bad setting degrades to a safe default rather than
 * a crash. Reads are cached; a write evicts the cache.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingRepository settingRepository;
    private final ObjectMapper mapper;

    @Cacheable(value = "settings", key = "#key")
    @Transactional(readOnly = true)
    public String getString(String key, String defaultValue) {
        return settingRepository.findByKey(key)
                .map(s -> unquote(s.getValue()))
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        try {
            String raw = getString(key, null);
            return raw == null ? defaultValue : new BigDecimal(raw);
        } catch (NumberFormatException e) {
            log.warn("Setting {} is not a number — using default {}", key, defaultValue);
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public int getInt(String key, int defaultValue) {
        try {
            String raw = getString(key, null);
            return raw == null ? defaultValue : Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean defaultValue) {
        String raw = getString(key, null);
        return raw == null ? defaultValue : Boolean.parseBoolean(raw.trim());
    }

    /** Settings the storefront is allowed to read. */
    @Transactional(readOnly = true)
    public Map<String, Object> publicSettings() {
        return settingRepository.findByIsPublicTrue().stream()
                .collect(java.util.stream.Collectors.toMap(Setting::getKey, s -> parse(s.getValue())));
    }

    @CacheEvict(value = "settings", key = "#key")
    @Transactional
    public void set(String key, String jsonValue) {
        Setting setting = settingRepository.findByKey(key)
                .orElseThrow(() -> new NotFoundException("No setting '" + key + "'."));
        // Validate it is JSON so a bad write cannot poison a later typed read.
        try {
            mapper.readTree(jsonValue);
        } catch (Exception e) {
            throw new com.ecoexpress.common.exception.ApiExceptions.BadRequestException(
                    "Setting value must be valid JSON.");
        }
        setting.setValue(jsonValue);
        log.info("Setting {} updated", key);
    }

    @Transactional(readOnly = true)
    public List<Setting> all() {
        return settingRepository.findAll();
    }

    /** Strips surrounding quotes from a JSON string literal ("foo" -> foo); leaves scalars alone. */
    private String unquote(String jsonValue) {
        if (jsonValue == null) {
            return null;
        }
        String v = jsonValue.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            try {
                return mapper.readValue(v, String.class);
            } catch (Exception ignored) {
                return v.substring(1, v.length() - 1);
            }
        }
        return v;
    }

    private Object parse(String jsonValue) {
        try {
            return mapper.readValue(jsonValue, Object.class);
        } catch (Exception e) {
            return jsonValue;
        }
    }
}
