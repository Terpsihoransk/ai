package ru.ars.ai.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Пропускает SQL-логи привязки параметров, скрывая шумные привязки BIGINT (идентификаторы),
 * но оставляя содержательные значения (VARCHAR, TIMESTAMP и т.п.).
 */
public class HideBigIntJdbcLogFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message != null && message.contains("BIGINT")) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
