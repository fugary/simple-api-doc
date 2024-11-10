package com.fugary.simple.api.web.vo.project;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ApiDocConfigSortsVo implements Serializable {

    private static final long serialVersionUID = 8931954445034540839L;
    private Integer projectId;
    private Integer folderId;
    private List<ApiDocSortVo> sorts;

}
