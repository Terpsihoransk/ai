package ru.ars.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import ru.ars.ai.memory.PostgresChatMemory;
import ru.ars.ai.repository.ChatRepository;

@Slf4j
@SpringBootApplication
public class AiApplication {

	@Value("${chat.memory.max-messages:12}")
	private int maxMessages;

	@Bean
	public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
		// Память чата подключается ко всем запросам; конкретный диалог выбирается параметром
		// ChatMemory.CONVERSATION_ID при вызове prompt().
		return builder
				.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
				.build();
	}

	/**
	 * Память чата поверх собственных таблиц {@code Chat}/{@code ChatEntry}.
	 * <p>
	 * Обязательно объявляется через {@code @Bean}: только Spring-бин получает прокси с
	 * {@code @Transactional}, без которого ленивая история чата не инициализируется.
	 * Размер окна контекста настраивается свойством {@code chat.memory.max-messages}.
	 */
	@Bean
	public ChatMemory chatMemory(ChatRepository chatRepository) {
		return PostgresChatMemory.builder()
				.maxMessages(maxMessages)
				.chatRepository(chatRepository)
				.build();
	}

	static void main(String[] args) {
		log.info("Запуск приложения ai...");
		ConfigurableApplicationContext context = SpringApplication.run(AiApplication.class, args);
		log.info("Приложение ai успешно запущено. Активных бинов: {}", context.getBeanDefinitionCount());
	}

}
