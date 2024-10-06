package com.fugary.simple.api.utils;

import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.entity.api.ModelBase;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.utils.servlet.HttpRequestUtils;
import com.fugary.simple.api.web.vo.NameValue;
import com.fugary.simple.api.web.vo.NameValueObj;
import com.fugary.simple.api.web.vo.query.ApiParamsVo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Stream;

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
                || headerName.matches("^(sec-|mock-).*");
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
     * @param paramVo
     * @param request
     * @return
     */
    public static ApiParamsVo toApiParams(ApiParamsVo paramVo, HttpServletRequest request) {
        List<NameValueObj> formData = paramVo.getFormData();
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
        } else if (!isUrlencoded) {
            Enumeration<String> parameterNames = request.getParameterNames();
            List<NameValue> parameters = paramVo.getRequestParams();
            while (parameterNames.hasMoreElements()) {
                String parameterName = parameterNames.nextElement();
                String parameterValue = request.getParameter(parameterName);
                if (StringUtils.isNotBlank(parameterValue)) {
                    parameters.add(new NameValue(parameterName, parameterValue));
                }
            }
        }
        return paramVo;
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
}
