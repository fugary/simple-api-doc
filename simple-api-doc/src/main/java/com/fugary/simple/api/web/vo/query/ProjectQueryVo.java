package com.fugary.simple.api.web.vo.query;

import lombok.Data;

/**
 * Create date 2024/7/15<br>
 *
 * @author gary.fu
 */
@Data
public class ProjectQueryVo extends SimpleQueryVo {

    private static final long serialVersionUID = 7288952527227126289L;
    private Integer projectId;

    private String userName;

    private String groupCode;
}
