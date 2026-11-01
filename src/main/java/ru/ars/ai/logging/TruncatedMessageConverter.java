package ru.ars.ai.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback-конвертер для слова {@code %msg}: обрезает слишком длинные сообщения.
 * <p>
 * Тела запроса и ответа модели при DEBUG-логировании Spring AI занимают десятки килобайт,
 * из-за чего полезные строки теряются в потоке. Логируем первые {@value #MAX_LENGTH} символов
 * и указываем исходную длину.
 */
public class TruncatedMessageConverter extends MessageConverter {

    private static final int MAX_LENGTH = 500;

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        if (message == null || message.length() <= MAX_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_LENGTH)
                + "... [обрезано, всего " + message.length() + " символов]";
    }
}
