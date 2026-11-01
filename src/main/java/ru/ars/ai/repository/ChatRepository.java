package ru.ars.ai.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.ars.ai.model.Chat;
import ru.ars.ai.repository.projection.ChatSummary;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    /**
     * Список чатов для сайдбара. Возвращает проекцию, поэтому история сообщений
     * (потенциально большая) не загружается вообще.
     */
    List<ChatSummary> findAllByOrderByCreatedAtDesc();

    /** Чат вместе с историей — для страницы чата, одним запросом вместо N+1. */
    @EntityGraph(attributePaths = "history")
    Optional<Chat> findWithHistoryById(Long id);
}
