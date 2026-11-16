package com.gqs;

import com.gqs.server.WebServer;
import com.gqs.upstream.UpstreamChecker;

import java.util.Map;

/**
 * 应用入口：启动内嵌 HTTP 服务器。
 *
 * <p>运行模式（本地 / 云端）由 {@link AppConfig#RUN_MODE} 单一变量控制，
 * 也可用环境变量 QSIM_MODE 覆盖。详见 {@link AppConfig}。</p>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        String mode = AppConfig.mode();
        String host = AppConfig.bindHost();
        int port = AppConfig.port();

        printBanner(mode, host, port);
        printUpstreamReminder();

        WebServer server = new WebServer();
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n正在关闭服务器...");
            server.stop();
        }));

        System.out.printf("✅ 服务器已启动：http://%s:%d%n", host, port);
        if ("local".equals(mode)) {
            System.out.println("   （本地模式，仅本机可访问）在浏览器打开 http://127.0.0.1:" + port);
        } else {
            System.out.println("   （云端模式，已绑定 0.0.0.0，可通过服务器公网 IP 访问）");
        }
        System.out.println("   按 Ctrl+C 停止。");
    }

    private static void printBanner(String mode, String host, int port) {
        System.out.println("==============================================================");
        System.out.println("  " + AppConfig.APP_NAME + "  v" + AppConfig.APP_VERSION);
        System.out.println("--------------------------------------------------------------");
        System.out.println("  运行模式        : " + mode + "  (由 AppConfig.RUN_MODE 控制)");
        System.out.println("  监听地址        : " + host + ":" + port);
        System.out.println("  量子内核        : Strange (org.redfx:strange, BSD-3-Clause)");
        System.out.println("  本项目协议      : MIT (详见 LICENSE)");
        System.out.println("  开源声明        : 详见 THIRD_PARTY_NOTICES.md 与 NOTICE");
        System.out.println("==============================================================");
    }

    private static void printUpstreamReminder() {
        Map<String, Object> pinned = new UpstreamChecker().pinned();
        Object commit = pinned.get("pinnedCommit");
        Object version = pinned.get("pinnedVersion");
        System.out.println();
        System.out.println("🔔 开发者提醒（上游开源代码）:");
        System.out.println("   上游项目  : " + pinned.get("repo"));
        System.out.println("   锁定版本  : " + version + "  (commit " + commit + ")");
        System.out.println("   检查更新  : 运行  python scripts/check_upstream.py");
        System.out.println("   更新后请同步: strange.version / NOTICE / THIRD_PARTY_NOTICES.md");
        System.out.println();
    }
}
