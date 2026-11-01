package ru.ars.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class AiApplication {

	private final ChatClient.Builder builder;

	@Bean
	public ChatClient chatClient(ChatClient.Builder builder) {
		return builder.build();
	}

	static void main(String[] args) {
		log.info("Запуск приложения ai...");
		ConfigurableApplicationContext context = SpringApplication.run(AiApplication.class, args);
		log.info("Приложение ai успешно запущено. Активных бинов: {}", context.getBeanDefinitionCount());
//		ChatClient chatClient = context.getBean(ChatClient.class);
//		System.out.println(chatClient.prompt().user("Дай первую строчку песни Арии Штиль").call().content());
	}

}
