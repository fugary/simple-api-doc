package com.fugary.simple.api.web.controllers.share;

import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.exports.ApiDocExporter;
import com.fugary.simple.api.exports.ApiDocViewGenerator;
import com.fugary.simple.api.exports.md.MdViewContext;
import com.fugary.simple.api.push.ApiInvokeProcessor;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.service.token.TokenService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.ExportDownloadVo;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectShareVo;
import com.fugary.simple.api.web.vo.query.ProjectDetailQueryVo;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@Slf4j
@RestController
@RequestMapping("/shares")
public class SimpleShareController {

    @Value("${spring.application.name}")
    private String applicationName;

    @Autowired
    private ApiProjectService apiProjectService;

    @Autowired
    private ApiProjectShareService apiProjectShareService;

    @Autowired
    private ApiProjectInfoService apiProjectInfoService;

    @Autowired
    private ApiProjectInfoDetailService apiProjectInfoDetailService;

    @Autowired
    private ApiDocService apiDocService;

    @Autowired
    private ApiDocSchemaService apiDocSchemaService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ApiInvokeProcessor apiInvokeProcessor;

    @Autowired
    private ApiDocExporter<OpenAPI> apiApiDocExporter;

    @Autowired
    private ApiDocExporter<String> apiApiDocMdExporter;

    @Autowired
    private ApiDocViewGenerator apiDocViewGenerator;

    @GetMapping("/loadShare/{shareId}")
    public SimpleResult<ApiProjectShareVo> loadShare(@PathVariable("shareId") String shareId,
                                                     @RequestParam(name = "pwd", required = false) String password) {
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        if (apiShare == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiProjectShareVo shareVo = SimpleModelUtils.toShareVo(apiShare);
        if (shareVo.isExpired()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2008);
        }
        if (shareVo.isNeedPassword()) {
            SimpleResult<ApiUser> validateResult = apiProjectShareService.validateSharePwd(password, apiShare);
            if (!validateResult.isSuccess()) {
                SimpleResult<ApiProjectShareVo> result = SimpleResultUtils.createSimpleResult(validateResult);
                result.setResultData(shareVo);
                return result;
            }
        }
        ApiUser apiUser = new ApiUser();
        apiUser.setUserName(SecurityUtils.getShareUserName(apiShare));
        shareVo.setShareToken(tokenService.createToken(apiUser));
        return SimpleResultUtils.createSimpleResult(shareVo);
    }

    @GetMapping("/loadProject/{shareId}")
    public SimpleResult<ApiProjectDetailVo> loadProject(@PathVariable("shareId") String shareId) {
        if (!StringUtils.equals(shareId, SecurityUtils.getLoginShareId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_401);
        }
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        ApiProject project;
        if (apiShare == null || (project = apiProjectService.getById(apiShare.getProjectId())) == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        return SimpleResultUtils.createSimpleResult(apiProjectService.loadProjectVo(ProjectDetailQueryVo.builder()
                .projectCode(project.getProjectCode())
                .forceEnabled(true)
                .includeDocContent(false)
                .removeAuditFields(false)
                .docIds(SimpleModelUtils.getShareDocIds(apiShare.getShareDocs()))
                .includeDocs(true).build()));
    }

    @PostMapping("/checkExportDownloadDocs")
    public SimpleResult<String> checkExportDownloadDocs(@RequestBody ExportDownloadVo downloadVo) {
        String shareId = downloadVo.getShareId();
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        if (apiShare == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!StringUtils.equals(shareId, SecurityUtils.getLoginShareId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_401);
        }
        Set<Integer> shareDocIds = SimpleModelUtils.getShareDocIds(apiShare.getShareDocs());
        if (CollectionUtils.isEmpty(downloadVo.getDocIds())) {
            downloadVo.setDocIds(new ArrayList<>(shareDocIds));
        } else {
            downloadVo.setDocIds(downloadVo.getDocIds().stream().filter(shareDocIds::contains).collect(Collectors.toList()));
        }
        String uuid = SimpleResultUtils.createTempExportFile(apiApiDocExporter, apiApiDocMdExporter, downloadVo, applicationName, apiShare.getProjectId());
        return SimpleResultUtils.createSimpleResult(uuid);
    }

    @GetMapping("/exportDownload/{type}/{shareId}/{uuid}")
    public ResponseEntity<InputStreamResource> exportDownloadDocs(@PathVariable("type") String type,
                                                                  @PathVariable("shareId") String shareId,
                                                                  @PathVariable("uuid") String uuid,
                                                                  HttpServletRequest request) throws IOException {
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        String fileName = uuid + "." + type;
        return SimpleResultUtils.downloadTempExportFile(request, applicationName, apiShare.getShareName(), fileName);
    }

    @GetMapping("/loadShareDoc/{shareId}/{docId}")
    public SimpleResult<ApiDocDetailVo> loadShareDoc(@PathVariable("shareId") String shareId,
                                                     @PathVariable("docId") Integer docId,
                                                     @RequestParam(value = "md",
                                                             required = false,
                                                             defaultValue = "false") Boolean markdown) {
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        ApiDoc apiDoc = apiDocService.getById(docId);
        if (apiShare == null || apiDoc == null || !apiShare.getProjectId().equals(apiDoc.getProjectId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        Set<Integer> docIds = SimpleModelUtils.getShareDocIds(apiShare.getShareDocs());
        if (!docIds.isEmpty() && !docIds.contains(docId)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        ApiProjectInfo apiInfo = apiProjectInfoService.getById(apiDoc.getInfoId());
        ApiDocDetailVo apiDocVo = apiDocSchemaService.loadDetailVo(apiDoc);
        if (apiInfo == null || !apiDocVo.getProjectId().equals(apiInfo.getProjectId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiProject apiProject = apiProjectService.getById(apiDocVo.getProjectId());
        ApiProjectInfoDetailVo apiInfoDetailVo = apiProjectInfoDetailService.parseInfoDetailVo(apiInfo, apiDocVo);
        apiInfoDetailVo.setProjectCode(apiProject.getProjectCode());
        apiDocVo.setProject(apiProject);
        apiDocVo.setProjectInfoDetail(apiInfoDetailVo);
        apiDocVo.setApiShare(SimpleModelUtils.toShareVo(apiShare));
        if (Boolean.TRUE.equals(markdown)) {
            String apiMarkdown = apiDocViewGenerator.generate(new MdViewContext(apiDocVo));
            apiDocVo.setApiMarkdown(apiMarkdown);
        }
        return SimpleResultUtils.createSimpleResult(apiDocVo);
    }

    @GetMapping("/loadMdDoc/{docId}")
    public SimpleResult<ApiDoc> loadMdDoc(@PathVariable("docId") Integer docId) {
        ApiDoc apiDoc = apiDocService.getById(docId);
        if (apiDoc == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        return SimpleResultUtils.createSimpleResult(apiDoc);
    }

    /**
     * 调试API
     *
     * @return
     */
    @RequestMapping("/proxy/**")
    public ResponseEntity<?> proxyApi(HttpServletRequest request, HttpServletResponse response) {
        return apiInvokeProcessor.invoke(request, response);
    }
}
