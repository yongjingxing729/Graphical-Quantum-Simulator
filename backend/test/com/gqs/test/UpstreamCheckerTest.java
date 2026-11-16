package com.gqs.test;

import com.gqs.upstream.UpstreamChecker;

import java.util.Map;

/**
 * UpstreamChecker 单元测试。
 *
 * <p>注意：{@code status()} 会做一次「尽力而为」的在线检查（有超时），
 * 因此这里只断言返回结构与字段存在性，不依赖网络结果。</p>
 */
public final class UpstreamCheckerTest {

    private UpstreamCheckerTest() {
    }

    public static void runAll() {
        TestRunner.test("pinned info has expected fields", () -> {
            Map<String, Object> p = new UpstreamChecker().pinned();
            TestRunner.assertEquals("repo",
                "https://github.com/redfx-quantum/strange", p.get("repo"));
            TestRunner.assertEquals("license", "BSD-3-Clause", p.get("license"));
            TestRunner.assertTrue("has pinnedCommit", p.containsKey("pinnedCommit"));
            TestRunner.assertTrue("has pinnedVersion", p.containsKey("pinnedVersion"));
        });

        TestRunner.test("pinned commit format (or empty when offline/cwd)", () -> {
            Object c = new UpstreamChecker().pinned().get("pinnedCommit");
            TestRunner.assertTrue("is string", c instanceof String);
            String sha = (String) c;
            TestRunner.assertTrue("40-hex or empty: " + sha,
                sha.isEmpty() || sha.matches("[0-9a-f]{40}"));
        });

        TestRunner.test("status contains update fields", () -> {
            Map<String, Object> s = new UpstreamChecker().status();
            TestRunner.assertTrue("hasUpdate", s.containsKey("hasUpdate"));
            TestRunner.assertTrue("latestCommit", s.containsKey("latestCommit"));
            TestRunner.assertTrue("latestVersion", s.containsKey("latestVersion"));
            TestRunner.assertTrue("checkedAt", s.containsKey("checkedAt"));
        });
    }
}
