package com.spring.ai.app.mcp.web.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;

@Service
public class WebFetchTools {

    private final RestClient restClient;

    private final List<String> allowedHosts;

    public WebFetchTools(RestClient restClient, @Value("${web.allowed-hosts}") List<String> allowedHosts) {
        this.restClient = restClient;
        this.allowedHosts = allowedHosts;
    }

    @Tool(description = """
            Fetch the contents of a public URL. Only URLs whose host is on
            the configured allowlist are permitted. Returns the response body
            as plain text (truncated to 8000 chars).
            """)
    public String fetchUrl(@ToolParam(description = "Full HTTP/HTTPS URL to fetch") String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        if (host == null || allowedHosts.stream().noneMatch(host::endsWith)) {
            return "ERROR: host '" + host + "' is not on the allowlist.";
        }
        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            if (body == null) return "";
            return body.length() > 8000 ? body.substring(0, 8000) + "...[truncated]" : body;
        } catch (Exception e) {
            return "ERROR fetching URL: " + e.getMessage();
        }
    }
}
