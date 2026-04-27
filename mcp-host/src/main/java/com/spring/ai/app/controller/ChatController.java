package com.spring.ai.app.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single endpoint to demonstrate the host. POST a question, get an answer
 * back. Behind the scenes the LLM may invoke any of the tools published
 * by the connected MCP servers.
 */
@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam("q") String question) {
        return chatClient.prompt().user(question).call().content();
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
