package com.fugary.simple.api.web.vo.imports;

import lombok.Data;

import java.io.Serializable;

/**
 * Create date 2024/9/24<br>
 *
 * @author gary.fu
 */
@Data
public class BasicAuthVo implements Serializable {

    private static final long serialVersionUID = -6922119865221362125L;
    private String userName;
    private String userPassword;
}
