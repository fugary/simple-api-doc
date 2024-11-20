package com.fugary.simple.api.web.vo.user;

import com.fugary.simple.api.entity.api.ApiUserGroup;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Create date 2024/11/20<br>
 *
 * @author gary.fu
 */
@Data
public class ApiUserGroupVo implements Serializable {
    private static final long serialVersionUID = 4012310984142509660L;
    private String groupCode;
    private List<ApiUserGroup> userGroups = new ArrayList<>();
}
