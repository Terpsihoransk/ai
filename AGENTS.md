# AGENTS.md

Руководство для AI-агентов (Claude, Cursor, Kilo Code, Copilot и т.п.), работающих с этим репозиторием. 
Задача документа — дать агенту достаточно контекста, чтобы вносить изменения, **не ломая архитектурные договорённости проекта**, 
и общаться с пользователем на правильном языке.

---

## 1. Коротко о проекте

- **Что это:** веб-чат с локальной LLM (Ollama) поверх Spring Boot 4 + Spring AI, с историей диалогов в PostgreSQL (pgvector) и SSE-стримингом ответов в браузер.
- **Полный обзор:** см. [`README.md`](README.md).
- **Точка входа:** [`AiApplication`](src/main/java/ru/ars/ai/AiApplication.java).
- **Единственная страница UI:** [`chat.html`](src/main/resources/templates/chat.html) + [`chat.js`](src/main/resources/static/js/chat.js).

## 2. Язык общения

- **Отвечай пользователю на русском.** Все комментарии в коде, логи, сообщения об ошибках, javadoc — тоже на русском (так уже написан весь проект; не ломай стиль).
- Идентификаторы (классы, методы, переменные) — на английском, как принято в Java.
- Строки для пользователя (titles, placeholder, шаблоны) — на русском.

## 3. Технологии и версии (не меняй без необходимости)

| Что              | Версия / значение                                        |
|------------------|-----------------------------------------------------------|
| Java             | **25** (`pom.xml → <java.version>25</java.version>`)      |
| Spring Boot      | **4.1.2**                                                 |
| Spring AI        | **2.0.2** (BOM)                                           |
| Lombok           | подключён, аннотации используются повсеместно             |
| LLM              | Ollama, модель `gemma3:4b-it-q4_K_M`                      |
| БД               | PostgreSQL 15+ с pgvector (`ankane/pgvector:v0.5.0`)      |
| Шаблонизатор     | Thymeleaf                                                 |
| JS-рендер MD     | `marked.js` (CDN, подключён в `chat.html`)                |

Если потребуется обновить версию — спроси подтверждение у пользователя.

## 4. Архитектурные принципы (нарушать не стоит)

1. **История чата хранится только в собственных таблицах** (`Chat` / `ChatEntry`). Spring AI пишет и читает её через нашу 
реализацию [`PostgresChatMemory`](src/main/java/ru/ars/ai/memory/PostgresChatMemory.java), подключённую к `ChatClient` через `MessageChatMemoryAdvisor` в [`AiApplication`](src/main/java/ru/ars/ai/AiApplication.java). 
**Не вводи** таблицы `SPRING_AI_CHAT_MEMORY_*` и не собирай историю руками в сервисах.
2. **Разделение ответственности:**
   - [`ChatController`](src/main/java/ru/ars/ai/controller/ChatController.java) — только HTTP;
   - [`ChatService`](src/main/java/ru/ars/ai/service/ChatService.java) — CRUD чатов для UI;
   - [`ChatStreamService`](src/main/java/ru/ars/ai/service/ChatStreamService.java) — вызов модели, **ничего не знает про HTTP/SSE**, возвращает `Flux<String>`;
   - [`SseStreamBridge`](src/main/java/ru/ars/ai/util/SseStreamBridge.java) — конверсия `Flux<String>` в `SseEmitter`.
3. **Промпт передаётся телом POST**, а не в query string: Tomcat по умолчанию режет заголовки на 8 КБ. См. комментарий в [`ChatController.streamChat()`](src/main/java/ru/ars/ai/controller/ChatController.java:63).
4. **SSE-токены сериализуются как JSON-строки** (см. [`SseUtils.toJsonString()`](src/main/java/ru/ars/ai/util/SseUtils.java:16)) — иначе SSE съедает ведущие пробелы. На фронте делается `JSON.parse`.
5. **Завершение стрима сигнализируется событием `event: done`.** Клиент (`chat.js`) ждёт его и, если не дождался, приписывает «_(ответ прерван)_».
6. **Транзакции на `PostgresChatMemory`** обязательны: советник вызывает методы на `Schedulers.boundedElastic()`, 
и ленивая `Chat.history` не инициализируется без транзакции. Переопределение дефолтного метода `add(id, message)` 
сделано именно поэтому — чтобы вызов шёл через Spring-прокси.
7. **Chat/ChatEntry — лениво**. Список чатов для сайдбара берётся через проекцию [`ChatSummary`](src/main/java/ru/ars/ai/repository/projection/ChatSummary.java); страница 
чата — через `findWithHistoryById` с `@EntityGraph`. Не добавляй `FetchType.EAGER` на `history`.
8. **Роль ↔ Message** живёт в [`Role`](src/main/java/ru/ars/ai/model/Role.java). Не пиши `switch` по типу сообщения вне этого enum.
9. **Markdown в ответах рендерится на клиенте** (`marked.parse`). На сервере хранится сырой текст, в шаблоне 
используется `th:text` (безопасный escape), а JS уже парсит Markdown — и для стрима, и для истории (`renderHistoryMarkdown`).

## 5. Правила работы с кодом

### Общие

