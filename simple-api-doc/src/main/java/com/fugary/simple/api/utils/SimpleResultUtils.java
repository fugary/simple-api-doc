package com.fugary.simple.api.utils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.SimplePage;
import com.fugary.simple.api.web.vo.query.SimpleQueryVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Created on 2020/5/4 11:05 .<br>
 *
 * @author gary.fu
 */
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
}
