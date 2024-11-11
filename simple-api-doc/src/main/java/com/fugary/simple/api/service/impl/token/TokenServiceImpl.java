package com.fugary.simple.api.service.impl.token;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fugary.simple.api.config.SimpleApiConfigProperties;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.service.token.TokenService;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Created on 2020/5/5 20:46 .<br>
 *
 * @author gary.fu
 */
@Slf4j
@Component
public class TokenServiceImpl implements TokenService {

    public static final String DEFAULT_ISSUER = "simple-api";
    public static final String USER_NAME_KEY = "name";

    @Autowired
    private SimpleApiConfigProperties simpleApiConfigProperties;

    @Override
    public String createToken(ApiUser user) {
        if (user != null) {
            Date currentDate = new Date();
            Date expireDate = DateUtils.addDays(currentDate, simpleApiConfigProperties.getJwtExpire());
            try {
                Algorithm algorithm = getAlgorithm();
                return JWT.create()
                        .withIssuer(DEFAULT_ISSUER)
                        .withClaim(USER_NAME_KEY, user.getUserName())
                        .withExpiresAt(expireDate)
                        .sign(algorithm);
            } catch (Exception e) {
                log.error("生成Jwt Token异常", e);
            }
        }
        return null;
    }

    @Override
    public ApiUser fromAccessToken(String accessToken) {
        DecodedJWT decoded = getDecoded(accessToken);
        if (decoded != null) {
            return toApiUser(decoded);
        }
        return null;
    }

    @Override
    public SimpleResult<ApiUser> validate(String accessToken) {
        int errorCode = SystemErrorConstants.CODE_401;
        try {
            DecodedJWT decoded = getDecoded1(accessToken);
            ApiUser user = toApiUser(decoded);
            return SimpleResultUtils.createSimpleResult(user);
        } catch (JWTVerificationException e) {
            // Invalid signature/claims
            log.debug("Token Error", e);
        }
        return SimpleResultUtils.createSimpleResult(errorCode);
    }

    protected ApiUser toApiUser(DecodedJWT decoded) {
        String userName = decoded.getClaim(USER_NAME_KEY).asString();
        ApiUser user = null;
        if (StringUtils.isNotBlank(userName)) {
            user = new ApiUser();
            user.setUserName(userName);
        }
        return user;
    }

    protected DecodedJWT getDecoded(String accessToken) {
        try {
            return getDecoded1(accessToken);
        } catch (JWTVerificationException e) {
            // Invalid signature/claims
            log.error("Token验证没通过", e);
        }
        return null;
    }

    protected DecodedJWT getDecoded1(String accessToken) {
        JWTVerifier verifier = JWT.require(getAlgorithm())
                // specify an specific claim validations
                .withIssuer(DEFAULT_ISSUER)
                // reusable verifier instance
                .build();
        return verifier.verify(accessToken);
    }

    protected Algorithm getAlgorithm() {
        if (StringUtils.isBlank(simpleApiConfigProperties.getJwtPassword())) {
            simpleApiConfigProperties.setJwtPassword(RandomStringUtils.randomAlphanumeric(16));
        }
        return Algorithm.HMAC512(simpleApiConfigProperties.getJwtPassword());
    }
}
