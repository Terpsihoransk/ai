package ru.ars.ai.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Роль участника диалога. Каждая роль умеет создавать соответствующее сообщение модели,
 * поэтому конвертация {@code Role -> Message} живёт рядом с самим перечислением, а не в виде
 * {@code switch} в других классах.
 */
@Getter
@RequiredArgsConstructor
public enum Role {

    USER("user") {
        @Override
        Message getMessage(String content) {
            return new UserMessage(content);
        }
    },
    ASSISTANT("assistant") {
        @Override
        Message getMessage(String content) {
            return new AssistantMessage(content);
        }
    },
    SYSTEM("system") {
        @Override
        Message getMessage(String content) {
            return new SystemMessage(content);
        }
    };

    /** Отображаемое имя роли, используется в шаблонах. */
    private final String role;

    /** Создаёт сообщение модели, соответствующее этой роли. */
    abstract Message getMessage(String content);

    /** Роль по типу сообщения модели. */
    public static Role fromMessageType(MessageType messageType) {
        return switch (messageType) {
            case USER -> USER;
            case ASSISTANT -> ASSISTANT;
            case SYSTEM -> SYSTEM;
            case TOOL -> throw new IllegalArgumentException(
                    "Сообщения типа TOOL не поддерживаются историей чата");
        };
    }

}