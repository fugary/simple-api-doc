package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiDocSchema;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;

import java.util.List;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
public interface ApiDocSchemaService extends IService<ApiDocSchema> {

    /**
     * 按照doc获取
     *
     * @param docId
     * @return
     */
    List<ApiDocSchema> loadByDoc(Integer docId);

    /**
     * 加载schema等详情
     *
     * @param apiDoc
     * @return
     */
    ApiDocDetailVo loadDetailVo(ApiDoc apiDoc);

    /**
     * 加载schema等详情
     *
     * @param apiDocs
     * @return
     */
    List<ApiDocDetailVo> loadDetailList(List<ApiDoc> apiDocs);
    /**
     * 按照doc删除
     *
     * @param docId
     * @return
     */
    boolean deleteByDoc(Integer docId);
}
