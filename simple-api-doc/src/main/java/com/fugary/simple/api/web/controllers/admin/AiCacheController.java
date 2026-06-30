package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.AiCache;
import com.fugary.simple.api.mapper.api.AiCacheMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectAccessService;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.ai.AiCacheQueryVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI Cache 管理 Controller
 *
 * @author gary.fu
 */
@Slf4j
@RestController
@RequestMapping("/admin/ai/caches")
public class AiCacheController {

    @Autowired
    private AiCacheMapper aiCacheMapper;

    @Autowired
    private ApiProjectAccessService apiProjectAccessService;

    @GetMapping
    public SimpleResult<List<AiCache>> search(@ModelAttribute AiCacheQueryVo queryVo) {
        Page<AiCache> page = SimpleResultUtils.toPage(queryVo);
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        if (queryVo.getProjectId() != null && !apiProjectAccessService.canAccessProject(queryVo.getProjectId(), ApiGroupAuthority.READABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        QueryWrapper<AiCache> queryWrapper = Wrappers.<AiCache>query()
                .eq(queryVo.getProjectId() != null, "project_id", queryVo.getProjectId())
                .eq(SecurityUtils.isAdmin() && StringUtils.isNotBlank(queryVo.getUserName()), "user_name", StringUtils.trimToEmpty(queryVo.getUserName()))
                .eq(queryVo.getStatus() != null, "status", queryVo.getStatus())
                .eq(StringUtils.isNotBlank(queryVo.getModelName()), "model_name", StringUtils.trimToEmpty(queryVo.getModelName()))
                .ge(queryVo.getStartDate() != null, "created_at", queryVo.getStartDate())
                .le(queryVo.getEndDate() != null, "created_at", queryVo.getEndDate())
                .and(StringUtils.isNotBlank(keyword), wrapper -> wrapper.like("cache_key", keyword)
                        .or().like("cache_value", keyword))
                .orderByDesc("created_at");
        if (!SecurityUtils.isAdmin() && queryVo.getProjectId() == null) {
            String userName = SecurityUtils.getLoginUserName();
            String groupCodesStr = apiProjectAccessService.loadReadableGroupCodesSql(userName);
            queryWrapper.and(wrapper -> wrapper.eq("user_name", userName)
                    .or().exists("select 1 from t_api_project p where p.id = t_ai_cache.project_id and p.user_name={0} and (p.group_code is null or p.group_code = '')", userName)
                    .or(StringUtils.isNotBlank(groupCodesStr),
                            item -> item.exists("select 1 from t_api_project p where p.id = t_ai_cache.project_id and p.group_code in ('" + groupCodesStr + "')")));
        }
        return SimpleResultUtils.createSimpleResult(aiCacheMapper.selectPage(page, queryWrapper));
    }

    @DeleteMapping("/{id}")
    public SimpleResult<Boolean> remove(@PathVariable("id") String id) {
        if (!SecurityUtils.isAdmin()) {
            AiCache aiCache = aiCacheMapper.selectById(id);
            if (aiCache == null || !StringUtils.equals(aiCache.getUserName(), SecurityUtils.getLoginUserName())) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
            }
        }
        return SimpleResultUtils.createSimpleResult(aiCacheMapper.deleteById(id) > 0);
    }
}
