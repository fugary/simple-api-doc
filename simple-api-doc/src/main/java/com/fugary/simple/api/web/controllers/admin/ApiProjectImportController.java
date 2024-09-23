package com.fugary.simple.api.web.controllers.admin;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.content.DocContentProvider;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectImportVo;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.InputStream;
import java.util.List;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@RestController
@RequestMapping("/admin/projects")
public class ApiProjectImportController {

    @Qualifier("streamDocContentProviderImpl")
    @Autowired
    private DocContentProvider<InputStream> streamDocContentProvider;

    @Qualifier("urlDocContentProviderImpl")
    @Autowired
    private DocContentProvider<UrlWithAuthVo> urlDocContentProvider;

    @Autowired
    private ApiProjectService apiProjectService;

    @SneakyThrows
    @PostMapping("/importProject")
    public SimpleResult<ApiProject> importProject(@ModelAttribute ApiProjectImportVo importVo, MultipartHttpServletRequest request){
        String content = StringUtils.EMPTY;
        if (ApiDocConstants.IMPORT_TYPE_FILE.equals(importVo.getImportType())) { // 文件模式
            List<MultipartFile> files = SimpleModelUtils.getUploadFiles(request);
            if (files.isEmpty()) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2002);
            }
            content = streamDocContentProvider.getContent(files.get(0).getInputStream());
        } else if (ApiDocConstants.IMPORT_TYPE_URL.equals(importVo.getImportType())) {
            content = urlDocContentProvider.getContent(importVo);
        }
        SimpleResult<ExportApiProjectVo> importResult = apiProjectService.processImportProject(content, importVo);
        if (importResult.isSuccess()) {
            return apiProjectService.importProject(importResult.getResultData(), importVo);
        }
        return SimpleResultUtils.createSimpleResult(importResult.getCode());
    }
}
