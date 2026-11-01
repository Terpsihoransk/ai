package ru.ars.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.ars.ai.model.Chat;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {
}
