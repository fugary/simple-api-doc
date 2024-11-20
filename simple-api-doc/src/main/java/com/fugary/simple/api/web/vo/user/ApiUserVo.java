package com.fugary.simple.api.web.vo.user;

import com.fugary.simple.api.entity.api.ApiUser;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Create date 2024/11/20<br>
 *
 * @author gary.fu
 */
@Data
public class ApiUserVo extends ApiUser {

    private List<ApiGroupVo> groups = new ArrayList<>();
}
