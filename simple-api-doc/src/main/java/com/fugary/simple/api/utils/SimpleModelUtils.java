package com.fugary.simple.api.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.utils.servlet.HttpRequestUtils;
import com.fugary.simple.api.web.vo.NameValue;
import com.fugary.simple.api.web.vo.NameValueObj;
import com.fugary.simple.api.web.vo.project.ApiProjectShareVo;
import com.fugary.simple.api.web.vo.query.ApiParamsVo;
import io.swagger.v3.oas.models.SpecVersion;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.fugary.simple.api.utils.servlet.HttpRequestUtils.getBodyResource;

/**
 * Created on 2020/5/6 9:06 .<br>
 *
 * @author gary.fu
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SimpleModelUtils {
    /**
     * 生成uuid
     *
     * @return
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * @param target
     * @param <T>
     */
    public static <T extends ModelBase> T addAuditInfo(T target) {
        Date currentDate = new Date();
        if (target != null) {
            ApiUser loginUser = SecurityUtils.getLoginUser();
            if (target.getId() == null) {
                target.setCreateDate(Objects.requireNonNullElse(target.getCreateDate(), currentDate));
                if (loginUser != null) {
                    target.setCreator(loginUser.getUserName());
                }
            } else {
                target.setModifyDate(currentDate);
                if (loginUser != null) {
                    target.setModifier(loginUser.getUserName());
                }
            }
        }
        return target;
    }

    /**
     * 清理审计信息，数据比较多时可以减少返回数据
     * @param target
     */
    public static void removeAuditInfo(ModelBase target) {
        if (target != null) {
            target.setCreator(null);
            target.setCreateDate(null);
            target.setModifier(null);
            target.setModifyDate(null);
        }
    }

    /**
     * 计算保存为json的头信息
     *
     * @param headers
     * @return
     */
    public static HttpHeaders calcHeaders(String headers) {
        if (StringUtils.isNotBlank(headers)) {
            List<Map<String, String>> headerList = JsonUtils.fromJson(headers, List.class);
            HttpHeaders httpHeaders = new HttpHeaders();
            headerList.stream().forEach(map -> httpHeaders.add(map.get("name"), map.get("value")));
            return httpHeaders;
        }
        return null;
    }

    /**
     * 是否是可用proxyUrl
     * @param proxyUrl
     * @return
     */
    public static boolean isValidProxyUrl(String proxyUrl) {
        return StringUtils.isNotBlank(proxyUrl) && proxyUrl.matches("https?://.*");
    }

    /**
     * 过滤部分header请求
     * @return
     */
    public static Set<String> getExcludeHeaders(){
        List<String> list = Arrays.asList(
            HttpHeaders.HOST.toLowerCase(),
            HttpHeaders.ORIGIN.toLowerCase(),
            HttpHeaders.REFERER.toLowerCase()
        );
        return new HashSet<>(list);
    }

    /**
     * 判断是否是需要过滤
     * @return
     */
    public static boolean isExcludeHeaders(String headerName){
        headerName = StringUtils.trimToEmpty(headerName).toLowerCase();
        return getExcludeHeaders().contains(headerName)
                || headerName.matches("^(sec-|simple-).*");
    }

    /**
     * 清理cors相关的头信息，代理时使用自己的头信息
     * @param response ResponseEntity
     */
    public static <T> ResponseEntity<T> removeProxyHeaders(ResponseEntity<T> response) {
        if (response != null) {
            HttpHeaders headers = new HttpHeaders();
            response.getHeaders().forEach((headerName, value) -> {
                if (!StringUtils.startsWithIgnoreCase(headerName, "access-control-")
                        && !StringUtils.equalsIgnoreCase(HttpHeaders.CONNECTION, headerName)) {
                    headers.addAll(headerName, value);
                }
            });
            return new ResponseEntity<>(response.getBody(), headers, response.getStatusCode());
        }
        return response;
    }

    /**
     * 解析成ApiParamsVo
     *
     * @param request
     * @return
     */
    public static ApiParamsVo toApiParams(HttpServletRequest request) {
        ApiParamsVo apiParams = new ApiParamsVo();
        String pathPrefix = request.getContextPath() + "/\\w+/proxy(/.*)";
        String requestPath = request.getRequestURI();
        Matcher matcher = Pattern.compile(pathPrefix).matcher(requestPath);
        if (matcher.matches()) {
            requestPath = matcher.group(1);
        }
        String targetUrl = request.getHeader(ApiDocConstants.SIMPLE_API_TARGET_URL_HEADER);
        if (StringUtils.startsWith(targetUrl, "//")) { // 没有协议
            targetUrl = request.getScheme() + ":" + targetUrl;
        }
        apiParams.setTargetUrl(targetUrl);
        apiParams.setRequestPath(requestPath);
        apiParams.setMethod(request.getMethod());
        Enumeration<String> headerNames = request.getHeaderNames();
        List<NameValue> headers = apiParams.getHeaderParams();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            boolean excludeHeader = SimpleModelUtils.getExcludeHeaders().contains(headerName.toLowerCase());
            if (!excludeHeader) {
                excludeHeader = SimpleModelUtils.isExcludeHeaders(headerName.toLowerCase());
            }
            if (!excludeHeader && StringUtils.isNotBlank(headerValue)) {
                headers.add(new NameValue(headerName, headerValue));
            }
        }
        headers.add(new NameValue(ApiDocConstants.SIMPLE_API_DEBUG_HEADER, "1"));
        Enumeration<String> parameterNames = request.getParameterNames();
        List<NameValue> parameters = apiParams.getRequestParams();
        List<NameValue> formUrlencoded = apiParams.getFormUrlencoded();
        List<NameValueObj> formData = apiParams.getFormData();
        boolean isUrlencoded = HttpRequestUtils.isCompatibleWith(request, MediaType.APPLICATION_FORM_URLENCODED);
        boolean isFormData = HttpRequestUtils.isCompatibleWith(request, MediaType.MULTIPART_FORM_DATA);
        if (isFormData) {
            MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
            multipartRequest.getFileNames().forEachRemaining(fieldName -> {
                formData.add(new NameValueObj(fieldName, multipartRequest.getFiles(fieldName)));
            });
            multipartRequest.getParameterMap().keySet().forEach(paramName -> {
                String paramValue = multipartRequest.getParameter(paramName);
                if (StringUtils.isNotBlank(paramValue)) {
                    formData.add(new NameValueObj(paramName, paramValue));
                }
            });
        } else if (isUrlencoded) {
            while (parameterNames.hasMoreElements()) {
                String parameterName = parameterNames.nextElement();
                String parameterValue = request.getParameter(parameterName);
                if (StringUtils.isNotBlank(parameterValue)) {
                    formUrlencoded.add(new NameValue(parameterName, parameterValue));
                }
            }
        } else {
            while (parameterNames.hasMoreElements()) {
                String parameterName = parameterNames.nextElement();
                String parameterValue = request.getParameter(parameterName);
                if (StringUtils.isNotBlank(parameterValue)) {
                    parameters.add(new NameValue(parameterName, parameterValue));
                }
            }
        }
        apiParams.setContentType(request.getContentType());
        try {
            apiParams.setRequestBody(StreamUtils.copyToString(getBodyResource(request).getInputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("Body解析错误", e);
        }
        return apiParams;
    }

    /**
     * 获取上传文件信息
     *
     * @param request
     * @return
     */
    public static List<MultipartFile> getUploadFiles(HttpServletRequest request) {
        if (request instanceof MultipartHttpServletRequest) {
            MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
            List<MultipartFile> files = multipartRequest.getFiles("files");
            if (CollectionUtils.isEmpty(files)) {
                files = multipartRequest.getFiles("file");
            }
            return files;
        }
        return new ArrayList<>();
    }

    /**
     * 转换成ShareVo
     * @param apiShare
     * @return
     */
    public static ApiProjectShareVo toShareVo(ApiProjectShare apiShare){
        ApiProjectShareVo shareVo = new ApiProjectShareVo();
        SimpleModelUtils.copy(apiShare, shareVo);
        shareVo.setNeedPassword(StringUtils.isNotBlank(apiShare.getSharePassword()));
        if (apiShare.getExpireDate() != null) {
            shareVo.setExpired(new Date().after(apiShare.getExpireDate()));
        }
        if (apiShare.getShowChildrenLength() == null) {
            shareVo.setShowChildrenLength(true);
        }
        if (apiShare.getShowTreeIcon() == null) {
            shareVo.setShowTreeIcon(true);
        }
        return shareVo;
    }

    /**
     * 复制非空数据
     *
     * @param source
     * @param target
     */
    public static void copyNoneNullValue(Object source, Object target) {
        if (target != null) {
            Stream.of(FieldUtils.getAllFields(source.getClass())).forEach(field -> {
                field.setAccessible(true);
                try {
                    Field targetField = FieldUtils.getField(target.getClass(), field.getName(), true);
                    if (targetField != null) {
                        targetField.setAccessible(true);
                        Object value = targetField.get(target);
                        if (value == null || (value instanceof String && StringUtils.isBlank((String) value))) {
                            targetField.set(target, field.get(source));
                        }
                    }
                } catch (IllegalAccessException e) {
                    log.error("copy属性错误", e);
                }
            });
        }
    }

    /**
     * 复制属性
     *
     * @param from
     * @param to
     * @return
     * @param <T>
     * @param <S>
     */
    public static <T, S> T copy(S from, T to) {
        try {
            BeanUtils.copyProperties(from, to);
        } catch (Exception e) {
            log.error("copy属性错误", e);
        }
        return to;
    }

    /**
     * 复制属性
     *
     * @param from
     * @param to
     * @return
     * @param <T>
     * @param <S>
     */
    public static <T, S> T copy(S from, Class<T> to) {
        Constructor<T> constructor = null;
        T target = null;
        try {
            constructor = to.getConstructor();
            target = constructor.newInstance();
            copy(from, target);
        } catch (Exception e) {
            log.error("copy属性错误", e);
        }
        return target;
    }

    /**
     * 获取临时文件路径
     * @param prefix
     * @param uuid
     * @param type
     * @return
     */
    public static String getFileFullPath(String prefix, String uuid, String type) {
        return getFileFullPath(prefix, uuid + "." + type);
    }

    /**
     * 获取临时文件路径
     * @param prefix
     * @param fileName
     * @return
     */
    public static String getFileFullPath(String prefix, String fileName) {
        String filePath = StringUtils.join(List.of(FileUtils.getTempDirectoryPath(), prefix), File.separator);
        File fileDir = new File(filePath);
        if (!fileDir.exists()) {
            try {
                FileUtils.forceMkdir(fileDir);
            } catch (IOException e) {
                log.error("创建临时文件夹失败", e);
            }
        }
        return StringUtils.join(List.of(filePath, fileName), File.separator);
    }

    /**
     * 获取一个新的ProjectInfo
     * @return
     */
    public static ApiProjectInfo getDefaultProjectInfo(ApiProject project){
        ApiProjectInfo apiProjectInfo = new ApiProjectInfo();
        apiProjectInfo.setProjectId(project.getId());
        apiProjectInfo.setOasVersion("3.0.1");
        apiProjectInfo.setSpecVersion(SpecVersion.V30.name());
        apiProjectInfo.setVersion("1.0.0");
        return apiProjectInfo;
    }

    /**
     * 计算指定文档的分享ID
     * @param shareDocs
     * @return
     */
    public static Set<Integer> getShareDocIds(String shareDocs){
        Set<Integer> docIds = new HashSet<>();
        if (StringUtils.isNotBlank(shareDocs)) {
            docIds = JsonUtils.fromJson(shareDocs, new TypeReference<>() {
            });
        }
        return docIds;
    }

    /**
     * 计算父级相关folderIds
     * @param folderId
     * @param folders
     * @param folderIds
     * @return
     */
    public static Set<Integer> calcFolderIds(Integer folderId, List<ApiFolder> folders, Set<Integer> folderIds) {
        folderIds = folderIds == null ? new HashSet<>() : folderIds;
        if (folderId != null) {
            folderIds.add(folderId);
            for (ApiFolder folder : folders) {
                if (folderId.equals(folder.getId()) && !folderIds.contains(folder.getParentId())) {
                    calcFolderIds(folder.getParentId(), folders, folderIds);
                }
            }
        }
        return folderIds;
    }
}
