package ru.ars.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;


@RequiredArgsConstructor
public class AiApplication {

	private final ChatClient.Builder builder;

	@Bean
	public ChatClient chatClient(ChatClient.Builder builder) {
		return builder.build();
	}

	static void main(String[] args) {
		SpringApplication.run(AiApplication.class, args);
	}

}
