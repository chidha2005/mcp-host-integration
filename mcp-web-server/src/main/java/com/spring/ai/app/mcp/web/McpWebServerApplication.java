package com.spring.ai.app.mcp.web;

import com.spring.ai.app.mcp.web.service.WebFetchTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class McpWebServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpWebServerApplication.class, args);
    }

    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }

    @Bean
    public ToolCallbackProvider webToolProvider(WebFetchTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
