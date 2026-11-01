package ru.ars.ai.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;

import java.io.IOException;

/**
 * Мост из реактивного потока токенов в {@link SseEmitter} для Spring MVC.
 * <p>
 * MVC не умеет напрямую отдавать {@code Flux<ServerSentEvent>} из Thymeleaf-контроллера,
 * поэтому адаптер подписывается на поток сам и корректно снимает подписку, когда клиент
 * закрыл соединение — модель не продолжает генерировать токены «в никуда».
 */
@Slf4j
@Component
public class SseStreamBridge {

    /** Таймаут SSE-соединения, мс. */
    private static final long SSE_TIMEOUT_MS = 300_000L;

    /** Имя события, сигнализирующего клиенту о завершении стрима. */
    private static final String DONE_EVENT = "done";

    public SseEmitter toEmitter(Flux<String> tokens) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // Swap позволяет отменить подписку из колбэка, даже если он сработал раньше,
        // чем subscribe() вернул Disposable.
        Disposable.Swap subscription = Disposables.swap();
        subscription.update(tokens
                .map(SseUtils::toJsonString)
                .subscribe(
                        token -> sendToken(emitter, subscription, token),
                        error -> fail(emitter, subscription, error),
                        () -> complete(emitter)
                ));
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(() -> {
            log.warn("SSE-соединение закрыто по таймауту {} мс", SSE_TIMEOUT_MS);
            subscription.dispose();
        });
        return emitter;
    }

    private void sendToken(SseEmitter emitter, Disposable subscription, String token) {
        try {
            emitter.send(SseEmitter.event().data(token));
        } catch (IOException | IllegalStateException e) {
            log.warn("Клиент закрыл соединение, стрим прерван: {}", e.getMessage());
            subscription.dispose();
            emitter.completeWithError(e);
        }
    }

    private void fail(SseEmitter emitter, Disposable subscription, Throwable error) {
        log.error("Ошибка стриминга ответа модели: {}", error.getMessage(), error);
        subscription.dispose();
        emitter.completeWithError(error);
    }

    private void complete(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name(DONE_EVENT).data(""));
        } catch (IOException | IllegalStateException e) {
            log.debug("Соединение закрыто до отправки завершающего события: {}", e.getMessage());
        }
        emitter.complete();
    }

}
