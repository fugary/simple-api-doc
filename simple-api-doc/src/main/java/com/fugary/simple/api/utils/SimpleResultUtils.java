package com.fugary.simple.api.utils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.exports.ApiDocExporter;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.ExportDownloadVo;
import com.fugary.simple.api.web.vo.query.SimplePage;
import com.fugary.simple.api.web.vo.query.SimpleQueryVo;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Created on 2020/5/4 11:05 .<br>
 *
 * @author gary.fu
 */
@Slf4j
@Component
public class SimpleResultUtils {

    private static MessageSource messageSource;

    @Autowired
    public void injectMessageSource(MessageSource messageSourceBean) {
        SimpleResultUtils.messageSource = messageSourceBean;
    }

    /**
     * 请求转换成page
     *
     * @param queryVo
     * @param <T>
     * @return
     */
    public static <T> Page<T> toPage(SimpleQueryVo queryVo) {
        SimplePage page = queryVo.getPage();
        int current = 1;
        int size = 10;
        if (page != null) {
            current = Math.toIntExact(page.getPageNumber() < 0 ? 1 : page.getPageNumber());
            size = Math.toIntExact(page.getPageSize() < 0 ? 10 : page.getPageSize());
        }
        return new Page<>(current, size);
    }

    /**
     * 分页输出
     *
     * @param page
     * @return
     */
    public static SimplePage fromPage(Page page) {
        return new SimplePage(page.getCurrent(), page.getSize(), page.getTotal(), page.getPages());
    }

    /**
     * 分页result对象
     *
     * @param data
     * @param <T>
     * @return
     */
    public static <T> SimpleResult<List<T>> createSimpleResult(Page<T> data) {
        if (data == null || data.getRecords() == null) {
            return createSimpleResult(SystemErrorConstants.CODE_404);
        }
        return SimpleResult.<List<T>>builder().resultData(data.getRecords())
                .page(fromPage(data))
                .message(getErrorMsg(SystemErrorConstants.CODE_0)).build();
    }

    /**
     * 简单Result对象
     *
     * @param data
     * @param <T>
     * @return
     */
    public static <T> SimpleResult<T> createSimpleResult(T data) {
        if (data == null) {
            return createSimpleResult(SystemErrorConstants.CODE_404);
        }
        return SimpleResult.<T>builder().resultData(data)
                .message(getErrorMsg(SystemErrorConstants.CODE_0)).build();
    }

    /**
     * 简单Result对象
     *
     * @param data
     * @param <T>
     * @return
     */
    public static <T> SimpleResult<T> createSimpleResult(int code, T data) {
        return SimpleResult.<T>builder().resultData(data)
                .code(code)
                .message(getErrorMsg(code)).build();
    }

    /**
     * 简单Result对象
     *
     * @param result
     * @return
     */
    public static <T> SimpleResult<T> createSimpleResult(SimpleResult<?> result) {
        return SimpleResult.<T>builder()
                .code(result.getCode())
                .message(StringUtils.defaultIfBlank(result.getMessage(), getErrorMsg(result.getCode()))).build();
    }

    /**
     * 简单Result对象
     *
     * @param success
     * @return
     */
    public static <T> SimpleResult<T> createSimpleResult(boolean success) {
        return createSimpleResult(success ? SystemErrorConstants.CODE_0 : SystemErrorConstants.CODE_1);
    }

    /**
     * 简单Result对象
     *
     * @param code
     * @return
     */
    public static <T> SimpleResult<T> createSimpleResult(int code) {
        return SimpleResult.<T>builder()
                .code(code)
                .message(getErrorMsg(code)).build();
    }

    /**
     * 内部异常错误
     *
     * @param msg
     * @return
     */
    public static <T> SimpleResult<T> createError(String msg) {
        return SimpleResult.<T>builder()
                .code(SystemErrorConstants.CODE_500)
                .message(msg).build();
    }

    public static String getErrorMsg(Integer code, Locale locale) {
        String messageKey = "simple.error.code." + code;
        if (messageSource != null) {
            return messageSource.getMessage(messageKey, null, locale);
        }
        return messageKey;
    }

    public static String getErrorMsg(Integer code) {
        return getErrorMsg(code, LocaleContextHolder.getLocale());
    }

    /**
     * 创建临时文件并返回uuid
     *
     * @param apiApiDocExporter
     * @param applicationName
     * @param downloadVo
     * @param projectId
     * @return
     */
    public static String createTempExportFile(ApiDocExporter<OpenAPI> apiApiDocExporter,
                                              ExportDownloadVo downloadVo,
                                              String applicationName,
                                              Integer projectId) {
        String uuid = SimpleModelUtils.uuid();
        String type = StringUtils.defaultIfBlank(downloadVo.getType(), "json");
        OpenAPI openAPI = apiApiDocExporter.export(projectId, downloadVo.getDocIds());
        String content;
        if (StringUtils.equals(type, "json")) {
            content = SchemaJsonUtils.toJson(openAPI, SchemaJsonUtils.isV31(openAPI));
        } else {
            content = SchemaYamlUtils.toYaml(openAPI, SchemaJsonUtils.isV31(openAPI));
        }
        if (downloadVo.isReturnContent()) {
            return content;
        }
        try {
            String filePathName = SimpleModelUtils.getFileFullPath(applicationName, uuid, type);
            Path tempFile = Files.createFile(Path.of(filePathName));
            Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8));
            tempFile.toFile().deleteOnExit();
        } catch (IOException e) {
            log.error("创建临时文件失败", e);
        }
        return uuid;
    }

    /**
     * 构建临时下载文件
     *
     * @param request
     * @param applicationName
     * @param prefixName
     * @param fileName
     * @return
     * @throws IOException
     */
    public static ResponseEntity<InputStreamResource> downloadTempExportFile(HttpServletRequest request,
                                                                             String applicationName,
                                                                             String prefixName,
                                                                             String fileName) throws IOException {
        // 构造临时文件的完整路径
        File tempFile = new File(SimpleModelUtils.getFileFullPath(applicationName, fileName));
        fileName = prefixName + "-" + fileName;
        // 检查文件是否存在
        if (!tempFile.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        // 创建 InputStreamResource 从文件中读取数据
        InputStreamResource resource = new InputStreamResource(new FileInputStream(tempFile));
        Function<HttpServletRequest, Boolean> deleteFileHook = req -> FileUtils.deleteQuietly(tempFile);
        request.setAttribute(ApiDocConstants.SHARE_FILE_DOWNLOAD_HOOK_KEY, deleteFileHook);
        // 设置响应头，准备文件下载
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(tempFile.length())
                .body(resource);
    }
}
