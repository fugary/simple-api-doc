package com.fugary.simple.api.exports.md;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class MarkdownHeadingUtilsTest {

    @Test
    public void testDemoteHeadingsBasic() {
        String input = "# Heading 1\n## Heading 2\n### Heading 3\n#### Heading 4\n##### Heading 5\n###### Heading 6";
        String expected = "### Heading 1\n#### Heading 2\n##### Heading 3\n###### Heading 4\n###### Heading 5\n###### Heading 6";
        String actual = MarkdownHeadingUtils.demoteHeadings(input, 2);
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testDemoteHeadingsPreserveCodeBlocks() {
        String input = "# Title\n\n```bash\n# This is a bash comment\necho hello\n```\n\n## Section\n\n~~~python\n# Python comment\n~~~";
        String expected = "### Title\n\n```bash\n# This is a bash comment\necho hello\n```\n\n#### Section\n\n~~~python\n# Python comment\n~~~";
        String actual = MarkdownHeadingUtils.demoteHeadings(input, 2);
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testFindMinHeadingLevel() {
        String input1 = "Some text without headings\n```\n# inside code\n```";
        Assertions.assertEquals(0, MarkdownHeadingUtils.findMinHeadingLevel(input1));

        String input2 = "```\n# inside code\n```\n### Heading 3\n#### Heading 4";
        Assertions.assertEquals(3, MarkdownHeadingUtils.findMinHeadingLevel(input2));

        String input3 = "# Heading 1\n## Heading 2";
        Assertions.assertEquals(1, MarkdownHeadingUtils.findMinHeadingLevel(input3));
    }

    @Test
    public void testStripFrontmatter() {
        String input = "---\ntitle: Guide\norder: 1\n---\n\n# Real Content";
        String actual = MarkdownHeadingUtils.stripFrontmatter(input);
        Assertions.assertEquals("# Real Content", actual);
    }

    @Test
    public void testStripDuplicateTitle() {
        String input = "# 用户指南\n\n这里是正文内容\n## 快速开始";
        String actual = MarkdownHeadingUtils.stripDuplicateTitle(input, "用户指南");
        Assertions.assertEquals("这里是正文内容\n## 快速开始", actual);

        // 带序号的前缀匹配
        String input2 = "# 01-用户指南\n\n这里是正文内容";
        String actual2 = MarkdownHeadingUtils.stripDuplicateTitle(input2, "用户指南");
        Assertions.assertEquals("这里是正文内容", actual2);

        // 不匹配时不剔除
        String input3 = "# 架构设计\n\n这里是正文内容";
        String actual3 = MarkdownHeadingUtils.stripDuplicateTitle(input3, "用户指南");
        Assertions.assertEquals(input3, actual3);
    }

    @Test
    public void testNormalizeDocMarkdown() {
        String input = "---\ntitle: 用户指南\norder: 10\n---\n\n# 用户指南\n\n系统概述介绍。\n\n## 快速入门\n\n### 安装步骤";
        // 剥离 Frontmatter -> 剥离首行重复标题 # 用户指南 -> 剩余 minLevel 为 2 (## 快速入门)
        // targetMinLevel 为 4，offset = 4 - 2 = 2
        // ## 快速入门 -> #### 快速入门；### 安装步骤 -> ##### 安装步骤
        String normalized = MarkdownHeadingUtils.normalizeDocMarkdown(input, "用户指南", 4);
        Assertions.assertTrue(normalized.contains("系统概述介绍。"));
        Assertions.assertTrue(normalized.contains("#### 快速入门"));
        Assertions.assertTrue(normalized.contains("##### 安装步骤"));
        Assertions.assertFalse(normalized.contains("---"));
        Assertions.assertFalse(normalized.contains("# 用户指南"));
    }

    @Test
    public void testRewriteDocLinks() {
        Map<String, String> targetMap = Map.of(
                "01-guide.md", "用户指南",
                "02-faq.md", "常见问题"
        );
        String input = "请参考 [用户指南](./01-guide.md) 和 [常见问题](02-faq.md#q1)，外部链接 [百度](https://baidu.com) 保持原样。\n```markdown\n[测试](./01-guide.md)\n```";
        String actual = MarkdownHeadingUtils.rewriteDocLinks(input, targetMap);
        Assertions.assertTrue(actual.contains("[用户指南](#用户指南)"));
        Assertions.assertTrue(actual.contains("[常见问题](#常见问题)"));
        Assertions.assertTrue(actual.contains("[百度](https://baidu.com)"));
        // 代码块内的链接不被替换
        Assertions.assertTrue(actual.contains("```markdown\n[测试](./01-guide.md)\n```"));
    }
}
