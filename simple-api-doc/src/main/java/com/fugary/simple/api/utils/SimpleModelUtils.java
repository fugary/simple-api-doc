package com.fugary.simple.api.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.utils.servlet.HttpRequestUtils;
import com.fugary.simple.api.web.vo.NameValue;
import com.fugary.simple.api.web.vo.NameValueObj;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectShareVo;
import com.fugary.simple.api.web.vo.query.ApiParamsVo;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Schema;
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
import java.util.stream.Collectors;
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
     * 混淆密码使用的pattern
     */
    public static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)((password|secret)\\\\?\"):\\s*(\\\\?\")[^\"\\\\]+(\\\\?\")");

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
                    target.setCreator(Objects.requireNonNullElse(target.getCreator(), loginUser.getUserName()));
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
     * 合并现有数据
     * @param model
     * @param existsModel
     */
    public static void mergeAuditInfo(ModelBase model, ModelBase existsModel) {
        if (existsModel != null) {
            SimpleModelUtils.addAuditInfo(existsModel);
            model.setCreator(existsModel.getCreator());
            model.setCreateDate(existsModel.getCreateDate());
            model.setModifier(existsModel.getModifier());
            model.setModifyDate(existsModel.getModifyDate());
        }
    }

    /**
     * 合并现有数据
     * @param model
     * @param existsModel
     */
    public static void mergeCreateInfo(ModelBase model, ModelBase existsModel) {
        if (existsModel != null) {
            model.setCreator(existsModel.getCreator());
            model.setCreateDate(existsModel.getCreateDate());
        }
    }

    /**
     * 清理并创建数据
     * @param target
     */
    public static void cleanCreateModel(ModelBase target) {
        target.setId(null);
        removeAuditInfo(target);
        addAuditInfo(target);
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
        if (from == null) {
            return null;
        }
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
        apiProjectInfo.setOasVersion("3.1.0");
        apiProjectInfo.setSpecVersion(SpecVersion.V31.name());
        apiProjectInfo.setVersion("v1.0.0");
        return apiProjectInfo;
    }

    /**
     * 计算默认的info信息
     *
     * @param detailVo
     * @param infoIds
     * @return
     */
    public static List<ApiProjectInfo> filterApiProjectInfo(ApiProjectDetailVo detailVo, Set<Integer> infoIds) {
        final Set<Integer> wrapInfoIds = SimpleModelUtils.wrap(infoIds);
        List<ApiProjectInfo> projectInfos = detailVo.getInfoList().stream().filter(info -> wrapInfoIds.contains(info.getId())).collect(Collectors.toList());
        if (projectInfos.isEmpty()) {
            projectInfos.add(SimpleModelUtils.getDefaultProjectInfo(detailVo));
        }
        return projectInfos;
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
     * 处理参数解析为String，并且特殊处理密码混淆
     * @param argsList
     * @return
     */
    public static String logDataString(List<Object> argsList) {
        String resultStr = StringUtils.EMPTY;
        if (argsList.size() == 1) {
            Object arg = argsList.get(0);
            resultStr = (arg.getClass().isPrimitive() || arg instanceof String) ? arg.toString() : JsonUtils.toJson(arg);
        } else if (argsList.size() > 1) {
            resultStr = JsonUtils.toJson(argsList);
        }
        resultStr = PASSWORD_PATTERN.matcher(resultStr).replaceAll("$1:$3***$4");
        return resultStr;
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

    /**
     * 处理Components
     *
     * @param apiDocDetail
     * @param specVersion
     * @param schemasMap
     */
    public static void processComponents(ApiDocDetailVo apiDocDetail, SpecVersion specVersion, Map<String, Schema<?>> schemasMap) {
        List<ApiProjectInfoDetail> componentSchemas = apiDocDetail.getProjectInfoDetail().getComponentSchemas();
        componentSchemas.forEach(detail -> {
            if (!schemasMap.containsKey(detail.getSchemaName())) {
                Schema<?> schema = SchemaJsonUtils.fromJson(detail.getSchemaContent(), Schema.class, SchemaJsonUtils.isV31(specVersion));
                if (schema != null) {
                    schemasMap.put(detail.getSchemaName(), SchemaJsonUtils.getSchema(schema, schemasMap));
                }
            }
        });
    }

    /**
     * 包装list
     *
     * @param list
     * @param <T>
     * @return
     */
    public static <T> List<T> wrap(List<T> list) {
        if (list == null) {
            return new ArrayList<>();
        }
        return list;
    }

    /**
     * 包装list
     *
     * @param set
     * @param <T>
     * @return
     */
    public static <T> Set<T> wrap(Set<T> set) {
        if (set == null) {
            return new HashSet<>();
        }
        return set;
    }

    /**
     * 包装map
     *
     * @param map
     * @param <K>
     * @param <V>
     * @return
     */
    public static <K, V> Map<K, V> wrap(Map<K, V> map) {
        if (map == null) {
            return new HashMap<>();
        }
        return map;
    }

    /**
     * index计算
     *
     * @param items
     * @param item
     * @return
     * @param <T>
     */
    public static <T> int indexOf(List<T> items, T item, Comparator<T> comparator) {
        if (items == null || comparator == null) {
            return -1;
        }
        for (int i = 0; i < items.size(); i++) {
            if (comparator.compare(items.get(i), item) == 0) {
                return i;
            }
        }
        return -1;
    }
}
