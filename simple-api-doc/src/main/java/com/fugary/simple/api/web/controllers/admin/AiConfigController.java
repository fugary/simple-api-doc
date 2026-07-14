package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.service.ai.AiConfigService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.SimpleQueryVo;
import com.fugary.simple.api.web.vo.query.ai.AiConfigQueryVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * AI Config 管理 Controller
 *
 * @author gary.fu
 */
@Slf4j
@RestController
@RequestMapping("/admin/ai/configs")
public class AiConfigController {

    @Autowired
    private AiConfigService aiConfigService;

    @GetMapping
    public SimpleResult<List<AiConfig>> search(AiConfigQueryVo queryVo) {
        Page<AiConfig> page = SimpleResultUtils.toPage(queryVo);
        QueryWrapper<AiConfig> queryWrapper = Wrappers.<AiConfig>query()
                .like(StringUtils.isNotBlank(queryVo.getConfigName()), "config_name", queryVo.getConfigName())
                .eq(queryVo.getStatus() != null, "status", queryVo.getStatus())
                .eq(queryVo.getIsDefault() != null, "is_default", queryVo.getIsDefault())
                .eq(queryVo.getModifyFrom() != null, "modify_from", queryVo.getModifyFrom())
                .isNull(queryVo.getModifyFrom() == null, "modify_from")
                .orderByDesc("id");
        return SimpleResultUtils.createSimpleResult(aiConfigService.page(page, queryWrapper));
    }

    @GetMapping("/{id}")
    public SimpleResult<AiConfig> get(@PathVariable("id") Integer id) {
        AiConfig config = aiConfigService.getAiConfig(id);
        if (config != null) {
            return SimpleResultUtils.createSimpleResult(config);
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
    }

    @PostMapping
    public SimpleResult<AiConfig> save(@RequestBody AiConfig aiConfig) {
        if (aiConfig.getId() != null) {
            AiConfig existsConfig = aiConfigService.getById(aiConfig.getId());
            if (existsConfig != null) {
                if ("system".equals(existsConfig.getCreator())) {
                    // System configurations cannot modify core fields, but can modify isDefault and status
                    aiConfig.setConfigName(existsConfig.getConfigName());
                    aiConfig.setProvider(existsConfig.getProvider());
                    aiConfig.setBaseUrl(existsConfig.getBaseUrl());
                    aiConfig.setApiKey(existsConfig.getApiKey());
                    aiConfig.setDefaultModel(existsConfig.getDefaultModel());
                }
                if (SimpleModelUtils.isSameData(aiConfig, existsConfig)) {
                    return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2000, existsConfig);
                }
                aiConfigService.saveByAiConfig(existsConfig); // 保存历史
            }
        }

        // 如果设置为默认，需要把其他的取消默认
        if (Integer.valueOf(1).equals(aiConfig.getIsDefault())) {
            aiConfigService.update(Wrappers.<AiConfig>update().set("is_default", 0).ne(aiConfig.getId() != null, "id", aiConfig.getId()));
        }

        SimpleModelUtils.addAuditInfo(aiConfig);
        boolean result = aiConfigService.saveOrUpdate(aiConfig);
        if (result) {
            return SimpleResultUtils.createSimpleResult(aiConfig);
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_500);
    }

    @DeleteMapping("/{id}")
    public SimpleResult<Boolean> delete(@PathVariable("id") Integer id) {
        AiConfig existsConfig = aiConfigService.getById(id);
        if (existsConfig != null) {
            if ("system".equals(existsConfig.getCreator())) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403, false);
            }
            aiConfigService.saveByAiConfig(existsConfig); // 保存历史

            // 同时删除其历史记录
            aiConfigService.remove(Wrappers.<AiConfig>query().eq("modify_from", id));
            return SimpleResultUtils.createSimpleResult(aiConfigService.removeById(id));
        }
        return SimpleResultUtils.createSimpleResult(false);
    }

    @PostMapping("/recover")
    public SimpleResult<AiConfig> recoverFromHistory(@RequestBody SimpleQueryVo historyVo) {
        AiConfig historyConfig = aiConfigService.getById(historyVo.getQueryId());
        if (historyConfig != null && historyConfig.getModifyFrom() != null) {
            AiConfig currentConfig = aiConfigService.getById(historyConfig.getModifyFrom());
            if (currentConfig != null) {
                aiConfigService.saveByAiConfig(currentConfig); // 当前版本存为历史
                AiConfig newConfig = aiConfigService.copyFromHistory(historyConfig, currentConfig);
                aiConfigService.updateById(newConfig);
                return SimpleResultUtils.createSimpleResult(newConfig);
            }
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
    }

    @PostMapping("/histories/{id}")
    public SimpleResult<List<AiConfig>> histories(@RequestBody AiConfigQueryVo queryVo, @PathVariable Integer id) {
        AiConfig currentData = aiConfigService.getById(id);
        Page<AiConfig> page = SimpleResultUtils.toPage(queryVo);
        QueryWrapper<AiConfig> queryWrapper = Wrappers.<AiConfig>query()
                .eq("modify_from", id);
        queryWrapper.orderByDesc("config_version");
        return SimpleResultUtils.createSimpleResult(aiConfigService.page(page, queryWrapper))
                .add("current", currentData);
    }

    @PostMapping("/loadHistoryDiff")
    public SimpleResult<Map<String, AiConfig>> loadHistoryDiff(@RequestBody SimpleQueryVo queryVo) {
        Integer id = queryVo.getQueryId();
        AiConfig modified = aiConfigService.getById(id);
        if (modified == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        Integer maxVersion = modified.getVersion();
        Page<AiConfig> page = new Page<>(1, 2);
        aiConfigService.page(page, Wrappers.<AiConfig>query()
                .eq("modify_from", ObjectUtils.defaultIfNull(modified.getModifyFrom(), modified.getId()))
                .le(maxVersion != null, "config_version", maxVersion)
                .orderByDesc("config_version"));
        if (page.getRecords().isEmpty()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        } else {
            Map<String, AiConfig> map = new HashMap<>(2);
            List<AiConfig> dataList = page.getRecords();
            map.put("modified", modified);
            dataList.stream().filter(data -> !data.getId().equals(modified.getId())).findFirst().ifPresent(data -> {
                map.put("original", data);
            });
            return SimpleResultUtils.createSimpleResult(map);
        }
    }
}
