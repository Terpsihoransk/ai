package ru.ars.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ars.ai.model.Chat;
import ru.ars.ai.repository.ChatRepository;
import ru.ars.ai.repository.projection.ChatSummary;

import java.util.List;

/**
 * Бизнес-логика чатов: CRUD и чтение чатов для UI.
 * Транспорт (SSE) вынесен в {@link ChatStreamService}, а чтение/запись истории сообщений —
 * в {@link ru.ars.ai.memory.PostgresChatMemory} (через advisor Spring AI); контроллер не должен
 * зависеть от способа доставки токенов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String CHAT_NOT_FOUND = "Чат с id=%d не найден";

    private final ChatRepository chatRepository;

    @Transactional(readOnly = true)
    public List<ChatSummary> getAllChats() {
        List<ChatSummary> chats = chatRepository.findAllByOrderByCreatedAtDesc();
        log.debug("Получен список чатов, количество={}", chats.size());
        return chats;
    }

    @Transactional(readOnly = true)
    public Chat getChat(Long chatId) {
        return chatRepository.findWithHistoryById(chatId).orElseThrow(() -> chatNotFound(chatId));
    }

    @Transactional
    public Chat createNewChat(String title) {
        Chat saved = chatRepository.save(Chat.builder().title(title).build());
        log.info("Создан чат id={}, title='{}'", saved.getId(), title);
        return saved;
    }

    @Transactional
    public void deleteChat(Long chatId) {
        if (!chatRepository.existsById(chatId)) {
            throw chatNotFound(chatId);
        }
        chatRepository.deleteById(chatId);
        log.info("Удалён чат id={}", chatId);
    }

    private IllegalArgumentException chatNotFound(Long chatId) {
        log.warn("Чат с id={} не найден", chatId);
        return new IllegalArgumentException(CHAT_NOT_FOUND.formatted(chatId));
    }

}