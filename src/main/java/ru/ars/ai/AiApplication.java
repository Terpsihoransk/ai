package ru.ars.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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

	/**
	 * Память чата должна обрабатываться раньше RAG-поиска, поэтому её порядок меньше.
	 * Advisor'ы сортируются по возрастанию order.
	 */
	private static final int HISTORY_ADVISOR_ORDER = -10;
	private static final int RAG_ADVISOR_ORDER = 0;

	/**
	 * Шаблон RAG-советника. Плейсхолдеры {@code {query}} и {@code {question_answer_context}}
	 * подставляет сам {@link QuestionAnswerAdvisor}: первый — из вопроса пользователя,
	 * второй — из найденных в векторном хранилище чанков.
	 * <p>
	 * Формулировка намеренно мягкая: модель не обязана отказываться от ответа, если в
	 * knowledgebase не нашлось подходящих документов — она отвечает по своим знаниям.
	 */
	private static final PromptTemplate RAG_PROMPT_TEMPLATE = new PromptTemplate("""
			{query}

			Контекст:
			---------------------
			{question_answer_context}
			---------------------

			Если в контексте выше есть ответ на вопрос — отвечай на его основе.
			Если подходящей информации в контексте нет — ответь самостоятельно, опираясь на свои знания.
			""");

	@Value("${chat.memory.max-messages:12}")
	private int maxMessages;

	@Value("${rag.advisor.top-k:4}")
	private int ragTopK;

	@Bean
	public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory, VectorStore vectorStore) {
		// Память чата подключается ко всем запросам; конкретный диалог выбирается параметром
		// ChatMemory.CONVERSATION_ID при вызове prompt().
		// RAG-советник дополняет запрос чанками из vector_store (наполняется DocumentLoaderService).
		return builder
				.defaultAdvisors(
						MessageChatMemoryAdvisor.builder(chatMemory).order(HISTORY_ADVISOR_ORDER).build(),
						QuestionAnswerAdvisor.builder(vectorStore)
								.promptTemplate(RAG_PROMPT_TEMPLATE)
								.searchRequest(SearchRequest.builder().topK(ragTopK).build())
								.order(RAG_ADVISOR_ORDER)
								.build())
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