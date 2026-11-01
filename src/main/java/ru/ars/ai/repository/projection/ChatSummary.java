package ru.ars.ai.repository.projection;

import java.time.LocalDateTime;

/**
 * Облегчённое представление чата для списка в сайдбаре — без истории сообщений.
 */
public interface ChatSummary {

    Long getId();

    String getTitle();

    LocalDateTime getCreatedAt();
}
