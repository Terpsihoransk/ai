package ru.ars.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.ars.ai.model.ChatEntry;

import java.util.List;

import static ru.ars.ai.model.Role.ASSISTANT;
import static ru.ars.ai.model.Role.USER;

/**
 * Взаимодействие с моделью: построение запроса из истории чата и стриминг ответа.
 * Не знает ничего про HTTP/SSE — отдаёт «сырой» поток токенов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStreamService {

    private final ChatClient chatClient;

    private final ChatService chatService;

    /**
     * Сохраняет промпт, стримит ответ модели по истории чата и сохраняет ответ.
     * Возвращаемый поток завершается только после записи ответа в БД, поэтому клиент,
     * получивший финальное событие, может быть уверен, что ответ уже сохранён.
     *
     * @return поток токенов ответа модели
     */
    public Flux<String> streamAnswer(Long chatId, String prompt) {
        log.debug("Стриминг ответа: chatId={}, длина промпта={} символов", chatId, prompt.length());

        chatService.addChatEntry(chatId, prompt, USER);
        List<Message> messages = toMessages(chatService.getHistory(chatId));

        StringBuilder answer = new StringBuilder();
        long startedAt = System.nanoTime();

        return chatClient.prompt()
                .messages(messages)
                .stream()
                .content()
                // Сигналы onNext у Flux сериализованы для одного подписчика, поэтому StringBuilder безопасен.
                .doOnNext(answer::append)
                .concatWith(Flux.defer(() -> saveAnswer(chatId, answer, startedAt).thenMany(Flux.empty())))
                .doOnCancel(() -> log.warn("Стрим прерван клиентом, ответ не сохранён: chatId={}", chatId))
                .doOnError(e -> log.error("Ошибка стриминга ответа модели (chatId={}): {}", chatId, e.getMessage(), e));
    }

    private List<Message> toMessages(List<ChatEntry> history) {
        return history.stream().map(this::toMessage).toList();
    }

    private Message toMessage(ChatEntry entry) {
        return switch (entry.getRole()) {
            case USER -> new UserMessage(entry.getContent());
            case ASSISTANT -> new AssistantMessage(entry.getContent());
            case SYSTEM -> new SystemMessage(entry.getContent());
        };
    }

    /**
     * Сохранение ответа выполняется на {@link Schedulers#boundedElastic()}: блокирующий JDBC
     * нельзя запускать в потоке реактивного HTTP-клиента модели.
     * <p>
     * Исключение при сохранении логируется внутри и не превращается в onError — ответ уже
     * доставлен клиенту, и «падение» стрима после успешного ответа только запутало бы UI.
     */
    private Mono<Void> saveAnswer(Long chatId, StringBuilder answer, long startedAt) {
        String text = answer.toString();
        if (text.isBlank()) {
            log.warn("Модель вернула пустой ответ, запись не сохранена: chatId={}", chatId);
            return Mono.empty();
        }
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        return Mono.fromRunnable(() -> persistAnswer(chatId, text, durationMs))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void persistAnswer(Long chatId, String answer, long durationMs) {
        try {
            chatService.addChatEntry(chatId, answer, ASSISTANT);
            log.info("Ответ сохранён: chatId={}, длительность={} мс, длина={} символов",
                    chatId, durationMs, answer.length());
        } catch (RuntimeException e) {
            log.error("Не удалось сохранить ответ модели (chatId={}): {}", chatId, e.getMessage(), e);
        }
    }

}
