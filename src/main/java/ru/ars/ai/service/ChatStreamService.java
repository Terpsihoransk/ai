package ru.ars.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Взаимодействие с моделью: стриминг ответа.
 * <p>
 * Историю чата сервис больше не собирает вручную: ею управляет {@code MessageChatMemoryAdvisor}
 * (зарегистрирован на {@link ChatClient}), который читает и пишет её через
 * {@link ru.ars.ai.memory.PostgresChatMemory} в таблицы {@code Chat}/{@code ChatEntry}.
 * Сервису достаточно передать id чата как {@link ChatMemory#CONVERSATION_ID}.
 * Не знает ничего про HTTP/SSE — отдаёт «сырой» поток токенов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStreamService {

    private final ChatClient chatClient;

    /**
     * Стримит ответ модели по истории чата. Промпт и ответ сохраняются advisor'ом в рамках
     * того же стрима: пользовательское сообщение — перед вызовом модели, ответ модели — по
     * завершении потока.
     *
     * @return поток токенов ответа модели
     */
    public Flux<String> streamAnswer(Long chatId, String prompt) {
        log.debug("Стриминг ответа: chatId={}, длина промпта={} символов", chatId, prompt.length());

        StringBuilder answer = new StringBuilder();
        long startedAt = System.nanoTime();

        return chatClient.prompt()
                .user(prompt)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content()
                // Сигналы onNext у Flux сериализованы для одного подписчика, поэтому StringBuilder безопасен.
                .doOnNext(answer::append)
                .doOnComplete(() -> log.info("Ответ получен: chatId={}, длительность={} мс, длина={} символов",
                        chatId, (System.nanoTime() - startedAt) / 1_000_000, answer.length()))
                .doOnCancel(() -> log.warn("Стрим прерван клиентом: chatId={}", chatId))
                .doOnError(e -> log.error("Ошибка стриминга ответа модели (chatId={}): {}", chatId, e.getMessage(), e));
    }

}
