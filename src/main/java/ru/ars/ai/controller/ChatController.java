package ru.ars.ai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import ru.ars.ai.model.Chat;
import ru.ars.ai.service.ChatService;

import java.io.IOException;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/")
    public String mainPage(ModelMap model) {
        log.info("GET / — загрузка главной страницы");
        model.addAttribute("chats", chatService.getAllChats());
        return "chat";
    }

    @GetMapping("/chat/{chatId}")
    public String showChat(@PathVariable Long chatId, ModelMap model) {
        log.info("GET /chat/{} — открытие чата", chatId);
        model.addAttribute("chats", chatService.getAllChats());
        model.addAttribute("chat", chatService.getChat(chatId));
        return "chat";
    }

    @PostMapping("/chat/new")
    public String newChat(@RequestParam String title) {
        log.info("POST /chat/new — создание чата с названием '{}'", title);
        Chat chat = chatService.createNewChat(title);
        log.debug("POST /chat/new — чат создан, id={}", chat.getId());
        return "redirect:/chat/" + chat.getId();
    }

    @DeleteMapping("chat/{chatId}/delete")
    public String deleteChat(@PathVariable Long chatId) {
        log.info("DELETE /chat/{}/delete — удаление чата", chatId);
        chatService.deleteChat(chatId);
        return "redirect:/";
    }

    @GetMapping("/chat-stream/{chatId}")
    public SseEmitter streamChat(@PathVariable Long chatId, @RequestParam("userPrompt") String userPrompt) {
        log.info("GET /chat-stream/{} — стриминг ответа модели, длина промпта={} символов", chatId, userPrompt.length());
        SseEmitter emitter = new SseEmitter(300_000L);
        Disposable subscription = chatService.proceedInteractionStream(chatId, userPrompt).subscribe(
                token -> {
                    try {
                        emitter.send(SseEmitter.event().data(toJsonString(token)));
                    } catch (IOException e) {
                        log.warn("Не удалось отправить токен клиенту (chatId={}): {}", chatId, e.getMessage());
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    log.error("Ошибка стриминга ответа модели (chatId={}): {}", chatId, error.getMessage(), error);
                    emitter.completeWithError(error);
                },
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data(""));
                    } catch (IOException e) {
                        log.debug("Соединение закрыто до отправки завершающего события (chatId={})", chatId);
                    }
                    emitter.complete();
                }
        );
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(subscription::dispose);
        return emitter;
    }

    /**
     * Экранирует значение как JSON-строку. Нужно потому, что SSE отбрасывает ведущий пробел
     * после "data:", из-за чего токены-пробелы терялись бы в UI.
     */
    private static String toJsonString(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

}