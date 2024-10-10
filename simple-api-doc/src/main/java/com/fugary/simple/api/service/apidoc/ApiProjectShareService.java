package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiProjectShare;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
public interface ApiProjectShareService extends IService<ApiProjectShare> {
    /**
     * 按照projectId删除
     *
     * @param projectId
     * @return
     */
    boolean deleteByProject(Integer projectId);

    /**
     * 加载数据
     *
     * @param shareId
     * @return
     */
    ApiProjectShare loadByShareId(String shareId);

    /**
     * 复制数据
     *
     * @param fromProjectId
     * @param toProjectId
     * @param id
     * @return
     */
    int copyProjectShares(Integer fromProjectId, Integer toProjectId, Integer id);
}
