package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiDoc;

import java.util.List;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
public interface ApiDocService extends IService<ApiDoc> {

    /**
     * 查询文档列表
     *
     * @param projectId
     * @return
     */
    List<ApiDoc> loadByProject(Integer projectId);

    /**
     * 查询可用文档列表
     *
     * @param projectId
     * @return
     */
    List<ApiDoc> loadEnabledByProject(Integer projectId);

    /**
     * 按照projectId删除，级联删除
     *
     * @param projectId
     * @return
     */
    boolean deleteByProject(Integer projectId);

    /**
     * 按照folderId删除，级联删除
     *
     * @param folderId
     * @return
     */
    boolean deleteByFolder(Integer folderId);

    /**
     * 指定id删除doc，级联删除
     *
     * @param docId
     * @return
     */
    boolean deleteDoc(Integer docId);

    /**
     * 是否已经存在
     * @param doc
     * @return
     */
    boolean existsApiDoc(ApiDoc doc);
}
