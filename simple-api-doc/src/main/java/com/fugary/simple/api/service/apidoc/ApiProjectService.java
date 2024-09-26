package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectDetailVo;
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
     * @param projectCode
     * @return
     */
    ApiProjectDetailVo loadProjectVo(String projectCode);

    /**
     * 级联删除
     *
     * @param id
     * @return
     */
    boolean deleteApiProject(Integer id);

    /**
     * 检查是否有重复
     *
     * @param project
     * @return
     */
    boolean existsApiProject(ApiProject project);

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
