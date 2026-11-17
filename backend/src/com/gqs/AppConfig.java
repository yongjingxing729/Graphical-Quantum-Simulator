package com.gqs;

/**
 * 应用全局配置。
 *
 * <p><b>★★★ 本地 / 云端运行模式的唯一开关 ★★★</b></p>
 *
 * <p>把下面 {@link #RUN_MODE} 的值从 {@code "local"} 改为 {@code "cloud"}，
 * 即可让服务切换为「云端服务器运行」模式（绑定 0.0.0.0、开放跨域、适配端口/前端目录环境变量）。
 * 默认为 {@code "local"}（绑定 127.0.0.1，仅本机可访问）。</p>
 *
 * <p>在 Docker / 云部署场景下，也可以不改代码，而是通过环境变量 {@code QSIM_MODE}
 * 覆盖该值（例如 {@code QSIM_MODE=cloud}），便于在云端编排平台一键切换。</p>
 */
public final class AppConfig {

    /**
     * ★★★ 唯一需要修改的变量：本地 / 云端运行模式 ★★★
     * 取值："local"（默认，本地运行）或 "cloud"（云端服务器运行）。
     */
    public static final String RUN_MODE = "local";

    /** 应用名称。 */
    public static final String APP_NAME = "Graphical Quantum Simulator";

    /** 本项目版本号。 */
    public static final String APP_VERSION = "0.1.0";

    /** 默认端口。 */
    public static final int DEFAULT_PORT = 8080;

    /** 单次线路允许的最大量子比特数（避免指数级内存爆炸）。 */
    public static final int MAX_QUBITS = 12;

    private AppConfig() {
    }

    /**
     * 解析实际运行模式：优先使用环境变量 QSIM_MODE，其次使用 RUN_MODE 常量。
     */
    public static String mode() {
        String env = System.getenv("QSIM_MODE");
        if (env != null && !env.isBlank()) {
            return env.trim().toLowerCase();
        }
        return RUN_MODE;
    }

    /** 是否为云端运行模式。 */
    public static boolean isCloud() {
        return "cloud".equals(mode());
    }

    /**
     * 服务监听地址。
     *
     * <p>本地模式默认绑定回环地址 {@code 127.0.0.1}，云端模式绑定 {@code 0.0.0.0}。
     * 在 Docker 容器中运行时，即使保持「本地模式」，也需要监听 {@code 0.0.0.0}
     * （否则宿主机的端口映射无法访问容器），因此支持环境变量 {@code QSIM_BIND_HOST} 覆盖。</p>
     */
    public static String bindHost() {
        String env = System.getenv("QSIM_BIND_HOST");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return isCloud() ? "0.0.0.0" : "127.0.0.1";
    }

    /** 服务监听端口：可用环境变量 QSIM_PORT 覆盖。 */
    public static int port() {
        String env = System.getenv("QSIM_PORT");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignored) {
                // 忽略非法端口，回退默认值
            }
        }
        return DEFAULT_PORT;
    }

    /** 前端静态资源目录：可用环境变量 QSIM_FRONTEND 覆盖。 */
    public static String frontendDir() {
        String env = System.getenv("QSIM_FRONTEND");
        return (env != null && !env.isBlank()) ? env : "frontend";
    }
}
