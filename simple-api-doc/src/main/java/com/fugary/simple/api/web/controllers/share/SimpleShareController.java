package com.fugary.simple.api.web.controllers.share;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.push.ApiInvokeProcessor;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.service.token.TokenService;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.YamlUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectShareVo;
import com.fugary.simple.api.web.vo.query.ProjectDetailQueryVo;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@RestController
@RequestMapping("/shares")
public class SimpleShareController {

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

    @GetMapping("/loadShare/{shareId}")
    public SimpleResult<ApiProjectShareVo> loadShare(@PathVariable("shareId") String shareId, @RequestParam(name = "pwd") String password) {
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
                .includeDocs(true).build()));
    }

    @GetMapping("/checkDownloadDocs/{shareId}")
    public SimpleResult<Boolean> checkDownloadDocs(@PathVariable("shareId") String shareId) {
        if (!StringUtils.equals(shareId, SecurityUtils.getLoginShareId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_401);
        }
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        List<ApiProjectInfo> infoList;
        ApiProjectInfo currentInfo;
        List<ApiProjectInfoDetail> details;
        ApiProjectInfoDetail detail;
        if (apiShare == null
                || (infoList = apiProjectInfoService.loadByProjectId(apiShare.getProjectId())).isEmpty()
                || (currentInfo = infoList.get(0)) == null
                || (details = apiProjectInfoDetailService.loadByProjectAndInfo(apiShare.getProjectId(), currentInfo.getId(),
                    Set.of(ApiDocConstants.PROJECT_SCHEMA_TYPE_CONTENT))).isEmpty()
                || (detail = details.get(0)) == null
                || StringUtils.isBlank(detail.getSchemaContent())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        return SimpleResultUtils.createSimpleResult(true);
    }

    @GetMapping("/download/{type}/{shareId}")
    public void downloadDocs(@PathVariable("type") String type,
                                                         @PathVariable("shareId") String shareId,
                                                         HttpServletResponse response) throws IOException {
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        ApiProjectInfo currentInfo = apiProjectInfoService.loadByProjectId(apiShare.getProjectId()).get(0);
        ApiProjectInfoDetail detail = apiProjectInfoDetailService.loadByProjectAndInfo(apiShare.getProjectId(), currentInfo.getId(),
                Set.of(ApiDocConstants.PROJECT_SCHEMA_TYPE_CONTENT)).get(0);
        String fileName = apiShare.getShareName() + "." + type;
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.addHeader("Content-Disposition", "attachment; filename="
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        try (OutputStream out = response.getOutputStream()) {
            // 获取文件内容（确保 getContent 返回的是字节数据）
            byte[] contentBytes = getContent(type, detail.getSchemaContent()).getBytes(StandardCharsets.UTF_8);
            // 将内容写入输出流
            out.write(contentBytes);
            out.flush();
        }
    }

    protected String getContent(String type, String content) {
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        SwaggerParseResult result = new OpenAPIParser().readContents(content, null, parseOptions);
        OpenAPI openAPI = result.getOpenAPI();
        if (StringUtils.equals(type, "json")) {
            return JsonUtils.toJson(openAPI);
        } else {
            return YamlUtils.toYaml(openAPI);
        }
    }

    @GetMapping("/loadShareDoc/{shareId}/{docId}")
    public SimpleResult<ApiDocDetailVo> loadShareDoc(@PathVariable("shareId") String shareId, @PathVariable("docId") Integer docId) {
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        ApiDoc apiDoc = apiDocService.getById(docId);
        if (apiShare == null || apiDoc == null || !apiShare.getProjectId().equals(apiDoc.getProjectId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiProjectInfo apiInfo = apiProjectInfoService.getById(apiDoc.getInfoId());
        ApiDocDetailVo apiDocVo = apiDocSchemaService.loadDetailVo(apiDoc);
        if (apiInfo == null || !apiDocVo.getProjectId().equals(apiInfo.getProjectId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiProject apiProject = apiProjectService.getById(apiDocVo.getProjectId());
        ApiProjectInfoDetailVo apiInfoDetailVo = apiProjectInfoDetailService.parseInfoDetailVo(apiInfo, apiDocVo);
        apiInfoDetailVo.setProjectCode(apiProject.getProjectCode());
        apiDocVo.setProjectInfoDetail(apiInfoDetailVo);
        apiDocVo.setApiShare(SimpleModelUtils.toShareVo(apiShare));
        return SimpleResultUtils.createSimpleResult(apiDocVo);
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
