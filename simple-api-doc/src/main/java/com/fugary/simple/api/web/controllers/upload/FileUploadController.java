package com.fugary.simple.api.web.controllers.upload;

import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.service.apidoc.asset.DocAssetStorageService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Create date 2024/10/29<br>
 *
 * @author gary.fu
 */
@Slf4j
@RestController
@RequestMapping("/upload")
public class FileUploadController {

    @Autowired
    private DocAssetStorageService docAssetStorageService;

    @PostMapping("/uploadFiles")
    public SimpleResult<List<String>> uploadFiles(HttpServletRequest request) {
        List<MultipartFile> files = SimpleModelUtils.getUploadFiles(request);
        if (files.isEmpty()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2002);
        }
        String filePath = docAssetStorageService.getBaseUploadPath();
        String baseUrl = "/upload/";
        List<String> fileList = files.stream().map(file -> {
            String fileName = SimpleModelUtils.uuid() + "." + FilenameUtils.getExtension(file.getOriginalFilename());
            try {
                FileUtils.forceMkdir(new File(filePath));
                Files.write(Path.of(String.join(File.separator, filePath, fileName)), file.getBytes());
            } catch (IOException e) {
                log.error("文件上传失败", e);
                return null;
            }
            return baseUrl + fileName;
        }).filter(Objects::nonNull).collect(Collectors.toList());
        return SimpleResultUtils.createSimpleResult(fileList);
    }

    /**
     * 显示/下载上传的静态文件（支持多级子目录，如 /upload/docs/{projectCode}/{md5}.png 或 /upload/{fileName}）
     *
     * @param request HTTP 请求
     * @return 文件资源流
     */
    @GetMapping("/**")
    public ResponseEntity<InputStreamResource> showFile(HttpServletRequest request) throws IOException {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = requestUri.substring(contextPath.length());
        String relativePath = StringUtils.substringAfter(path, "/upload/");
        if (StringUtils.isBlank(relativePath)) {
            return ResponseEntity.notFound().build();
        }

        String decodedRelativePath = URLDecoder.decode(relativePath, StandardCharsets.UTF_8);
        String baseUploadPath = docAssetStorageService.getBaseUploadPath();
        File baseDir = new File(baseUploadPath);
        File targetFile = new File(baseDir, decodedRelativePath.replace('/', File.separatorChar));

        // 安全检查：防止路径遍历 (../) 攻击
        if (!targetFile.exists() || !targetFile.getCanonicalPath().startsWith(baseDir.getCanonicalPath())) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(targetFile.getName())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        InputStreamResource resource = new InputStreamResource(new FileInputStream(targetFile));
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(targetFile.length())
                .body(resource);
    }
}
