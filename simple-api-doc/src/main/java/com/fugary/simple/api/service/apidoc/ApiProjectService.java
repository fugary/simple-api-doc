package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.web.vo.imports.ApiProjectDetailVo;

import java.util.List;

/**
 * Create date 2024/7/15<br>
 *
 * @author gary.fu
 */
public interface ApiProjectService extends IService<ApiProject> {

    /**
     * 加载project详情
     * @param projectCode
     * @return
     */
    ApiProjectDetailVo loadProjectVo(String projectCode);

    /**
     * 级联删除分组、请求和数据
     *
     * @param id
     * @return
     */
    boolean deleteMockProject(Integer id);

    /**
     * 级联删除分组、请求和数据
     *
     * @param ids
     * @return
     */
    boolean deleteMockProjects(List<Integer> ids);

    /**
     * 检查是否有重复
     *
     * @param project
     * @return
     */
    boolean existsMockProject(ApiProject project);

}
