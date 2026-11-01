package ru.ars.ai.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import ru.ars.ai.model.LoadedDocument;
import ru.ars.ai.repository.DocumentRepository;

import java.util.Arrays;
import java.util.List;

/**
 * Загружает текстовые документы из каталога {@code classpath:/knowledgebase} в векторное хранилище (RAG).
 * <p>
 * Сервис реализует {@link CommandLineRunner}, поэтому запускается автоматически при старте приложения,
 * если включён флаг {@code rag.document-loader.enabled} (по умолчанию — включён).
 * <p>
 * Алгоритм работы:
 * <ol>
 *     <li>проверяет наличие каталога {@code classpath:/knowledgebase};</li>
 *     <li>находит все файлы {@code .txt} (включая вложенные каталоги);</li>
 *     <li>вычисляет MD5-хеш содержимого каждого файла и пропускает уже загруженные неизменённые документы;</li>
 *     <li>разбивает текст на чанки ({@link TokenTextSplitter}, размер чанка 500 токенов) и сохраняет их в {@link VectorStore};</li>
 *     <li>фиксирует факт загрузки в таблице через {@link DocumentRepository} (модель {@link LoadedDocument}).</li>
 * </ol>
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "rag.document-loader", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DocumentLoaderService implements CommandLineRunner {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ResourcePatternResolver resolver;

    @Autowired
    private VectorStore vectorStore;


    @SneakyThrows
    public void loadDocuments() {
        if (!resolver.getResource("classpath:/knowledgebase").exists()) {
            log.warn("Каталог classpath:/knowledgebase не найден — загрузка документов пропущена");
            return;
        }

        List<Resource> resources = Arrays.stream(resolver.getResources("classpath:/knowledgebase/**/*.txt")).toList();
        if (resources.isEmpty()) {
            log.info("Каталог knowledgebase не содержит .txt — загружать нечего");
            return;
        }

        resources.stream()
                .map(resource -> Pair.of(resource, calcContentHash(resource)))
                // Если документ есть и не менялся, не трогаем
                .filter(pair -> !documentRepository.existsByFilenameAndContentHash(pair.getFirst().getFilename(), pair.getSecond()))
                .forEach(pair -> {
                    Resource resource = pair.getFirst();
                    List<Document> documents = new TextReader(resource).get();
                    TokenTextSplitter textSplitter = TokenTextSplitter.builder().withChunkSize(500).build();
                    List<Document> chunks = textSplitter.apply(documents);
                    vectorStore.accept(chunks);

                    LoadedDocument loadedDocument = LoadedDocument.builder()
                            .documentType("txt")
                            .chunkCount(chunks.size())
                            .filename(resource.getFilename())
                            .contentHash(pair.getSecond())
                            .build();
                    documentRepository.save(loadedDocument);

                });
    }

    @SneakyThrows
    private String calcContentHash(Resource resource) {
        return DigestUtils.md5DigestAsHex(resource.getInputStream());
    }

    @Override
    public void run(String... args) {
        loadDocuments();
    }
}