- **Lombok везде**: `@RequiredArgsConstructor`, `@Slf4j`, `@Getter`/`@Setter`, `@Builder`. Не пиши конструкторы и геттеры вручную.
- **Логгер — `log` из `@Slf4j`.** Логи — на русском, с указанием значимых параметров (id чата, длина промпта и т.п.). Используй уровни:
  - `INFO` — бизнес-события (создание/удаление чата, успешный ответ);
  - `DEBUG` — технические подробности (длины, вспомогательные состояния);
  - `WARN` — ожидаемые проблемы (прерывание клиентом, не найденный чат);
  - `ERROR` — исключения.
- **Длинные логи** всё равно обрежутся [`TruncatedMessageConverter`](src/main/java/ru/ars/ai/logging/TruncatedMessageConverter.java) до 500 символов — это нормально, не удивляйся.
- **Исключения:** бросай осмысленные `IllegalArgumentException`/`IllegalStateException` с русскими сообщениями, использующими `.formatted(...)`.
- **Nullable / Optional:** репозиторные методы, которые могут не найти сущность, возвращают `Optional<Chat>` и обрабатываются `.orElseThrow(...)`.

### Правки файлов

- Используй инструмент `fast_edit_file` (если доступен) для изменений — так требует среда.
- **Все правки одного файла — одним вызовом.**
- Не трогай форматирование соседних участков без необходимости.

### Миграции схемы

- Пока используется `spring.jpa.hibernate.ddl-auto=update` — Hibernate сам синхронизирует схему. Flyway/Liquibase не подключены. 
**Если ломаешь обратную совместимость схемы — предупреди пользователя** и предложи SQL для существующих БД.

### JPA

- Entity-классы помечены Lombok-аннотациями + `@Entity`. Не забывай про `@NoArgsConstructor` для JPA.
- Не используй `EAGER` без явной причины. Если нужен eager-подобный доступ — используй `@EntityGraph` в репозитории.
- Для `List<ChatEntry>` в `Chat` оставлена join-таблица (из исторических соображений — чтобы старые сообщения не потерялись). 
Не переводи на `@ManyToOne`-ссылку без миграции данных.

### Фронтенд (`chat.js`, `chat.html`, `chat.css`)

- JS — **без фреймворков**, ванильный. Так и оставляй.
- При изменении `chat.js` **увеличивай параметр `v=` в `chat.html`** (`th:src="@{/js/chat.js(v=6)}"`) — это ручной cache-buster.
- `textContent` для пользовательского ввода, `innerHTML = marked.parse(...)` — только для ответа модели (которую мы сами стримим). Не меняй местами.

## 6. Запуск и проверка

Перед «готово» убедись, что:

1. Проект собирается: `./mvnw clean package` (Linux/macOS) или `mvnw.cmd clean package` (Windows).
2. Тесты зелёные: `./mvnw test` (сейчас это только контекст-тест [`AiApplicationTests`](src/test/java/ru/ars/ai/AiApplicationTests.java)).
3. Для ручной проверки нужны поднятые контейнеры: `docker compose up -d` → приложение на `http://localhost:8081/`.

Если ты агент без доступа к оболочке/докеру — хотя бы прогони компиляцию и логически проверь изменения.

## 7. Чего НЕ делать

- ❌ Не добавляй параллельное хранение истории через Spring AI (JDBC/InMemory `ChatMemoryRepository`) — есть `PostgresChatMemory`.
- ❌ Не передавай промпт в query string.
- ❌ Не отключай JSON-экранирование токенов в SSE.
- ❌ Не ставь `FetchType.EAGER` на `Chat.history`.
- ❌ Не добавляй `@Transactional` на контроллер — транзакции на уровне сервиса/мемори.
- ❌ Не вводи английский в комментарии/логи/javadoc, если рядом стоит русский стиль.
- ❌ Не подключай фронтенд-фреймворки (React/Vue) — проект осознанно простой.
- ❌ Не коммить секреты (пока их и нет: креды в `application.properties` — для локалки).

## 8. Полезные ссылки на код

- Конфигурация ChatClient с advisor'ом памяти: [`AiApplication.chatClient()`](src/main/java/ru/ars/ai/AiApplication.java:17)
- Стриминг ответа модели: [`ChatStreamService.streamAnswer()`](src/main/java/ru/ars/ai/service/ChatStreamService.java:33)
- Мост в SSE: [`SseStreamBridge.toEmitter()`](src/main/java/ru/ars/ai/util/SseStreamBridge.java:29)
- Чтение/запись истории чата: [`PostgresChatMemory`](src/main/java/ru/ars/ai/memory/PostgresChatMemory.java)
- Эндпоинт стрима: [`ChatController.streamChat()`](src/main/java/ru/ars/ai/controller/ChatController.java:63)
- Парсер SSE на клиенте: [`readEventStream()`](src/main/resources/static/js/chat.js:29)

## 9. Коммуникация с пользователем

- **Спрашивай** про неоднозначные вещи (апгрейд версий, схема БД, большие рефакторинги) до правок.
- **Отчитывайся** короткими техническими сообщениями без «воды». Запрещены приветствия вроде «Отлично!», «Конечно!» и подобные — пиши сразу по делу (это требование окружения).
- Никаких оценок времени (часы/дни/недели) — только список шагов.
