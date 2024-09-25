package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiProject;

import java.util.List;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
public interface ApiDocService extends IService<ApiDoc> {

    /**
     * 保存ApiDoc列表
     *
     * @param apiDocs
     * @param project
     * @return
     */
    int saveApiDocs(List<? extends ApiDoc> apiDocs, ApiProject project);
}
