package com.gqs.upstream;

import com.gqs.json.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上游 Strange 仓库信息检查器。
 *
 * <p>职责：读取 {@code strange.version} 中的锁定信息，并（尽力而为地）查询 GitHub 与
 * Maven Central，判断上游是否有新提交 / 新版本，为开发者提供「需要更新」的提醒。</p>
 *
 * <p>网络查询是<b>尽力而为</b>的：离线或超时不会导致服务失败，只是返回 {@code hasUpdate=null}（未知）。</p>
 */
public final class UpstreamChecker {

    public static final String REPO = "https://github.com/redfx-quantum/strange";
    public static final String LICENSE = "BSD-3-Clause";

    private static final long CACHE_MS = 60 * 60 * 1000L; // 1 小时缓存

    private final String pinnedCommit;
    private final String pinnedVersion;

    private volatile Map<String, Object> cachedLatest = null;
    private volatile long cachedAt = 0;

    public UpstreamChecker() {
        String commit = "";
        String version = "";
        Path f = locateStrangeVersionFile();
        if (f != null) {
            try {
                for (String line : Files.readAllLines(f)) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#")) {
                        continue;
                    }
                    int eq = t.indexOf('=');
                    if (eq < 0) {
                        continue;
                    }
                    String k = t.substring(0, eq).trim();
                    String v = t.substring(eq + 1).trim();
                    if ("commit".equals(k)) {
                        commit = v;
                    } else if ("version".equals(k)) {
                        version = v;
                    }
                }
            } catch (IOException ignored) {
                // 读取失败时回退为空，不影响服务启动
            }
        }
        this.pinnedCommit = commit;
        this.pinnedVersion = version;
    }

    private static Path locateStrangeVersionFile() {
        String[] candidates = {
            "strange.version",
            "./strange.version",
            "../strange.version",
        };
        for (String c : candidates) {
            Path p = Paths.get(c);
            if (Files.exists(p)) {
                return p;
            }
        }
        String home = System.getenv("QSIM_HOME");
        if (home != null && !home.isBlank()) {
            Path p = Paths.get(home, "strange.version");
            if (Files.exists(p)) {
                return p;
            }
        }
        return null;
    }

    /**
     * 仅返回锁定信息（不做网络请求），用于 /api/meta 快速响应。
     */
    public Map<String, Object> pinned() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("repo", REPO);
        m.put("license", LICENSE);
        m.put("pinnedCommit", pinnedCommit);
        m.put("pinnedVersion", pinnedVersion);
        return m;
    }

    /**
     * 返回完整状态（含尽力而为的上游检查，结果缓存 1 小时），用于 /api/upstream。
     */
    public Map<String, Object> status() {
        Map<String, Object> m = pinned();
        Map<String, Object> latest;
        synchronized (this) {
            if (cachedLatest != null && System.currentTimeMillis() - cachedAt < CACHE_MS) {
                latest = cachedLatest;
            } else {
                latest = fetchLatest();
                cachedLatest = latest;
                cachedAt = System.currentTimeMillis();
            }
        }
        Object lc = latest.get("latestCommit");
        Object lv = latest.get("latestVersion");
        m.put("latestCommit", lc);
        m.put("latestVersion", lv);
        m.put("checkedAt", latest.get("checkedAt"));
        m.put("hasUpdate", computeHasUpdate(lc, lv));
        return m;
    }

    private Boolean computeHasUpdate(Object latestCommit, Object latestVersion) {
        boolean known = false;
        boolean update = false;
        if (latestCommit instanceof String && pinnedCommit != null && !pinnedCommit.isEmpty()) {
            known = true;
            if (!latestCommit.equals(pinnedCommit)) {
                update = true;
            }
        }
        if (latestVersion instanceof String && pinnedVersion != null && !pinnedVersion.isEmpty()) {
            known = true;
            if (!latestVersion.equals(pinnedVersion)) {
                update = true;
            }
        }
        return known ? update : null;
    }

    private Map<String, Object> fetchLatest() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("latestCommit", fetchLatestCommit());
        out.put("latestVersion", fetchLatestVersion());
        out.put("checkedAt", System.currentTimeMillis());
        return out;
    }

    private String fetchLatestCommit() {
        String body = httpGet("https://api.github.com/repos/redfx-quantum/strange/commits/main");
        if (body == null) {
            return null;
        }
        try {
            Object o = Json.parse(body);
            if (o instanceof Map) {
                Object sha = ((Map<?, ?>) o).get("sha");
                if (sha instanceof String) {
                    return ((String) sha).trim();
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private String fetchLatestVersion() {
        String body = httpGet(
            "https://search.maven.org/solrsearch/select?q=g:org.redfx+AND+a:strange&rows=1&wt=json");
        if (body == null) {
            return null;
        }
        try {
            Object o = Json.parse(body);
            if (o instanceof Map) {
                Object resp = ((Map<?, ?>) o).get("response");
                if (resp instanceof Map) {
                    Object docs = ((Map<?, ?>) resp).get("docs");
                    if (docs instanceof List && !((List<?>) docs).isEmpty()) {
                        Object first = ((List<?>) docs).get(0);
                        if (first instanceof Map) {
                            Object lv = ((Map<?, ?>) first).get("latestVersion");
                            if (lv instanceof String) {
                                return (String) lv;
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private String httpGet(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .header("User-Agent", "gqs-upstream-checker")
                .GET()
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return resp.body();
            }
        } catch (Exception ignored) {
            // 网络不可用 / 超时：返回 null 表示「未知」
        }
        return null;
    }
}
