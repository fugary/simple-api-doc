package com.fugary.simple.api.web.vo.exports;

import com.fugary.simple.api.entity.api.ApiProject;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Data
public class ExportApiProjectVo extends ApiProject {

    private static final long serialVersionUID = -8612077469215078772L;
    private ExportApiProjectInfoVo projectInfo;
    private List<ExportApiProjectInfoDetailVo> projectInfoDetails = new ArrayList<>();
    private List<ExportApiFolderVo> folders = new ArrayList<>();
    private List<ExportApiDocVo> docs = new ArrayList<>();

}
