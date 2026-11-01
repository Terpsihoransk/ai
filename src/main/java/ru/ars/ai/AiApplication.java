package ru.ars.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
public class AiApplication {

	@Bean
	public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
		// Память чата подключается ко всем запросам; конкретный диалог выбирается параметром
		// ChatMemory.CONVERSATION_ID при вызове prompt().
		return builder
				.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
				.build();
	}

	static void main(String[] args) {
		log.info("Запуск приложения ai...");
		ConfigurableApplicationContext context = SpringApplication.run(AiApplication.class, args);
		log.info("Приложение ai успешно запущено. Активных бинов: {}", context.getBeanDefinitionCount());
	}

}
