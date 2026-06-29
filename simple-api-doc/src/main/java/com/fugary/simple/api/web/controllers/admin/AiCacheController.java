package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.AiCache;
import com.fugary.simple.api.mapper.api.AiCacheMapper;
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

    @GetMapping
    public SimpleResult<List<AiCache>> search(@ModelAttribute AiCacheQueryVo queryVo) {
        if (!SecurityUtils.isAdmin()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        Page<AiCache> page = SimpleResultUtils.toPage(queryVo);
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        QueryWrapper<AiCache> queryWrapper = Wrappers.<AiCache>query()
                .eq(queryVo.getStatus() != null, "status", queryVo.getStatus())
                .eq(StringUtils.isNotBlank(queryVo.getModelName()), "model_name", StringUtils.trimToEmpty(queryVo.getModelName()))
                .ge(queryVo.getStartDate() != null, "created_at", queryVo.getStartDate())
                .le(queryVo.getEndDate() != null, "created_at", queryVo.getEndDate())
                .and(StringUtils.isNotBlank(keyword), wrapper -> wrapper.like("cache_key", keyword)
                        .or().like("cache_value", keyword))
                .orderByDesc("created_at");
        return SimpleResultUtils.createSimpleResult(aiCacheMapper.selectPage(page, queryWrapper));
    }

    @DeleteMapping("/{id}")
    public SimpleResult<Boolean> remove(@PathVariable("id") String id) {
        if (!SecurityUtils.isAdmin()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(aiCacheMapper.deleteById(id) > 0);
    }
}
