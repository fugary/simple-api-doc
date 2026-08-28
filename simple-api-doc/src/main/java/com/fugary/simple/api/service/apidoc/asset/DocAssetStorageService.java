package com.fugary.simple.api.service.apidoc.asset;

import java.util.Map;

/**
 * 文档静态资源（图片等）存储与链接替换服务
 *
 * @author gary.fu
 */
public interface DocAssetStorageService {

    /**
     * 保存图片资源并返回访问 URL（基于 MD5 命名与 projectCode 目录隔离，支持幂等去重）
     *
     * @param imageBytes 图片二进制数据
     * @param originalFileName 原始文件名或相对路径（如 logo.png 或 images/arch.png）
     * @param projectCode 项目代码（用于子目录隔离）
     * @return 图片可访问的相对 URL（如 /upload/docs/{projectCode}/{md5}.png）
     */
    String saveImage(byte[] imageBytes, String originalFileName, String projectCode);

    /**
     * 将 Markdown 文本中的相对图片路径替换为已保存的本地静态资源 URL
     *
     * @param markdownContent 原始 Markdown 文本
     * @param currentDocPath 当前 Markdown 文件在项目/仓库中的相对路径（如 docs/01-guide/test.md）
     * @param imagePathToUrlMap 图片相对路径到本地 URL 的映射表
     * @return 替换后的 Markdown 文本
     */
    String replaceRelativeImages(String markdownContent, String currentDocPath, Map<String, String> imagePathToUrlMap);

    /**
     * 判断给定路径是否为支持的图片文件
     *
     * @param path 文件路径或文件名
     * @return 是否为图片
     */
    boolean isImageFile(String path);

    /**
     * 获取上传根目录的物理绝对路径（自动安全展开 ~ 为 user.home）
     *
     * @return 上传根目录路径（如 C:/Users/xxx/upload）
     */
    String getBaseUploadPath();
}
