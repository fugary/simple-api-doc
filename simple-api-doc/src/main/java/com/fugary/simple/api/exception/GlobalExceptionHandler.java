package com.fugary.simple.api.exception;

import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Created by gary.fu on 2024/8/20.
 */
@Slf4j
@Component
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ResponseBody
	@ExceptionHandler({Exception.class})
	public <T> SimpleResult<T> exceptionHandler(Exception e) {
		log.error("全局异常处理", e);
		return SimpleResultUtils.createError(ExceptionUtils.getMessage(e));
	}

	@ResponseBody
	@ExceptionHandler({SimpleRuntimeException.class})
	public <T> SimpleResult<T> exceptionHandler(SimpleRuntimeException e) {
		log.error("全局异常处理", e);
		return SimpleResultUtils.createSimpleResult(e.getCode().intValue());
	}
}
