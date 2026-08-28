package com.fugary.simple.api.web.vo.imports;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * 导入文档来源数据载体（支持纯文本与二进制字节流）
 *
 * @author gary.fu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocSourceData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文本内容（Swagger JSON/YAML、单个 Markdown、Git 虚拟 JSON 列表等）
     */
    private String textContent;

    /**
     * 二进制字节内容（ZIP 压缩包等）
     */
    private byte[] binaryContent;

    /**
     * 文件名或来源文件名
     */
    private String fileName;

    /**
     * 内容类型（如 application/zip, text/plain, application/json 等）
     */
    private String contentType;

    public static DocSourceData ofText(String text) {
        return DocSourceData.builder().textContent(text).build();
    }

    public static DocSourceData ofText(String text, String fileName) {
        return DocSourceData.builder().textContent(text).fileName(fileName).build();
    }

    public static DocSourceData ofBinary(byte[] bytes) {
        return DocSourceData.builder().binaryContent(bytes).build();
    }

    public static DocSourceData ofBinary(byte[] bytes, String fileName) {
        return DocSourceData.builder().binaryContent(bytes).fileName(fileName).build();
    }

    public static DocSourceData ofBinary(byte[] bytes, String fileName, String contentType) {
        return DocSourceData.builder().binaryContent(bytes).fileName(fileName).contentType(contentType).build();
    }

    /**
     * 是否包含二进制内容
     *
     * @return boolean
     */
    public boolean isBinary() {
        return binaryContent != null && binaryContent.length > 0;
    }

    /**
     * 是否包含文本内容
     *
     * @return boolean
     */
    public boolean isText() {
        return StringUtils.isNotBlank(textContent);
    }

    /**
     * 是否为空数据
     *
     * @return boolean
     */
    public boolean isEmpty() {
        return !isBinary() && !isText();
    }

    /**
     * 安全获取文本内容（若为纯文本直接返回；若为二进制则安全解码为 UTF-8 字符串兜底）
     *
     * @return 文本内容
     */
    public String getTextContent() {
        if (textContent != null) {
            return textContent;
        }
        if (binaryContent != null) {
            return new String(binaryContent, StandardCharsets.UTF_8);
        }
        return null;
    }

    /**
     * 安全获取二进制字节内容（若为二进制直接返回；若为纯文本则按 UTF-8 转为字节数组）
     *
     * @return 字节数组
     */
    public byte[] getBinaryContent() {
        if (binaryContent != null) {
            return binaryContent;
        }
        if (textContent != null) {
            return textContent.getBytes(StandardCharsets.UTF_8);
        }
        return null;
    }
}
