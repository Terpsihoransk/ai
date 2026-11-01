document.addEventListener("DOMContentLoaded", function() {
    const sendButton = document.getElementById("send-button");
    const chatInput = document.getElementById("chat-input");
    const messagesContainer = document.getElementById("messages");

    // Добавляет сообщение в ленту и возвращает "пузырь" для текста
    function appendMessage(cssClass, avatarSrc, avatarAlt, text) {
        const messageDiv = document.createElement("div");
        messageDiv.className = "message " + cssClass;

        const avatar = document.createElement("img");
        avatar.src = avatarSrc;
        avatar.alt = avatarAlt;

        const bubble = document.createElement("div");
        bubble.className = "bubble";
        // textContent, а не innerHTML: текст пользователя не должен трактоваться как разметка
        bubble.textContent = text;

        messageDiv.appendChild(avatar);
        messageDiv.appendChild(bubble);
        messagesContainer.appendChild(messageDiv);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
        return bubble;
    }

    // Разбирает SSE-поток и отдаёт накопленный текст на каждый токен.
    // Возвращает текст ответа и признак того, что сервер прислал событие "done".
    async function readEventStream(body, onText) {
        const reader = body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";
        let fullText = "";
        let sawDone = false;

        while (true) {
            const { value, done } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });

            const events = buffer.split(/\r?\n\r?\n/);
            buffer = events.pop();

            for (const rawEvent of events) {
                const lines = rawEvent.split(/\r?\n/);
                const eventName = lines
                    .filter(line => line.startsWith("event:"))
                    .map(line => line.slice(6).trim())[0];
                if (eventName === "done") {
                    sawDone = true;
                    continue;
                }

                const data = lines
                    .filter(line => line.startsWith("data:"))
                    .map(line => line.slice(5).replace(/^ /, ""))
                    .join("\n");
                if (!data) continue;

                // Сервер присылает токен как JSON-строку, чтобы SSE не терял пробелы в начале данных
                let token;
                try {
                    token = JSON.parse(data);
                } catch (e) {
                    console.error("Некорректный токен SSE:", data);
                    continue;
                }
                if (typeof token !== "string" || token.length === 0) continue;

                fullText += token;
                onText(fullText);
            }
        }
        return { fullText: fullText, sawDone: sawDone };
    }

    async function sendMessage() {
        const prompt = chatInput.value.trim();
        if (!prompt) return;
        chatInput.value = "";

        appendMessage("user", "/images/user.png", "User", prompt);
        const aiBubble = appendMessage("mentor", "/images/mentor.png", "Mentor", "");

        const pathParts = window.location.pathname.split("/");
        const chatId = pathParts[pathParts.length - 1];

        try {
            // Промпт уходит в теле POST-запроса: в query string длинный текст
            // не влезает в лимит размера HTTP-заголовков
            const response = await fetch(`/chat-stream/${chatId}`, {
                method: "POST",
                headers: {"Content-Type": "text/plain;charset=UTF-8"},
                body: prompt
            });
            if (!response.ok) {
                const details = await response.text().catch(() => "");
                throw new Error("HTTP " + response.status + (details ? " — " + details.slice(0, 300) : ""));
            }
            if (!response.body) {
                throw new Error("пустой ответ сервера");
            }

            const result = await readEventStream(response.body, function(text) {
                // Преобразуем Markdown в HTML (при условии, что marked.js подключен)
                aiBubble.innerHTML = marked.parse(text);
                messagesContainer.scrollTop = messagesContainer.scrollHeight;
            });

            if (!result.fullText) {
                throw new Error("сервер не вернул ни одного токена");
            }
            if (!result.sawDone) {
                aiBubble.innerHTML = marked.parse(result.fullText + "\n\n_(ответ прерван)_");
            }
        } catch (e) {
            console.error("Ошибка стриминга ответа:", e);
            if (!aiBubble.textContent) {
                aiBubble.textContent = "Не удалось получить ответ: " + e.message;
            }
        }
    }

    // История приходит из шаблона простым текстом (th:text), поэтому Markdown
    // в сохранённых ответах ассистента нужно отрисовать так же, как при стриме
    function renderHistoryMarkdown() {
        document.querySelectorAll(".messages .message.mentor .bubble").forEach(function(bubble) {
            const text = bubble.textContent;
            if (text.trim()) {
                bubble.innerHTML = marked.parse(text);
            }
        });
    }

    renderHistoryMarkdown();

    // Отправка по клику на кнопку
    sendButton.addEventListener("click", sendMessage);

    // Отправка по Enter (Shift+Enter — перенос строки)
    chatInput.addEventListener("keydown", function(event) {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            sendMessage();
        }
    });
});
