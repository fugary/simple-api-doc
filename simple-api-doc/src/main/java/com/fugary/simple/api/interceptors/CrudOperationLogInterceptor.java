package com.fugary.simple.api.interceptors;

import com.fugary.simple.api.config.SimpleApiConfigProperties;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiLog;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.event.log.OperationLogEvent;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.utils.servlet.HttpRequestUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.LoginVo;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
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
        try {
            result = joinpoint.proceed();
        } catch (Exception e) {
            exception = e;
        }
        if (simpleApiConfigProperties.isApiLogEnabled()) {
            processApiLog(joinpoint, startTime, result, exception);
        }
        if (exception != null) {
            throw exception;
        }
        return result;
    }

    private void processApiLog(ProceedingJoinPoint joinpoint, long startTime, Object result, Exception exception) {
        MethodSignature signature = (MethodSignature) joinpoint.getSignature();
        Object[] args = joinpoint.getArgs();
        HttpServletRequest request = HttpRequestUtils.getCurrentRequest();
        log.info("{}/{}", args, result);
        if (request != null && !HttpMethod.GET.matches(request.getMethod())) {
            String logName = getLogName(signature);
            log.info("{}", request.getMethod());
            ApiUser loginUser = SecurityUtils.getLoginUser();
            Date createDate = new Date();
            ApiLog.ApiLogBuilder logBuilder = ApiLog.builder()
                    .ipAddress(HttpRequestUtils.getIp(request))
                    .logName(logName)
                    .logType(request.getMethod())
                    .createDate(createDate)
                    .logTime(createDate.getTime() - startTime)
                    .exceptions(exception == null ? null : ExceptionUtils.getStackTrace(exception));
            if (loginUser != null) {
                logBuilder.userName(loginUser.getUserName())
                        .creator(loginUser.getUserName());
            }
            boolean success = exception == null;
            if (result instanceof SimpleResult) {
                SimpleResult simpleResult = ((SimpleResult<?>) result);
                logBuilder.logMessage(simpleResult.getMessage());
                success = simpleResult.isSuccess();
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
            } else {
                List<Object> argsList = Arrays.stream(args).filter(this::isValidParam).collect(Collectors.toList());
                logBuilder.logData(SimpleModelUtils.logDataString(argsList));
            }
            ApiLog apiLog = logBuilder.build();
            log.info("{}", apiLog);
            publishEvent(apiLog);
        }
    }

    private boolean isValidParam(Object target) {
        return target != null && (target.getClass().isPrimitive() || target instanceof Serializable);
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
