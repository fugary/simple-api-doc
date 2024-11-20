package com.fugary.simple.api.web.vo.user;

import com.fugary.simple.api.entity.api.ApiGroup;
import lombok.Data;

import java.util.List;

/**
 * Create date 2024/11/20<br>
 *
 * @author gary.fu
 */
@Data
public class ApiGroupVo extends ApiGroup {

    private static final long serialVersionUID = -8467969521665902738L;
    private List<String> authorities;
}
