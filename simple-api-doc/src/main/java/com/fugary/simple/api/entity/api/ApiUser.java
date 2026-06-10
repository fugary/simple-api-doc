package com.fugary.simple.api.entity.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Created on 2020/5/5 18:37 .<br>
 *
 * @author gary.fu
 */
@Data
public class ApiUser extends ModelBase {

    private static final long serialVersionUID = -1694029740474077017L;
    private String userName;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String userPassword;
    private String userEmail;
    private String nickName;
}
