package com.fugary.simple.api.web.vo.query;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Create date 2024/8/8<br>
 *
 * @author gary.fu
 */
@Data
public class JwtParamVo implements Serializable {

    private String issuer;
    private String payload;
    private String secret;
    private String algorithm;
    private Date expireTime;

}
