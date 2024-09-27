package com.fugary.simple.api.web.controllers.admin;

import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.service.apidoc.ApiProjectShareService;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.project.ApiProjectShareVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

/**
 * Create date 2024/9/27<br>
 *
 * @author gary.fu
 */
@RestController
@RequestMapping("/shares")
public class ApiProjectShareController {

    @Autowired
    private ApiProjectShareService apiProjectShareService;

    @GetMapping("/{shareId}")
    public SimpleResult<ApiProjectShareVo> loadShare(String shareId) {
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
}
