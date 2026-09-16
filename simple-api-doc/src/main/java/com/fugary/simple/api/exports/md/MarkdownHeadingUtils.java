package com.fugary.simple.api.exports.md;

import com.fugary.simple.api.imports.markdown.MarkdownDocImporterImpl;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 标题动态降级、标题规范化与链接重写工具类
 *
 * @author gary.fu
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MarkdownHeadingUtils {

    /**
     * 匹配 ATX 标题（最多允许 3 个前导空格，1-6 个 #，后接空格或制表符）
     */
    private static final Pattern ATX_HEADING_PATTERN = Pattern.compile("^[ ]{0,3}(#{1,6})([ \\t]+.*|[ \\t]*)$");

    /**
     * 匹配代码块开闭围栏（3 个及以上的 ` 或 ~）
     */
    private static final Pattern FENCE_PATTERN = Pattern.compile("^[ ]{0,3}((`{3,})|(~{3,}))(.*)$");

    /**
     * 匹配 Markdown 链接：[text](url)
     */
    private static final Pattern MD_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^)\\s]+)(?:\\s*\"[^\"]*\")?\\)");

    /**
     * 匹配标题前缀序号等噪音
     */
    private static final Pattern TITLE_PREFIX_PATTERN = Pattern.compile("^[0-9]+[.\\-_ ]+");

    /**
     * 动态平移/降级 Markdown 中的 ATX 标题层级（精准避开代码块内部的内容）
     *
     * @param markdown    原始 Markdown 文本
     * @param levelOffset 降级偏移量（例如 1 表示 # 变为 ##，2 表示 # 变为 ###）
     * @return 降级后的 Markdown 文本
     */
    public static String demoteHeadings(String markdown, int levelOffset) {
        if (StringUtils.isBlank(markdown) || levelOffset <= 0) {
            return markdown;
        }

        String[] lines = markdown.split("\r?\n", -1);
        boolean isWindows = markdown.contains("\r\n");
        String lineSeparator = isWindows ? "\r\n" : "\n";

        FenceTracker fenceTracker = new FenceTracker();
        StringBuilder sb = new StringBuilder(markdown.length() + 64);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (!fenceTracker.checkLine(line)) {
                // 代码块外部才检测并修改标题
                Matcher headingMatcher = ATX_HEADING_PATTERN.matcher(line);
                if (headingMatcher.matches()) {
                    int currentLevel = headingMatcher.group(1).length();
                    int newLevel = Math.min(6, currentLevel + levelOffset);
                    String hashes = "#".repeat(newLevel);
                    line = hashes + headingMatcher.group(2);
                }
            }

            sb.append(line);
            if (i < lines.length - 1) {
                sb.append(lineSeparator);
            }
        }

        return sb.toString();
    }

    /**
     * 计算 Markdown 文档在代码块外部的最小标题级别（1..6），若无标题返回 0
     *
     * @param markdown Markdown 文本
     * @return 最小标题级别
     */
    public static int findMinHeadingLevel(String markdown) {
        if (StringUtils.isBlank(markdown)) {
            return 0;
        }

        String[] lines = markdown.split("\r?\n", -1);
        FenceTracker fenceTracker = new FenceTracker();
        int minLevel = Integer.MAX_VALUE;

        for (String line : lines) {
            if (!fenceTracker.checkLine(line)) {
                Matcher headingMatcher = ATX_HEADING_PATTERN.matcher(line);
                if (headingMatcher.matches()) {
                    int currentLevel = headingMatcher.group(1).length();
                    minLevel = Math.min(minLevel, currentLevel);
                }
            }
        }

        return minLevel == Integer.MAX_VALUE ? 0 : minLevel;
    }

    /**
     * 剥离文档可能包含的 YAML Frontmatter 元数据头部
     *
     * @param content Markdown 内容
     * @return 剥离后的内容
     */
    public static String stripFrontmatter(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        Matcher matcher = MarkdownDocImporterImpl.FRONTMATTER_PATTERN.matcher(content);
        if (matcher.matches()) {
            return matcher.group(2).stripLeading();
        }
        return content;
    }

    /**
     * 若文档正文起始第一个非空行是与文档名相同的标题，则剥离该重复标题
     *
     * @param markdown Markdown 文本
     * @param docName  文档名称
     * @return 处理后的 Markdown 文本
     */
    public static String stripDuplicateTitle(String markdown, String docName) {
        if (StringUtils.isBlank(markdown) || StringUtils.isBlank(docName)) {
            return markdown;
        }

        String[] lines = markdown.split("\r?\n", -1);
        boolean isWindows = markdown.contains("\r\n");
        String lineSeparator = isWindows ? "\r\n" : "\n";

        int firstNonBlankIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            if (StringUtils.isNotBlank(lines[i])) {
                firstNonBlankIdx = i;
                break;
            }
        }

        if (firstNonBlankIdx >= 0) {
            String firstLine = lines[firstNonBlankIdx];
            Matcher headingMatcher = ATX_HEADING_PATTERN.matcher(firstLine);
            if (headingMatcher.matches()) {
                String headingText = headingMatcher.group(2).trim();
                if (isTitleMatch(headingText, docName)) {
                    // 找到首个标题且与文档名匹配，剥离该行以及其后连续的空行
                    int nextIdx = firstNonBlankIdx + 1;
                    while (nextIdx < lines.length && StringUtils.isBlank(lines[nextIdx])) {
                        nextIdx++;
                    }
                    if (nextIdx >= lines.length) {
                        return "";
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = nextIdx; i < lines.length; i++) {
                        sb.append(lines[i]);
                        if (i < lines.length - 1) {
                            sb.append(lineSeparator);
                        }
                    }
                    return sb.toString();
                }
            }
        }

        return markdown;
    }

    /**
     * 判断标题文本与文档名称是否相匹配（支持忽略前缀序号等）
     */
    private static boolean isTitleMatch(String headingText, String docName) {
        if (StringUtils.equalsIgnoreCase(headingText, docName)) {
            return true;
        }
        String cleanHeading = cleanTitle(headingText);
        String cleanDocName = cleanTitle(docName);
        return StringUtils.equalsIgnoreCase(cleanHeading, cleanDocName);
    }

    /**
     * 清理标题中的序号、标点等噪音
     */
    private static String cleanTitle(String title) {
        if (title == null) {
            return "";
        }
        return TITLE_PREFIX_PATTERN.matcher(title).replaceFirst("").trim();
    }

    /**
     * 对 Markdown 说明文档进行标准化处理：
     * 1. 剥离 Frontmatter
     * 2. 剥离与 docName 重复的首行标题
     * 3. 动态降级内部标题：使内部最小标题至少为 targetMinLevel（单 MD 导出推荐为 4，即 ####）
     *
     * @param docContent      原始正文
     * @param docName         文档名称
     * @param targetMinLevel  目标最小标题级别（例如 4）
     * @return 标准化后的 Markdown
     */
    public static String normalizeDocMarkdown(String docContent, String docName, int targetMinLevel) {
        if (StringUtils.isBlank(docContent)) {
            return "";
        }

        String content = stripFrontmatter(docContent);
        content = stripDuplicateTitle(content, docName);

        int minLevel = findMinHeadingLevel(content);
        if (minLevel > 0 && minLevel < targetMinLevel) {
            int offset = targetMinLevel - minLevel;
            content = demoteHeadings(content, offset);
        }

        return content;
    }

    /**
     * 快捷重载：默认目标最小标题级别为 4（####），适配单 MD 导出三级标题 ### 📄 章节
     */
    public static String normalizeDocMarkdown(String docContent, String docName) {
        return normalizeDocMarkdown(docContent, docName, 4);
    }

    /**
     * 将 Markdown 文档中指向项目内部其他文档的相对链接，重写为单文档内的标题锚点跳转链接
     * 例如：[快速开始](./01-quickstart.md) -> [快速开始](#快速开始)
     *
     * @param markdown       Markdown 文本
     * @param docTargetMap   目标文件名/相对路径到目标文档标题的映射表
     * @return 重写后的 Markdown
     */
    public static String rewriteDocLinks(String markdown, Map<String, String> docTargetMap) {
        if (StringUtils.isBlank(markdown) || docTargetMap == null || docTargetMap.isEmpty()) {
            return markdown;
        }

        String[] lines = markdown.split("\r?\n", -1);
        boolean isWindows = markdown.contains("\r\n");
        String lineSeparator = isWindows ? "\r\n" : "\n";

        FenceTracker fenceTracker = new FenceTracker();
        StringBuilder sb = new StringBuilder(markdown.length());
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (!fenceTracker.checkLine(line)) {
                line = rewriteLinksInLine(line, docTargetMap);
            }

            sb.append(line);
            if (i < lines.length - 1) {
                sb.append(lineSeparator);
            }
        }

        return sb.toString();
    }

    /**
     * 在单行中扫描并替换相对文档链接
     */
    private static String rewriteLinksInLine(String line, Map<String, String> docTargetMap) {
        if (StringUtils.isBlank(line) || !line.contains("](") || !line.contains(".md")) {
            return line;
        }

        Matcher matcher = MD_LINK_PATTERN.matcher(line);
        StringBuilder sb = new StringBuilder();
        boolean found = false;

        while (matcher.find()) {
            String linkText = matcher.group(1);
            String url = matcher.group(2);

            // 忽略外链与纯锚点
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("#") || url.startsWith("mailto:")) {
                continue;
            }

            String filePart = url;
            int hashIdx = url.indexOf('#');
            if (hashIdx >= 0) {
                filePart = url.substring(0, hashIdx);
            }

            if (filePart.endsWith(".md") || filePart.endsWith(".markdown")) {
                String targetAnchor = findTargetDocAnchor(filePart, docTargetMap);
                if (StringUtils.isNotBlank(targetAnchor)) {
                    found = true;
                    String replacement = "[" + linkText + "](#" + targetAnchor + ")";
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                }
            }
        }

        if (found) {
            matcher.appendTail(sb);
            return sb.toString();
        }
        return line;
    }

    /**
     * 根据相对路径自适应匹配目标文档标题作为锚点
     */
    private static String findTargetDocAnchor(String filePart, Map<String, String> docTargetMap) {
        if (StringUtils.isBlank(filePart)) {
            return null;
        }
        // 1. 直接全量匹配
        if (docTargetMap.containsKey(filePart)) {
            return docTargetMap.get(filePart);
        }
        // 2. 去除 ./ 或 / 前缀匹配
        String cleanPath = filePart.replaceAll("^[./\\\\]+", "");
        if (docTargetMap.containsKey(cleanPath)) {
            return docTargetMap.get(cleanPath);
        }
        // 3. 仅根据文件名匹配（如 01-guide.md）
        String fileName = FilenameUtils.getName(filePart);
        if (docTargetMap.containsKey(fileName)) {
            return docTargetMap.get(fileName);
        }
        // 4. 去除 .md 扩展名匹配
        String baseName = FilenameUtils.getBaseName(filePart);
        if (docTargetMap.containsKey(baseName)) {
            return docTargetMap.get(baseName);
        }
        return null;
    }

    /**
     * 代码块围栏状态追踪器，用于精准识别 Markdown 中的代码块区域
     */
    private static class FenceTracker {
        private boolean inCodeBlock = false;
        private char fenceChar = ' ';
        private int fenceLength = 0;

        /**
         * 处理一行文本，若处于代码块开闭围栏行或处于代码块内部，返回 true；否则返回 false
         */
        public boolean checkLine(String line) {
            Matcher fenceMatcher = FENCE_PATTERN.matcher(line);
            if (fenceMatcher.matches()) {
                String fence = fenceMatcher.group(1);
                char curFenceChar = fence.charAt(0);
                int curFenceLength = fence.length();

                if (!inCodeBlock) {
                    inCodeBlock = true;
                    fenceChar = curFenceChar;
                    fenceLength = curFenceLength;
                } else if (curFenceChar == fenceChar && curFenceLength >= fenceLength) {
                    inCodeBlock = false;
                }
                return true;
            }
            return inCodeBlock;
        }
    }
}
