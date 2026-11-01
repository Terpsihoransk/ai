package ru.ars.ai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.ars.ai.model.Chat;
import ru.ars.ai.service.ChatService;
import ru.ars.ai.service.ChatStreamService;
import ru.ars.ai.util.SseStreamBridge;

/**
 * MVC-контроллер. Транспортная часть SSE живёт здесь, а не в сервисе —
 * сервис отдаёт обычный поток токенов.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    private final ChatStreamService chatStreamService;

    private final SseStreamBridge sseStreamBridge;

    @GetMapping("/")
    public String mainPage(ModelMap model) {
        model.addAttribute("chats", chatService.getAllChats());
        return "chat";
    }

    @GetMapping("/chat/{chatId}")
    public String showChat(@PathVariable Long chatId, ModelMap model) {
        model.addAttribute("chats", chatService.getAllChats());
        model.addAttribute("chat", chatService.getChat(chatId));
        return "chat";
    }

    @PostMapping("/chat/new")
    public String newChat(@RequestParam String title) {
        Chat chat = chatService.createNewChat(title);
        return "redirect:/chat/" + chat.getId();
    }

    /** Форма в шаблоне отправляет POST, поэтому именно POST — иначе кнопка удаления не работала. */
    @PostMapping("/chat/{chatId}/delete")
    public String deleteChat(@PathVariable Long chatId) {
        chatService.deleteChat(chatId);
        return "redirect:/";
    }

    /**
     * Отдаёт поток токенов модели как SSE. Мост добавляет событие {@code done} в конце,
     * сигнализирующее клиенту о завершении генерации.
     * <p>
     * Промпт передаётся в теле POST-запроса, а не в query string: у Tomcat есть жёсткий лимит
     * на размер заголовка запроса (по умолчанию 8 КБ), и длинный текст падал с
     * {@code Request header is too large}.
     */
    @PostMapping(value = "/chat-stream/{chatId}",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@PathVariable Long chatId, @RequestBody String userPrompt) {
        log.debug("Стриминг ответа: chatId={}, длина промпта={} символов", chatId, userPrompt.length());
        return sseStreamBridge.toEmitter(chatStreamService.streamAnswer(chatId, userPrompt));
    }

}