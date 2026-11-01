document.addEventListener("DOMContentLoaded", function() {
    const sendButton = document.getElementById("send-button");
    const chatInput = document.getElementById("chat-input");
    const messagesContainer = document.getElementById("messages");

    function sendMessage() {
        const prompt = chatInput.value.trim();
        if (!prompt) return;
        chatInput.value = "";

        // Добавляем сообщение пользователя в чат
        const userDiv = document.createElement("div");
        userDiv.className = "message user";
        userDiv.innerHTML = `<img src="/images/user.png" alt="User"><div class="bubble">${prompt}</div>`;
        messagesContainer.appendChild(userDiv);

        const pathParts = window.location.pathname.split("/");
        const chatId = pathParts[pathParts.length - 1];
        const url = `/chat-stream/${chatId}?userPrompt=${encodeURIComponent(prompt)}`;

        const eventSource = new EventSource(url);
        let fullText = "";

        // Создаем блок для ответа AI
        const aiDiv = document.createElement("div");
        aiDiv.className = "message mentor";
        // Добавляем изображение ассистента
        aiDiv.innerHTML = `<img src="/images/mentor.png" alt="Mentor">`;
        // Создаем элемент для содержимого, куда будем вставлять ответ
        const aiBubble = document.createElement("div");
        aiBubble.className = "bubble";
        aiDiv.appendChild(aiBubble);
        messagesContainer.appendChild(aiDiv);

        eventSource.onmessage = function(event) {
            let token = event.data;
            // Сервер присылает JSON вида {"text":"..."}; поддерживаем также JSON-строку и сырой текст
            try {
                const parsed = JSON.parse(token);
                if (typeof parsed === "string") {
                    token = parsed;
                } else if (parsed && typeof parsed.text === "string") {
                    token = parsed.text;
                }
            } catch (e) {
                // не JSON — используем данные как есть
            }
            if (!token) return;
            fullText += token;
            // Преобразуем Markdown в HTML (при условии, что marked.js подключен)
            aiBubble.innerHTML = marked.parse(fullText);
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
        };

        // Сервер присылает событие "done" по завершении стрима — закрываем соединение,
        // иначе EventSource автоматически переподключится и повторно вызовет модель
        eventSource.addEventListener("done", function() {
            eventSource.close();
        });

        eventSource.onerror = function(e) {
            console.error("Ошибка SSE:", e);
            eventSource.close();
        };
    }

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
