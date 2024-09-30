package com.fugary.simple.api.web.controllers.share;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectShareVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private ApiProjectShareService apiProjectShareService;

    @Autowired
    private ApiProjectInfoService apiProjectInfoService;

    @Autowired
    private ApiProjectInfoDetailService apiProjectInfoDetailService;

    @Autowired
    private ApiDocService apiDocService;

    @Autowired
    private ApiDocSchemaService apiDocSchemaService;

    @GetMapping("/{shareId}")
    public SimpleResult<ApiProjectShareVo> loadShare(@PathVariable("shareId") String shareId) {
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
        return SimpleResultUtils.createSimpleResult(shareVo);
    }

    @GetMapping("/{shareId}/{docId}")
    public SimpleResult<ApiDocDetailVo> loadShareDoc(@PathVariable("shareId") String shareId, @PathVariable("docId") Integer docId) {
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        ApiDoc apiDoc = apiDocService.getById(docId);
        if (apiShare == null || apiDoc == null || !apiShare.getProjectId().equals(apiDoc.getProjectId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiDocDetailVo apiDocVo = new ApiDocDetailVo();
        BeanUtils.copyProperties(apiDoc, apiDocVo);
        List<ApiDocSchema> docSchemas = apiDocSchemaService.loadByDoc(docId);
        docSchemas.forEach(schema -> {
            switch (schema.getBodyType()) {
                case ApiDocConstants.DOC_SCHEMA_TYPE_REQUEST:
                    apiDocVo.getRequestsSchemas().add(schema);
                    break;
                case ApiDocConstants.DOC_SCHEMA_TYPE_RESPONSE:
                    apiDocVo.getResponsesSchemas().add(schema);
                    break;
                case ApiDocConstants.DOC_SCHEMA_TYPE_PARAMETERS:
                    apiDocVo.setParametersSchema(schema);
                    break;
            }
        });
        return SimpleResultUtils.createSimpleResult(apiDocVo);
    }

    @GetMapping("/info/{shareId}/{docId}")
    public SimpleResult<ApiProjectInfoDetailVo> loadInfo(@PathVariable("shareId") String shareId, @PathVariable("docId") Integer docId) {
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        ApiDoc apiDoc = apiDocService.getById(docId);
        if (apiShare == null || apiDoc == null || !apiShare.getProjectId().equals(apiDoc.getProjectId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiProjectInfo apiInfo = apiProjectInfoService.getById(apiDoc.getInfoId());
        if (apiInfo == null || !apiShare.getProjectId().equals(apiInfo.getProjectId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiProjectInfoDetailVo apiInfoVo = new ApiProjectInfoDetailVo();
        BeanUtils.copyProperties(apiInfo, apiInfoVo);
        List<ApiProjectInfoDetail> apiInfoDetails = apiProjectInfoDetailService.loadByProjectAndInfo(apiShare.getProjectId(), apiInfo.getId(),
                Set.of(ApiDocConstants.PROJECT_SCHEMA_TYPE_COMPONENT, ApiDocConstants.PROJECT_SCHEMA_TYPE_SECURITY));
        apiInfoDetails.forEach(detail -> {
            switch (detail.getBodyType()) {
                case ApiDocConstants.PROJECT_SCHEMA_TYPE_COMPONENT:
                    apiInfoVo.getComponentSchemas().add(detail);
                    break;
                case ApiDocConstants.PROJECT_SCHEMA_TYPE_SECURITY:
                    apiInfoVo.getSecuritySchemas().add(detail);
                    break;
            }
        });
        return SimpleResultUtils.createSimpleResult(apiInfoVo);
    }

}
