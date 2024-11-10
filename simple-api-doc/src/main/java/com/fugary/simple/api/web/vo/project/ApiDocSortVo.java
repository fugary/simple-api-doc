package com.fugary.simple.api.web.vo.project;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode
@Data
public class ApiDocSortVo implements Serializable {

    private Integer docId;
    private Integer folderId;
    private Integer sortId;

}
