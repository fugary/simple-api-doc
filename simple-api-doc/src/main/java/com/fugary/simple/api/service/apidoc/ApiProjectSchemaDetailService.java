package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectSchemaDetail;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectSchemaDetailVo;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectSchemaVo;

import java.util.List;

/**
 * Create date 2024/9/26<br>
 *
 * @author gary.fu
 */
public interface ApiProjectSchemaDetailService extends IService<ApiProjectSchemaDetail> {

    /**
     * 按照projectId删除
     *
     * @param projectId
     * @return
     */
    boolean deleteByProject(Integer projectId);

    /**
     * 保存导入数据
     *
     * @param projectSchemaDetails
     */
    void saveApiProjectSchema(List<ExportApiProjectSchemaDetailVo> projectSchemaDetails, ApiProject apiProject);

}
