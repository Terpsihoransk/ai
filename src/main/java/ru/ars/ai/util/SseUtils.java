package ru.ars.ai.util;

import tools.jackson.databind.json.JsonMapper;

/**
 * Утилиты для безопасной передачи данных через Server-Sent Events.
 */
public final class SseUtils {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    /**
     * Экранирует значение как JSON-строку. Нужно потому, что SSE отбрасывает ведущий пробел
     * после "data:", из-за чего токены-пробелы терялись бы в UI.
     */
    public static String toJsonString(String value) {
        return JSON_MAPPER.writeValueAsString(value);
    }
}
