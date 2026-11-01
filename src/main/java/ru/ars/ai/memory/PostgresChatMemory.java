package ru.ars.ai.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.ars.ai.model.Chat;
import ru.ars.ai.model.ChatEntry;
import ru.ars.ai.repository.ChatRepository;

import java.util.Comparator;
import java.util.List;

/**
 * Реализация {@link ChatMemory} поверх собственных таблиц приложения ({@code Chat}/{@code ChatEntry}).
 * <p>
 * Благодаря этому {@code MessageChatMemoryAdvisor} читает и пишет историю в те же таблицы, что
 * отображает UI: история не дублируется между Spring AI и приложением (в проекте нет таблиц
 * {@code SPRING_AI_CHAT_MEMORY}).
 * <p>
 * Advisor вызывает методы на {@code Schedulers.boundedElastic()} (значение
 * {@code BaseAdvisor.DEFAULT_SCHEDULER}), поэтому блокирующий JDBC не попадает в event-loop
 * реактивного клиента модели. Методы объявлены {@link Transactional}, чтобы ленивая коллекция
 * {@link Chat#getHistory()} инициализировалась внутри транзакции.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostgresChatMemory implements ChatMemory {

    private final ChatRepository chatRepository;

    /** Сколько последних сообщений отдавать модели в качестве контекста. */
    @Value("${chat.memory.max-messages:20}")
    private int maxMessages;

    /**
     * Переопределяем default-метод интерфейса, чтобы вызов из advisor'а гарантированно проходил
     * через прокси с транзакцией (иначе ленивая история чата не загрузится).
     */
    @Override
    @Transactional
    public void add(String conversationId, Message message) {
        add(conversationId, List.of(message));
    }

    @Override
    @Transactional
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        Chat chat = loadChat(conversationId);
        messages.stream().map(ChatEntry::toChatEntry).forEach(chat::addChatEntry);
        chatRepository.save(chat);
        log.debug("В память чата id={} добавлено сообщений: {}", conversationId, messages.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> get(String conversationId) {
        List<Message> messages = loadChat(conversationId).getHistory().stream()
                .sorted(Comparator.comparing(ChatEntry::getId))
                .map(ChatEntry::toMessage)
                .toList();
        // Скользящее окно: модели нужны самые свежие сообщения, а не первые.
        int from = Math.max(0, messages.size() - maxMessages);
        return messages.subList(from, messages.size());
    }

    @Override
    @Transactional
    public void clear(String conversationId) {
        Chat chat = loadChat(conversationId);
        chat.getHistory().clear();
        chatRepository.save(chat);
        log.debug("История чата id={} очищена", conversationId);
    }

    private Chat loadChat(String conversationId) {
        Long chatId = Long.valueOf(conversationId);
        return chatRepository.findById(chatId).orElseThrow(
                () -> new IllegalArgumentException("Чат с id=%d не найден".formatted(chatId)));
    }

}
