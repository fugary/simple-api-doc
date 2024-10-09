package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectImportVo;

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
     * @param projectCode  项目代码
     * @param forceEnabled 仅启用数据
     * @param includeDocs  是否包含doc等信息
     * @return
     */
    ApiProjectDetailVo loadProjectVo(String projectCode, boolean forceEnabled, boolean includeDocs);

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
}
