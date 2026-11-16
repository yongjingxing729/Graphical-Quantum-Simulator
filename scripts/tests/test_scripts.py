#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""scripts/ 下 Python 工具的单元测试（仅标准库 unittest）。"""

import contextlib
import io
import shutil
import sys
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SCRIPTS_DIR))

import check_upstream  # noqa: E402

# 测试临时目录放在工作区内（沙箱允许写入），避免系统临时目录权限问题
TEST_TMP = Path(__file__).resolve().parent / "_tmp"


class ReadPinnedTest(unittest.TestCase):
    def setUp(self):
        self.work = TEST_TMP / ("case_" + self._testMethodName)
        if self.work.exists():
            shutil.rmtree(self.work, ignore_errors=True)
        self.work.mkdir(parents=True, exist_ok=True)

    def tearDown(self):
        shutil.rmtree(self.work, ignore_errors=True)

    def test_parses_fields(self):
        (self.work / "strange.version").write_text(
            "# 注释\n"
            "repo=https://github.com/redfx-quantum/strange\n"
            "commit=0123456789abcdef0123456789abcdef01234567\n"
            "version=0.1.3\n"
            "license=BSD-3-Clause\n",
            encoding="utf-8",
        )
        data = check_upstream.read_pinned(self.work)
        self.assertEqual(data["commit"], "0123456789abcdef0123456789abcdef01234567")
        self.assertEqual(data["version"], "0.1.3")
        self.assertEqual(data["license"], "BSD-3-Clause")

    def test_missing_file_uses_defaults(self):
        with contextlib.redirect_stderr(io.StringIO()):
            data = check_upstream.read_pinned(self.work)
        self.assertEqual(data["commit"], "")
        self.assertEqual(data["version"], "")
        self.assertEqual(data["license"], "BSD-3-Clause")

    def test_ignores_comments_and_blank_lines(self):
        (self.work / "strange.version").write_text(
            "\n# a comment\nversion=9.9.9\n\n", encoding="utf-8"
        )
        data = check_upstream.read_pinned(self.work)
        self.assertEqual(data["version"], "9.9.9")


class ConstantsTest(unittest.TestCase):
    def test_repo_url(self):
        self.assertEqual(check_upstream.REPO, "https://github.com/redfx-quantum/strange")


if __name__ == "__main__":
    unittest.main()
