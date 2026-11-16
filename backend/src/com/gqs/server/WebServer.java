package com.gqs.server;

import com.gqs.AppConfig;
import com.gqs.circuit.CircuitException;
import com.gqs.circuit.CircuitRunner;
import com.gqs.json.Json;
import com.gqs.upstream.UpstreamChecker;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 JDK 内置 {@link HttpServer} 的轻量 Web 服务器。
 *
 * <p>同时提供 REST API 与前端静态资源（前端为原生 HTML/CSS/JS，无需构建）。</p>
 */
public final class WebServer {

    private final HttpServer server;
    private final Path frontendRoot;
    private final UpstreamChecker upstream = new UpstreamChecker();

    public WebServer() throws IOException {
        this.frontendRoot = Paths.get(AppConfig.frontendDir()).toAbsolutePath().normalize();
        this.server = HttpServer.create(
            new InetSocketAddress(AppConfig.bindHost(), AppConfig.port()), 0);
        this.server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            if ("OPTIONS".equals(method)) {
                sendEmpty(ex, 204);
                return;
            }
            if ("GET".equals(method) && "/api/meta".equals(path)) {
                sendJson(ex, 200, meta());
            } else if ("GET".equals(method) && "/api/upstream".equals(path)) {
                sendJson(ex, 200, upstream.status());
            } else if ("POST".equals(method) && "/api/circuit/run".equals(path)) {
                runCircuit(ex);
            } else if ("GET".equals(method)) {
                serveStatic(ex, path);
            } else {
                sendJson(ex, 405, error("method_not_allowed", "Method not allowed"));
            }
        } catch (CircuitException ce) {
            sendJson(ex, 400, error("bad_request", ce.getMessage()));
        } catch (Json.JsonException je) {
            sendJson(ex, 400, error("bad_json", je.getMessage()));
        } catch (Exception e) {
            sendJson(ex, 500, error("internal_error",
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            ex.close();
        }
    }

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void runCircuit(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            throw new CircuitException("Request body is empty");
        }
        Object parsed = Json.parse(body);
        if (!(parsed instanceof Map)) {
            throw new CircuitException("Request body must be a JSON object");
        }
        Map<String, Object> resp = CircuitRunner.run((Map<String, Object>) parsed);
        sendJson(ex, 200, resp);
    }

    private Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("app", AppConfig.APP_NAME);
        m.put("version", AppConfig.APP_VERSION);
        m.put("mode", AppConfig.mode());
        m.put("bindHost", AppConfig.bindHost());
        m.put("port", AppConfig.port());
        m.put("upstream", upstream.pinned());
        m.put("maxQubits", AppConfig.MAX_QUBITS);
        m.put("gates", List.of(
            "H", "X", "Y", "Z", "I", "S", "T", "P", "RX", "RY", "RZ",
            "CNOT", "CZ", "SWAP", "TOFFOLI", "M"));
        return m;
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", code);
        m.put("message", message);
        return m;
    }

    // ------------------------------------------------------------------
    // 静态资源
    // ------------------------------------------------------------------

    private void serveStatic(HttpExchange ex, String path) throws IOException {
        String rel = "/".equals(path) || path.isEmpty() ? "index.html" : path.substring(1);
        Path target = frontendRoot.resolve(rel).normalize();
        // 防止目录穿越
        if (!target.startsWith(frontendRoot)) {
            sendNotFound(ex);
            return;
        }
        if (!Files.isRegularFile(target)) {
            sendNotFound(ex);
            return;
        }
        byte[] bytes = Files.readAllBytes(target);
        ex.getResponseHeaders().set("Content-Type", contentType(rel));
        applyCommonHeaders(ex);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendNotFound(HttpExchange ex) throws IOException {
        byte[] bytes = "{\"error\":\"not_found\",\"message\":\"Resource not found\"}"
            .getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        applyCommonHeaders(ex);
        ex.sendResponseHeaders(404, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String contentType(String rel) {
        String lower = rel.toLowerCase();
        if (lower.endsWith(".html")) {
            return "text/html; charset=utf-8";
        } else if (lower.endsWith(".css")) {
            return "text/css; charset=utf-8";
        } else if (lower.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        } else if (lower.endsWith(".json")) {
            return "application/json; charset=utf-8";
        } else if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (lower.endsWith(".png")) {
            return "image/png";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lower.endsWith(".ico")) {
            return "image/x-icon";
        }
        return "application/octet-stream";
    }

    // ------------------------------------------------------------------
    // 响应工具
    // ------------------------------------------------------------------

    private void sendJson(HttpExchange ex, int status, Object obj) throws IOException {
        byte[] bytes = Json.stringify(obj).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        applyCommonHeaders(ex);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendEmpty(HttpExchange ex, int status) throws IOException {
        applyCommonHeaders(ex);
        ex.sendResponseHeaders(status, -1);
    }

    /**
     * 公共响应头：云端模式下开放跨域，便于将前端与后端分离部署。
     */
    private void applyCommonHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (AppConfig.isCloud()) {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        }
    }
}
