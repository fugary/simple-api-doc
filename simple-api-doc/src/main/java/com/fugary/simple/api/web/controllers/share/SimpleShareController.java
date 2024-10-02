package com.fugary.simple.api.web.controllers.share;

import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.service.token.TokenService;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectShareVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

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

    @GetMapping("/loadShare/{shareId}")
    public SimpleResult<ApiProjectShareVo> loadShare(@PathVariable("shareId") String shareId, @RequestParam(name = "pwd") String password) {
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        if (apiShare == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiProjectShareVo shareVo = new ApiProjectShareVo();
        BeanUtils.copyProperties(apiShare, shareVo);
        shareVo.setNeedPassword(StringUtils.isNotBlank(apiShare.getSharePassword()));
        if (apiShare.getExpireDate() != null) {
            shareVo.setExpired(new Date().after(apiShare.getExpireDate()));
        }
        if (shareVo.isExpired()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2008);
        }
        if (shareVo.isNeedPassword()) {
            SimpleResult<ApiUser> validateResult = validateSharePwd(password, apiShare);
            if (!validateResult.isSuccess()) {
                SimpleResult<ApiProjectShareVo> result = SimpleResultUtils.createSimpleResult(validateResult);
                result.setResultData(shareVo);
                return result;
            }
        }
        ApiUser apiUser = new ApiUser();
        apiUser.setUserName(apiShare.getShareId());
        shareVo.setShareToken(tokenService.createToken(apiUser));
        return SimpleResultUtils.createSimpleResult(shareVo);
    }

    protected SimpleResult<ApiUser> validateSharePwd(String password, ApiProjectShare apiShare) {
        if (StringUtils.isNotBlank(password)) {
            if (StringUtils.equals(apiShare.getSharePassword(), password)) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_0);
            }
            return tokenService.validateTokenOnly(password);
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_401);
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
        return SimpleResultUtils.createSimpleResult(apiProjectService.loadProjectVo(project.getProjectCode(), true));
    }


    @GetMapping("/loadShareDoc/{shareId}/{docId}")
    public SimpleResult<ApiDocDetailVo> loadShareDoc(@PathVariable("shareId") String shareId, @PathVariable("docId") Integer docId) {
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        ApiDoc apiDoc = apiDocService.getById(docId);
        if (apiShare == null || apiDoc == null || !apiShare.getProjectId().equals(apiDoc.getProjectId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiDocDetailVo apiDocVo = apiDocSchemaService.loadDetailVo(apiDoc);
        ApiProjectInfo apiInfo = apiProjectInfoService.getById(apiDocVo.getInfoId());
        if (apiInfo == null || !apiDocVo.getProjectId().equals(apiInfo.getProjectId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiProjectInfoDetailVo apiInfoDetailVo = apiProjectInfoDetailService.parseInfoDetailVo(apiInfo, apiDocVo);
        apiDocVo.setProjectInfoDetail(apiInfoDetailVo);
        return SimpleResultUtils.createSimpleResult(apiDocVo);
    }

}
