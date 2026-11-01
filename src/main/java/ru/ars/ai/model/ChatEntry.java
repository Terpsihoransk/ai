package ru.ars.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.ai.chat.messages.Message;

import java.time.LocalDateTime;

/**
 * Одно сообщение чата. Владелец связи — {@link Chat#getHistory()} (join-таблица),
 * поэтому здесь обратная ссылка не нужна.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    private Role role;

    @CreationTimestamp
    private LocalDateTime startedAt;

    // Преобразует запись истории в сообщение для модели
    public Message toMessage() {
        return role.getMessage(content);
    }

    // Преобразует сообщение модели в запись истории
    public static ChatEntry toChatEntry(Message message) {
        return ChatEntry.builder()
                .role(Role.fromMessageType(message.getMessageType()))
                .content(message.getText())
                .build();
    }

}