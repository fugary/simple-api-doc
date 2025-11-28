package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.exports.ExportEnvConfigVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectImportVo;
import com.fugary.simple.api.web.vo.query.ProjectDetailQueryVo;

import java.util.List;

/**
 * Create date 2024/7/15<br>
 *
 * @author gary.fu
 */
public interface ApiProjectService extends IService<ApiProject> {

    /**
     * 加载project详情
     *
     * @param queryVo  项目查询条件
     * @return
     */
    ApiProjectDetailVo loadProjectVo(ProjectDetailQueryVo queryVo);

    /**
     * 级联删除
     *
     * @param id
     * @return
     */
    boolean deleteApiProject(Integer id);

    /**
     * 级联删除
     *
     * @param ids
     * @return
     */
    boolean deleteApiProjects(List<Integer> ids);

    /**
     * 检查是否有重复
     *
     * @param project
     * @return
     */
    boolean existsApiProject(ApiProject project);

    /**
     * 验证用户Project是否有权限
     *
     * @param projectId
     * @return
     */
    boolean validateUserProject(Integer projectId);

    /**
     * 保存ApiProject
     *
     * @param project
     * @return
     */
    boolean saveProject(ApiProject project);

    /**
     * 解析成ExportVo对象
     *
     * @param content
     * @param importVo
     * @return
     */
    SimpleResult<ExportApiProjectVo> processImportProject(String content, ApiProjectImportVo importVo);

    /**
     * 导入新ApiProject对象
     *
     * @param exportVo
     * @param importVo
     * @return
     */
    SimpleResult<ApiProject> importNewProject(ExportApiProjectVo exportVo, ApiProjectImportVo importVo);

    /**
     * 更新现有ApiProject
     *
     * @param currentProject
     * @param exportVo
     * @param importVo
     * @return
     */
    SimpleResult<ApiProject> importUpdateProject(ApiProject currentProject, ExportApiProjectVo exportVo, ApiProjectImportVo importVo);

    /**
     * 复制项目
     *
     * @param project
     * @return
     */
    SimpleResult<ApiProject> copyProject(ApiProject project);

    /**
     * 环境保存
     *
     * @param apiProject
     * @param envConfigs
     * @return
     */
    boolean saveEnvConfigs(ApiProject apiProject, List<ExportEnvConfigVo> envConfigs);

    /**
     * 获取或创建ProjectInfo信息
     *
     * @param apiDoc
     * @return
     */
    ApiProjectInfo findOrCreateProjectInfo(ApiDoc apiDoc);

    /**
     * 获取或创建ProjectInfo信息
     *
     * @param projectId
     * @return
     */
    ApiProjectInfo findOrCreateProjectInfo(Integer projectId);
}
