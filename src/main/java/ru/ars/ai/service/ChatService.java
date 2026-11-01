package ru.ars.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import ru.ars.ai.model.Chat;
import ru.ars.ai.model.ChatEntry;
import ru.ars.ai.model.Role;
import ru.ars.ai.repository.ChatRepository;

import java.util.List;

import static ru.ars.ai.model.Role.ASSISTANT;
import static ru.ars.ai.model.Role.USER;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;

    private final ChatClient chatClient;

    public List<Chat> getAllChats() {
        List<Chat> chats = chatRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        log.debug("Получен список чатов, количество={}", chats.size());
        return chats;
    }

    public Chat getChat(Long chatId) {
        log.debug("Поиск чата по id={}", chatId);
        return chatRepository.findById(chatId).orElseThrow(() -> {
            log.warn("Чат с id={} не найден", chatId);
            return new IllegalArgumentException("Чат с id=%d не найден".formatted(chatId));
        });
    }

    public Chat createNewChat(String title) {
        log.info("Создание нового чата: '{}'", title);
        Chat chat = Chat.builder().title(title).build();
        Chat saved = chatRepository.save(chat);
        log.info("Чат создан, id={}", saved.getId());
        return saved;
    }

    public void deleteChat(Long chatId) {
        log.info("Удаление чата id={}", chatId);
        chatRepository.deleteById(chatId);
        log.info("Чат id={} удалён", chatId);
    }

    @Transactional
    public void addChatEntry(Long chatId, String prompt, Role role) {
        log.debug("Добавление записи в чат id={}, роль={}, длина={} символов", chatId, role, prompt.length());
        Chat chat = chatRepository.findById(chatId).orElseThrow(() -> {
            log.warn("Не удалось добавить запись: чат id={} не найден", chatId);
            return new IllegalArgumentException("Чат с id=%d не найден".formatted(chatId));
        });
        chat.addChatEntry(ChatEntry.builder().content(prompt).role(role).build());
        chatRepository.save(chat);
    }

    public Flux<String> proceedInteractionStream(Long chatId, String prompt) {
        log.info("Обработка запроса (streaming): chatId={}, длина промпта={} символов", chatId, prompt.length());
        this.addChatEntry(chatId, prompt, USER);
        long start = System.currentTimeMillis();
        StringBuilder fullAnswer = new StringBuilder();
        return chatClient.prompt().user(prompt).stream().content()
                .doOnNext(fullAnswer::append)
                .doOnComplete(() -> {
                    long duration = System.currentTimeMillis() - start;
                    log.info("Ответ получен (streaming): chatId={}, длительность={} мс, длина ответа={} символов",
                            chatId, duration, fullAnswer.length());
                    this.addChatEntry(chatId, fullAnswer.toString(), ASSISTANT);
                })
                .doOnError(e -> log.error("Ошибка при обращении к модели (chatId={}): {}", chatId, e.getMessage(), e));
    }

}