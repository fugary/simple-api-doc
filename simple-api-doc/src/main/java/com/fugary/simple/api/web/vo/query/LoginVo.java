package com.fugary.simple.api.web.vo.query;

import lombok.Data;

import java.io.Serializable;

/**
 * Create date 2024/9/27<br>
 *
 * @author gary.fu
 */
@Data
public class LoginVo implements Serializable {

    private static final long serialVersionUID = -4972465034740164705L;
    private String userName;
    private String userPassword;
}
