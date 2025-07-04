package com.fugary.simple.api.interceptors;

import com.fugary.simple.api.config.SimpleApiConfigProperties;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiLog;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.event.log.OperationLogEvent;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleLogUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.utils.servlet.HttpRequestUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.LoginVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Create date 2024/11/14<br>
 *
 * @author gary.fu
 */
@Component
@Aspect
@Slf4j
public class CrudOperationLogInterceptor implements ApplicationContextAware {

    @Autowired
    private SimpleApiConfigProperties simpleApiConfigProperties;

    private ApplicationContext applicationContext;

    @Pointcut("execution(* com..controllers..*.*(..))")
    public void crudOperation() {
    }

    @Around("crudOperation()")
    public Object proceedingMethod(ProceedingJoinPoint joinpoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        Exception exception = null;
        boolean apiLogEnabled = simpleApiConfigProperties.isApiLogEnabled();
        ApiLog.ApiLogBuilder logBuilder = apiLogEnabled ? initLogBuilder(joinpoint) : null;
        try {
            SimpleLogUtils.setLogBuilder(logBuilder);
            result = joinpoint.proceed();
        } catch (Exception e) {
            exception = e;
        } finally {
            SimpleLogUtils.clearLogBuilder();
        }
        if (apiLogEnabled) {
            processApiLog(logBuilder, joinpoint, startTime, result, exception);
        }
        if (exception != null) {
            throw exception;
        }
        return result;
    }

    protected ApiLog.ApiLogBuilder initLogBuilder(ProceedingJoinPoint joinpoint) {
        HttpServletRequest request = HttpRequestUtils.getCurrentRequest();
        ApiLog.ApiLogBuilder logBuilder = null;
        if (request != null) {
            if (checkNeedLog(joinpoint)) {
                logBuilder = ApiLog.builder()
                        .ipAddress(HttpRequestUtils.getIp(request))
                        .logType(request.getMethod())
                        .headers(JsonUtils.toJson(HttpRequestUtils.getRequestHeadersMap(request)))
                        .requestUrl(HttpRequestUtils.getRequestUrl(request));
            }
        }
        return logBuilder;
    }

    private void processApiLog(ApiLog.ApiLogBuilder logBuilder, ProceedingJoinPoint joinpoint, long startTime, Object result, Exception exception) {
        if (logBuilder != null) {
            MethodSignature signature = (MethodSignature) joinpoint.getSignature();
            Object[] args = joinpoint.getArgs();
            String logName = getLogName(signature);
            ApiUser loginUser = SecurityUtils.getLoginUser();
            Date createDate = new Date();
            logBuilder.logName(logName)
                    .createDate(createDate)
                    .logTime(createDate.getTime() - startTime)
                    .exceptions(exception == null ? null : ExceptionUtils.getStackTrace(exception));
            if (loginUser != null) {
                logBuilder.userName(loginUser.getUserName())
                        .creator(loginUser.getUserName());
            }
            boolean success = exception == null;
            if (result instanceof SimpleResult) {
                SimpleResult<?> simpleResult = ((SimpleResult<?>) result);
                logBuilder.logMessage(simpleResult.getMessage());
                success = simpleResult.isSuccess();
                logBuilder.responseBody(JsonUtils.toJson(simpleResult));
            }
            if (result instanceof ResponseEntity<?>) {
                ResponseEntity<?> responseEntity = ((ResponseEntity<?>) result);
                success = responseEntity.getStatusCode().is2xxSuccessful();
                logBuilder.responseBody(SimpleLogUtils.getResponseBody(responseEntity));
            }
            HttpServletResponse response = HttpRequestUtils.getCurrentResponse();
            if (response != null) {
                logBuilder.responseHeaders(JsonUtils.toJson(HttpRequestUtils.getResponseHeadersMap(response)));
            }
            logBuilder.logResult(success ? ApiDocConstants.SUCCESS : ApiDocConstants.FAIL);
            Pair<Boolean, LoginVo> loginPair = checkLogin(logName, args);
            if (loginPair.getLeft()) {
                LoginVo loginVo = loginPair.getRight();
                logBuilder.userName(loginVo.getUserName());
                if (success) {
                    logBuilder.creator(loginVo.getUserName());
                }
                logBuilder.logData(SimpleModelUtils.logDataString(List.of(loginVo)));
            } else if (!isNotNeedArgs(signature.getMethod().getName())) {
                List<Object> argsList = Arrays.stream(args).filter(this::isValidParam).collect(Collectors.toList());
                logBuilder.logData(SimpleModelUtils.logDataString(argsList));
            }
            ApiLog apiLog = logBuilder.build();
            publishEvent(apiLog);
        }
    }

    private boolean isValidParam(Object target) {
        return target != null && (target.getClass().isPrimitive() || target instanceof Serializable);
    }

    protected boolean isNotNeedArgs(String methodName) {
        return StringUtils.equalsAny(methodName, "proxyApi");
    }

    private boolean checkNeedLog(ProceedingJoinPoint joinpoint) {
        HttpServletRequest request = HttpRequestUtils.getCurrentRequest();
        MethodSignature signature = (MethodSignature) joinpoint.getSignature();
        String methodName = signature.getMethod().getName();
        return request != null && !StringUtils.startsWithAny(methodName, "load", "get", "query", "find", "search", "list")
                && (isNotNeedArgs(methodName) || !HttpMethod.GET.matches(request.getMethod()));
    }

    private Pair<Boolean, LoginVo> checkLogin(String logName, Object[] args) {
        boolean isLogin = false;
        if ("LoginController#login".equals(logName)) {
            isLogin = true;
            Object loginVo = Arrays.stream(args).filter(arg -> arg instanceof LoginVo).findFirst().orElse(null);
            return Pair.of(isLogin, (LoginVo) loginVo);
        }
        return Pair.of(isLogin, null);
    }

    private String getLogName(MethodSignature signature) {
        Class<?> declaringType = signature.getDeclaringType();
        Method method = signature.getMethod();
        String methodName = method.getName();
        return declaringType.getSimpleName() + "#" + methodName;
    }

    private void publishEvent(ApiLog apiLog) {
        applicationContext.publishEvent(new OperationLogEvent(apiLog));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
