package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Create date 2024/9/26<br>
 *
 * @author gary.fu
 */
public interface ApiProjectInfoDetailService extends IService<ApiProjectInfoDetail> {

    /**
     * 加载需要的详细信息
     *
     * @param projectId
     * @param infoId
     * @param types
     * @return
     */
    List<ApiProjectInfoDetail> loadByProjectAndInfo(Integer projectId, Integer infoId, Set<String> types);

    /**
     * 按照projectId删除
     *
     * @param projectId
     * @return
     */
    boolean deleteByProject(Integer projectId);

    /**
     * 按照projectId和infoId删除
     *
     * @param projectId
     * @param infoId
     * @return
     */
    boolean deleteByProjectInfo(Integer projectId, Integer infoId);

    /**
     * 保存项目详细信息
     *
     * @param apiProject         项目基本信息
     * @param apiProjectInfo     项目更多信息
     * @param projectInfoDetails 项目一些schema等详情
     */
    void saveApiProjectInfoDetails(ApiProject apiProject, ApiProjectInfo apiProjectInfo, List<ExportApiProjectInfoDetailVo> projectInfoDetails);

    /**
     * 解析成SchemaMap
     *
     * @param projectInfoDetails
     * @return
     */
    Map<String, ApiProjectInfoDetail> toSchemaKeyMap(List<ApiProjectInfoDetail> projectInfoDetails);

    /**
     * 数据过滤
     *
     * @param projectInfoDetails
     * @param schemaKeyMap
     * @param docDetailVo
     * @return
     */
    List<ApiProjectInfoDetail> filterByDocDetail(List<ApiProjectInfoDetail> projectInfoDetails, Map<String, ApiProjectInfoDetail> schemaKeyMap, ApiDocDetailVo docDetailVo);
}
