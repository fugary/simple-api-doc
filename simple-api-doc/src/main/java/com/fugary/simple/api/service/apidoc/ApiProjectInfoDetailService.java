package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;

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
     * @param types
     * @return
     */
    List<ApiProjectInfoDetail> loadByProject(Integer projectId, Set<String> types);

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
     * 解析doc详情
     *
     * @param apiInfo
     * @param apiDocDetail
     * @return
     */
    ApiProjectInfoDetailVo parseInfoDetailVo(ApiProjectInfo apiInfo, ApiDocDetailVo apiDocDetail);

    /**
     * 合并数据
     *
     * @param detailVoList
     * @return
     */
    ApiProjectInfoDetailVo mergeInfoDetailVo(List<ApiProjectInfoDetailVo> detailVoList);

    /**
     * 解析doc详情
     *
     * @param apiInfo
     * @param apiInfoDetails
     * @param docDetailList
     * @return
     */
    ApiProjectInfoDetailVo parseInfoDetailVo(ApiProjectInfo apiInfo, List<ApiProjectInfoDetail> apiInfoDetails, List<ApiDocDetailVo> docDetailList);

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
     * @param docDetailList
     * @return
     */
    List<ApiProjectInfoDetail> filterByDocDetail(List<ApiProjectInfoDetail> projectInfoDetails, Map<String, ApiProjectInfoDetail> schemaKeyMap, List<ApiDocDetailVo> docDetailList);

    /**
     * 过滤Component对应有关系的数据
     *
     * @param projectInfoDetails
     * @param infoDetail
     * @return
     */
    List<ApiProjectInfoDetail> filterByInfoDetail(List<ApiProjectInfoDetail> projectInfoDetails, Map<String, ApiProjectInfoDetail> schemaKeyMap, ApiProjectInfoDetail infoDetail);

    /**
     * 查询关联数据
     *
     * @param infoDetail
     * @return
     */
    List<ApiProjectInfoDetail> findRelatedInfoDetails(ApiProjectInfoDetail infoDetail);

    /**
     * 检查是否有重复
     *
     * @param infoDetail
     * @return
     */
    boolean existsInfoDetail(ApiProjectInfoDetail infoDetail);
}
