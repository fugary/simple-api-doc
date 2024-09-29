package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiProjectShareService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.ProjectQueryVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Create date 2024/9/27<br>
 *
 * @author gary.fu
 */
@RestController
@RequestMapping("/admin/shares")
public class ApiProjectShareController {

    @Autowired
    private ApiProjectShareService apiProjectShareService;

    @Autowired
    private ApiProjectService apiProjectService;

    @GetMapping
    public SimpleResult<List<ApiProjectShare>> search(@ModelAttribute ProjectQueryVo queryVo) {
        Page<ApiProjectShare> page = SimpleResultUtils.toPage(queryVo);
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        QueryWrapper<ApiProjectShare> queryWrapper = Wrappers.<ApiProjectShare>query()
                .eq("project_id", queryVo.getProjectId())
                .like(StringUtils.isNotBlank(keyword), "share_name", keyword);
        return SimpleResultUtils.createSimpleResult(apiProjectShareService.page(page, queryWrapper));
    }

    @GetMapping("/{id}")
    public SimpleResult<ApiProjectShare> get(@PathVariable("id") Integer id) {
        return SimpleResultUtils.createSimpleResult(apiProjectShareService.getById(id));
    }

    @DeleteMapping("/{id}")
    public SimpleResult remove(@PathVariable("id") Integer id) {
        ApiProjectShare apiShare = apiProjectShareService.getById(id);
        if (!validateShareUser(apiShare)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiProjectShareService.removeById(id));
    }

    @PostMapping
    public SimpleResult save(@RequestBody ApiProjectShare apiShare) {
        if (!validateShareUser(apiShare)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        apiShare.setShareId(StringUtils.defaultIfBlank(apiShare.getShareId(), SimpleModelUtils.uuid()));
        return SimpleResultUtils.createSimpleResult(apiProjectShareService.saveOrUpdate(SimpleModelUtils.addAuditInfo(apiShare)));
    }

    protected boolean validateShareUser(ApiProjectShare projectShare) {
        if (projectShare != null) {
            return apiProjectService.validateUserProject(projectShare.getProjectId());
        }
        return true;
    }

}
