package com.spring.ai.app.mcp.file.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;


@Service
public class FileSystemTools {

    private final Path fsRoot;

    public FileSystemTools(@Value("${fs.root}") String fsRoot) throws IOException {
        this.fsRoot = Paths.get(fsRoot).toAbsolutePath().normalize();
        Files.createDirectories(this.fsRoot);
    }

    @Tool(description = """
            List files in a directory inside the sandboxed root.
            Use a relative path like "." for root, or "sub directory" for a subdirectory.
            """)
    public List<String> listFiles(@ToolParam(description = "Relative path inside the sandbox root, e.g. \".\" or \"reports\"") String relativePath) throws IOException {
        Path target = resolveSafe(relativePath);
        try (Stream<Path> stream = Files.list(target)) {
            return stream.map(p -> fsRoot.relativize(p).toString()).sorted().toList();
        }
    }

    @Tool(description = """
            Read the contents of a text file inside the sandbox root.
            Returns the full file contents as a string.
            """)
    public String readFile(@ToolParam(description = "Relative path to the file from the sandbox root") String relativePath) throws IOException {
        Path target = resolveSafe(relativePath);
        if (!Files.isRegularFile(target)) {
            throw new IOException("Not a regular file: " + relativePath);
        }
        return Files.readString(target);
    }

    @Tool(description = """
            Write text content to a file inside the sandbox root.
            Creates the file if it doesn't exist; overwrites if it does.
            """)
    public String writeFile(@ToolParam(description = "Relative path to write to") String relativePath, @ToolParam(description = "Text content to write") String content) throws IOException {
        Path target = resolveSafe(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
        return "Wrote " + content.length() + " characters to " + relativePath;
    }

    /**
     * Resolves a user-supplied relative path against the sandbox root and
     * validates the result is still inside the root. Rejects any traversal.
     */
    private Path resolveSafe(String relativePath) throws IOException {
        Path resolved = fsRoot.resolve(relativePath).toAbsolutePath().normalize();
        if (!resolved.startsWith(fsRoot)) {
            throw new IOException("Path escape attempt rejected: " + relativePath);
        }
        return resolved;
    }
}
