package ru.ars.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "rag.document-loader.enabled=false")
class AiApplicationTests {

	@Test
	void contextLoads() {
	}

}