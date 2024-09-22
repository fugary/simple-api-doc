package com.fugary.simple.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Created on 2020/5/5 20:43 .<br>
 *
 * @author gary.fu
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "simple.api")
public class SimpleApiConfigProperties {

    private String jwtPassword = "";

    private Integer jwtExpire = 7;
}
