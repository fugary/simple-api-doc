package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.AiConfig;
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

    /** 从已认证的请求中加载有效分享；普通用户请求返回 null。 */
    ApiProjectShare loadCurrentShare();

    /** 当前版本文档是否在分享范围内，且所属项目仍启用。 */
    boolean canReadDoc(ApiDoc doc, ApiProjectShare share);

    /** 加载并校验分享文档，不允许使用其他项目或未分享的文档。 */
    ApiDoc requireDoc(String docId, ApiProjectShare share);

    /** 校验分享 AI 开关，并取得可用的默认模型配置。 */
    AiConfig requireAiConfig(ApiProjectShare share, boolean search);

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
