package ru.ars.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ars.ai.model.Chat;
import ru.ars.ai.model.ChatEntry;
import ru.ars.ai.model.Role;
import ru.ars.ai.repository.ChatRepository;
import ru.ars.ai.repository.projection.ChatSummary;

import java.util.List;

/**
 * Бизнес-логика чатов: CRUD и работа с историей сообщений.
 * Транспорт (SSE) и взаимодействие с моделью вынесены в {@link ChatStreamService};
 * контроллер не должен зависеть от способа доставки токенов.
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

    /** Чат вместе с историей — для страницы чата. */
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

    /** История сообщений чата в порядке добавления. */
    @Transactional(readOnly = true)
    public List<ChatEntry> getHistory(Long chatId) {
        return getChat(chatId).getHistory();
    }

    /**
     * Добавляет сообщение в историю чата.
     * <p>
     * Вызывается только из других бинов ({@link ChatStreamService}), поэтому прокси Spring
     * корректно открывает транзакцию — вызов изнутри этого класса {@code @Transactional} бы проигнорировал.
     */
    @Transactional
    public void addChatEntry(Long chatId, String content, Role role) {
        Chat chat = chatRepository.findById(chatId).orElseThrow(() -> chatNotFound(chatId));
        chat.addChatEntry(ChatEntry.builder().content(content).role(role).build());
        log.debug("В чат id={} добавлено сообщение роли={}, длина={} символов", chatId, role, content.length());
    }

    private IllegalArgumentException chatNotFound(Long chatId) {
        log.warn("Чат с id={} не найден", chatId);
        return new IllegalArgumentException(CHAT_NOT_FOUND.formatted(chatId));
    }

}