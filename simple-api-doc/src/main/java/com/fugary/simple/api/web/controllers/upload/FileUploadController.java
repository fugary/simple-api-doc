package com.fugary.simple.api.web.controllers.upload;

import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
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

    @Value("${dbs.h2.data-dir:~}")
    private String baseDataDir;

    @PostMapping("/uploadFiles")
    public SimpleResult<List<String>> uploadFiles(HttpServletRequest request) {
        List<MultipartFile> files = SimpleModelUtils.getUploadFiles(request);
        if (files.isEmpty()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2002);
        }
        String filePath = String.join(File.separator, baseDataDir, "upload");
        String baseUrl = request.getRequestURL().toString().replace("uploadFiles", "");
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
     * @param fileName
     * @return
     */
    @GetMapping("/{fileName}")
    public ResponseEntity<InputStreamResource> showFile(@PathVariable("fileName") String fileName) throws FileNotFoundException {
        String filePath = String.join(File.separator, baseDataDir, "upload");
        File file = new File(String.join(File.separator, filePath, fileName));
        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(file.length())
                .body(resource);
    }
}
