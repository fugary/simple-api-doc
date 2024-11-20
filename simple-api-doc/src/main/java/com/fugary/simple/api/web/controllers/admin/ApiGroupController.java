package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiGroup;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.SimpleQueryVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Create date 2024/11/20<br>
 *
 * @author gary.fu
 */
@RestController
@RequestMapping("/admin/groups")
public class ApiGroupController {

    @Autowired
    private ApiGroupService apiGroupService;

    @GetMapping
    public SimpleResult<List<ApiGroup>> search(@ModelAttribute SimpleQueryVo queryVo) {
        Page<ApiGroup> page = SimpleResultUtils.toPage(queryVo);
        QueryWrapper<ApiGroup> queryWrapper = Wrappers.query();
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        if (StringUtils.isNotBlank(keyword)) {
            queryWrapper.and(wrapper -> wrapper.like("group_name", keyword)
                    .or().like("description", keyword));
        }
        return SimpleResultUtils.createSimpleResult(apiGroupService.page(page, queryWrapper));
    }

    @GetMapping("/{id}")
    public SimpleResult<ApiGroup> get(@PathVariable("id") Integer id) {
        return SimpleResultUtils.createSimpleResult(apiGroupService.getById(id));
    }

    @DeleteMapping("/{id}")
    public SimpleResult<Boolean> remove(@PathVariable("id") Integer id) {
        return SimpleResultUtils.createSimpleResult(apiGroupService.removeById(id));
    }

    @PostMapping
    public SimpleResult<Boolean> save(@RequestBody ApiGroup group) {
        if (apiGroupService.existsGroup(group)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_1001);
        }
        if (!SecurityUtils.isAdmin()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        if (StringUtils.isBlank(group.getGroupCode())) {
            group.setGroupCode(SimpleModelUtils.uuid());
        }
        return SimpleResultUtils.createSimpleResult(apiGroupService.saveOrUpdate(SimpleModelUtils.addAuditInfo(group)));
    }
}
