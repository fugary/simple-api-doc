package com.fugary.simple.api.web.vo.query;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Set;

/**
 * Create date 2024/10/11<br>
 *
 * @author gary.fu
 */
@Builder
@Data
public class ProjectDetailQueryVo implements Serializable {

    private Integer projectId;
    private String projectCode;
    private boolean forceEnabled;
    private boolean includeDocs;
    private boolean includeTasks;
    private boolean includesShares;
    private Set<Integer> docIds;
}
