package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiDocSchema;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.mapper.api.ApiDocMapper;
import com.fugary.simple.api.service.apidoc.ApiDocSchemaService;
import com.fugary.simple.api.service.apidoc.ApiDocService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Service
public class ApiDocServiceImpl extends ServiceImpl<ApiDocMapper, ApiDoc> implements ApiDocService {

    @Autowired
    private ApiDocSchemaService apiDocSchemaService;

    @Override
    public List<ApiDoc> loadByProject(Integer projectId) {
        return this.list(Wrappers.<ApiDoc>query().eq("project_id", projectId).orderByAsc("sort_id"));
    }

    @Override
    public List<ApiDoc> loadEnabledByProject(Integer projectId) {
        return this.list(Wrappers.<ApiDoc>query().eq("project_id", projectId)
                .eq(ApiDocConstants.STATUS_KEY, ApiDocConstants.STATUS_ENABLED)
                .orderByAsc("sort_id"));
    }

    @Override
    public boolean deleteByProject(Integer projectId) {
        //select * from T_API_DOC_SCHEMA where exists (select 1 from t_api_doc d where d.project_id=0 and d.id=t_api_doc_schema.doc_id)
        apiDocSchemaService.remove(Wrappers.<ApiDocSchema>query()
                .exists("select 1 from t_api_doc d where d.project_id = {0} and d.id = t_api_doc_schema.doc_id", projectId));
        return this.remove(Wrappers.<ApiDoc>query().eq("project_id", projectId));
    }

    @Override
    public boolean deleteByFolder(Integer folderId) {
        //select * from T_API_DOC_SCHEMA where exists (select 1 from t_api_doc d where d.folder_id=0 and d.id=t_api_doc_schema.doc_id)
        apiDocSchemaService.remove(Wrappers.<ApiDocSchema>query()
                .exists("select 1 from t_api_doc d where d.folder_id = {0} and d.id = t_api_doc_schema.doc_id", folderId));
        return this.remove(Wrappers.<ApiDoc>query().eq("folder_id", folderId));
    }

    @Override
    public boolean deleteDoc(Integer docId) {
        apiDocSchemaService.remove(Wrappers.<ApiDocSchema>query().eq("doc_id", docId));
        return this.removeById(docId);
    }

    @Override
    public boolean existsApiDoc(ApiDoc doc) {
        List<ApiDoc> existsItems = list(Wrappers.<ApiDoc>query().eq("project_id", doc.getProjectId())
                .eq("folder_id", doc.getFolderId())
                .eq("doc_name", doc.getDocName()));
        return existsItems.stream().anyMatch(item -> !item.getId().equals(doc.getId()));
    }

    @Override
    public int copyProjectDocs(Integer fromProjectId, Integer toProjectId,
                               Map<Integer, Pair<ApiFolder, ApiFolder>> foldersMap,
                               Map<Integer, Pair<ApiProjectInfo, ApiProjectInfo>> infosMap) {
        List<ApiDoc> apiDocs = loadByProject(fromProjectId);
        apiDocs.forEach(apiDoc -> {
            ApiDoc newDoc = SimpleModelUtils.copy(apiDoc, ApiDoc.class);
            newDoc.setProjectId(toProjectId);
            newDoc.setId(null);
            Pair<ApiFolder, ApiFolder> folderPair = foldersMap.get(apiDoc.getFolderId());
            if (folderPair != null && folderPair.getRight() != null) {
                newDoc.setFolderId(folderPair.getRight().getId());
            }
            Pair<ApiProjectInfo, ApiProjectInfo> infoPair = infosMap.get(apiDoc.getInfoId());
            if (infoPair != null && infoPair.getRight() != null) {
                newDoc.setInfoId(infoPair.getRight().getId());
            }
            save(newDoc);
            List<ApiDocSchema> schemas = apiDocSchemaService.loadByDoc(apiDoc.getId());
            schemas.forEach(schema -> {
                ApiDocSchema newSchema = SimpleModelUtils.copy(schema, ApiDocSchema.class);
                newSchema.setId(null);
                newSchema.setDocId(newDoc.getId());
                apiDocSchemaService.save(newSchema);
            });
        });
        return apiDocs.size();
    }
}
