# Ars AI

Небольшое веб-приложение на **Spring Boot 4** + **Spring AI**, которое:

- общается с локальной LLM через [Ollama](https://ollama.com/) (`gemma3:4b-it-q4_K_M`);
- хранит историю диалогов в **PostgreSQL** (с расширением **pgvector**);
- отдаёт ответы токенами через **Server-Sent Events (SSE)**;
- рендерит простой UI на **Thymeleaf** с Markdown-разметкой ответов.

---

## Содержание

- [Технологический стек](#технологический-стек)
- [Архитектура](#архитектура)
- [Требования](#требования)
- [Быстрый запуск](#быстрый-запуск)
- [Конфигурация](#конфигурация)
- [HTTP API](#http-api)
- [Структура проекта](#структура-проекта)
- [Логирование](#логирование)

---

## Технологический стек

| Слой            | Технология                                                   |
|-----------------|--------------------------------------------------------------|
| Язык            | Java **25**                                                  |
| Фреймворк       | Spring Boot **4.1.2**                                        |
| LLM-интеграция  | Spring AI **2.0.2** (`spring-ai-starter-model-ollama`)       |
| Vector store    | pgvector (`spring-ai-starter-vector-store-pgvector`)         |
| БД              | PostgreSQL 15+ с расширением `pgvector` (образ `ankane/pgvector`) |
| ORM             | Spring Data JPA / Hibernate                                  |
| UI              | Thymeleaf + ванильный JS + [marked.js](https://marked.js.org/) |
| Стриминг        | Spring MVC `SseEmitter` ← мост из `Flux<String>`             |
| Сборка          | Maven (wrapper в `./mvnw`)                                   |
| Lombok          | подключён для сокращения boilerplate                         |

## Архитектура

```
┌────────────┐      POST /chat-stream/{id}      ┌──────────────────────┐
│  Browser   │  ─────────────────────────────▶  │   ChatController     │
│ (chat.js)  │  ◀────── SSE: data / done ─────  │                      │
└────────────┘                                  └────────┬─────────────┘
                                                         │
                                                         ▼
                                             ┌──────────────────────┐
                                             │ ChatStreamService    │
                                             │  → ChatClient.prompt │
                                             └────────┬─────────────┘
                                                      │
             ┌────────────────────────────────────────┤
             ▼                                        ▼
   ┌──────────────────┐                   ┌─────────────────────────┐
   │ MessageChatMemory│ (advisor Spring AI)│   Ollama (localhost)   │
   │     Advisor      │                   │  gemma3:4b-it-q4_K_M    │
   └────────┬─────────┘                   └─────────────────────────┘
            │ read / write
            ▼
   ┌──────────────────┐                   ┌─────────────────────────┐
   │PostgresChatMemory│ ───── JPA ─────▶  │  PostgreSQL + pgvector  │
   │ (ChatMemory impl)│                   │  (Chat / ChatEntry)     │
   └──────────────────┘                   └─────────────────────────┘
```

Ключевые идеи:

- **История не дублируется** между Spring AI и приложением. Собственная реализация [`PostgresChatMemory`](src/main/java/ru/ars/ai/memory/PostgresChatMemory.java) пишет и читает те же таблицы `Chat`/`ChatEntry`, что отображает UI.
- **Стриминг**: [`ChatStreamService`](src/main/java/ru/ars/ai/service/ChatStreamService.java) отдаёт сырой `Flux<String>`, а [`SseStreamBridge`](src/main/java/ru/ars/ai/util/SseStreamBridge.java) конвертирует его в `SseEmitter`, корректно отменяя подписку при разрыве клиента.
- **Промпт в теле POST**, а не в query string: длинный пользовательский ввод не влезает в лимит заголовков Tomcat (8 КБ по умолчанию).
- **Markdown** в ответах рендерится на фронте через `marked.js` — и для стриминговых токенов, и для истории.

## Требования

- **JDK 25** (обязательно: в `pom.xml` зафиксирован `java.version=25`).
- **Maven 3.9+** либо встроенный wrapper (`./mvnw`, `mvnw.cmd`).
- **Docker** и **Docker Compose** — для PostgreSQL + Ollama одной командой.
- Либо, если без Docker: PostgreSQL 15+ с установленным `pgvector` и локально запущенный Ollama.

## Быстрый запуск

### 1. Поднять инфраструктуру (PostgreSQL + Ollama)

```bash
docker compose up -d
```

Что произойдёт:

- запустится **PostgreSQL** на хосте `localhost:5433` (БД `ragdb`, пользователь/пароль `postgres/postgres`);
- запустится **Ollama** на `localhost:11431`, автоматически подтянутся модели `gemma3:4b-it-q4_K_M` и `mxbai-embed-large`.

> ⚠️ Первый запуск скачивает несколько гигабайт моделей — наберитесь терпения. Прогресс виден в `docker compose logs -f ollama`.

### 2. Запустить приложение

Windows:

```cmd
mvnw.cmd spring-boot:run
```

Linux / macOS:

```bash
./mvnw spring-boot:run
```

Или собрать `jar` и запустить:

```bash
./mvnw clean package
java -jar target/ai-0.0.1-SNAPSHOT.jar
```

### 3. Открыть UI

[http://localhost:8081/](http://localhost:8081/)

- Введи название нового чата → **BOOM! Новый чат**.
- Пиши сообщение, жми **SEND** (или `Enter`; `Shift+Enter` — перенос строки).
- Ответ стримится токенами и отображается с Markdown.

## Конфигурация

Все настройки — в [`application.properties`](src/main/resources/application.properties). Самое важное:

| Свойство                             | Значение по умолчанию                | Назначение                                                      |
|--------------------------------------|--------------------------------------|------------------------------------------------------------------|
| `server.port`                        | `8081`                               | Порт веб-приложения                                              |
| `server.max-http-request-header-size`| `64KB`                               | Расширенный лимит заголовков (на случай больших cookies)         |
| `spring.ai.ollama.base-url`          | `http://localhost:11431`             | URL Ollama (совпадает с портом из `docker-compose.yml`)          |
| `spring.ai.ollama.chat.model`        | `gemma3:4b-it-q4_K_M`                | Модель для чата                                                  |
| `spring.datasource.url`              | `jdbc:postgresql://localhost:5433/ragdb` | Подключение к PostgreSQL                                     |
| `spring.datasource.username` / `password` | `postgres` / `postgres`         | Креденшилы БД                                                    |
| `spring.jpa.hibernate.ddl-auto`      | `update`                             | Hibernate сам создаёт/мигрирует схему                            |
| `chat.memory.max-messages`           | `20` (значение из кода)              | Размер скользящего окна истории, отдаваемой модели               |

> Для продакшена `ddl-auto=update` и хардкод кредов — не вариант. Для локалки удобно.

## HTTP API

| Метод | Путь                        | Назначение                                                   |
|-------|-----------------------------|--------------------------------------------------------------|
| GET   | `/`                         | Главная: список чатов (левый сайдбар)                        |
| GET   | `/chat/{chatId}`            | Страница конкретного чата с историей                         |
| POST  | `/chat/new?title=...`       | Создать новый чат (`application/x-www-form-urlencoded`)      |
| POST  | `/chat/{chatId}/delete`     | Удалить чат                                                  |
| POST  | `/chat-stream/{chatId}`     | **SSE-стрим ответа модели.** Тело — `text/plain` с промптом. |

Формат SSE-событий:

```
data: "фрагмент токена"

data: " следующий фрагмент"

event: done
data: 
```

Каждый `data` — это JSON-строка (чтобы SSE не терял ведущие пробелы). Клиент декодирует её через `JSON.parse` и дописывает в текущий «пузырь» ответа.

## Структура проекта

```
src/main/java/ru/ars/ai/
├── AiApplication.java            # Точка входа + бин ChatClient с MessageChatMemoryAdvisor
├── controller/
│   └── ChatController.java       # Thymeleaf-страницы + SSE-эндпоинт
├── service/
│   ├── ChatService.java          # CRUD чатов для UI
│   └── ChatStreamService.java    # Flux<String> от модели (через ChatClient)
├── memory/
│   └── PostgresChatMemory.java   # ChatMemory поверх таблиц приложения
├── model/
│   ├── Chat.java                 # Чат (id, title, createdAt, history)
│   ├── ChatEntry.java            # Сообщение (content, role, startedAt)
│   └── Role.java                 # USER / ASSISTANT / SYSTEM + конвертация Message↔Role
├── repository/
│   ├── ChatRepository.java       # JpaRepository + findWithHistoryById (EntityGraph)
│   └── projection/ChatSummary.java  # Проекция для сайдбара (без истории)
├── util/
│   ├── SseStreamBridge.java      # Мост Flux<String> → SseEmitter
│   └── SseUtils.java             # JSON-экранирование токенов
└── logging/
    ├── TruncatedMessageConverter.java   # Обрезает длинные сообщения в логах
    └── HideBigIntJdbcLogFilter.java     # Прячет шумные привязки BIGINT в SQL

src/main/resources/
├── application.properties
├── logback-spring.xml            # Единый формат, фильтры SQL
├── static/
│   ├── css/chat.css              # Comic-style UI
│   ├── js/chat.js                # Fetch + ручной парсер SSE + marked.js
│   └── images/{user,mentor}.png
└── templates/chat.html           # Единственная страница (сайдбар + лента)
```

## Логирование

- Формат: `yyyy-MM-dd HH:mm:ss LEVEL [thread] logger - message`.
- `ru.ars.ai` на уровне `DEBUG`, корень — `INFO`.
- Spring AI на `DEBUG`, но `org.springframework.ai.chat.metadata` подавлен до `INFO` (убирает шум «Ignore null value for key …»).
- Сообщения длиннее **500 символов** автоматически обрезаются конвертером [`TruncatedMessageConverter`](src/main/java/ru/ars/ai/logging/TruncatedMessageConverter.java), чтобы тела промптов/ответов не забивали консоль.
- Привязки параметров `BIGINT` (как правило, id) скрыты фильтром [`HideBigIntJdbcLogFilter`](src/main/java/ru/ars/ai/logging/HideBigIntJdbcLogFilter.java) — остаются полезные `VARCHAR`/`TIMESTAMP`.

---

## Остановка инфраструктуры

```bash
docker compose down          # остановить контейнеры
docker compose down -v       # и удалить volume с БД
```
