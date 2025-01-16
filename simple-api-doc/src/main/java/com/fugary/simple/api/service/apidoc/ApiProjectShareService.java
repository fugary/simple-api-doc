package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.web.vo.SimpleResult;

import java.util.Map;

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
     * @param docMappings
     * @return
     */
    int copyProjectShares(Integer fromProjectId, Integer toProjectId, Integer id, Map<Integer, Integer> docMappings);

    /**
     * 复制一份新的数据
     *
     * @param apiProjectShare
     * @return
     */
    SimpleResult<ApiProjectShare> copyProjectShare(ApiProjectShare apiProjectShare);

    /**
     * 密码验证
     *
     * @param password
     * @param apiShare
     * @return
     */
    SimpleResult<ApiUser> validateSharePwd(String password, ApiProjectShare apiShare);
}
