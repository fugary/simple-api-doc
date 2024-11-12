package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;

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
     * 查询文档列表
     *
     * @param projectId
     * @return
     */
    List<ApiDoc> loadByProject(Integer projectId, boolean content);

    /**
     * 查询可用文档列表
     *
     * @param projectId
     * @return
     */
    List<ApiDoc> loadEnabledByProject(Integer projectId, boolean content);

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
     * 保存ApiDoc
     * @param apiDoc
     * @return
     */
    boolean saveApiDoc(ApiDoc apiDoc, ApiDoc existsDoc);

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

    /**
     * 复制文档
     *
     * @param fromProjectId
     * @param toProjectId
     * @param foldersMap
     * @param infosMap
     * @return
     */
    int copyProjectDocs(Integer fromProjectId, Integer toProjectId,
                        Map<Integer, Pair<ApiFolder, ApiFolder>> foldersMap,
                        Map<Integer, Pair<ApiProjectInfo, ApiProjectInfo>> infosMap);

    /**
     * 复制一份ApiDoc，仅Markdown文档
     * @param apiDoc
     * @return
     */
    boolean copyApiDoc(ApiDoc apiDoc);
}
