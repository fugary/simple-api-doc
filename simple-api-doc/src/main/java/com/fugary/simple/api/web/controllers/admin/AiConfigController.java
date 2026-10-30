package com.fugary.simple.api.web.controllers.admin;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;
import com.fugary.simple.api.service.ai.provider.AiSseSupport;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.exception.SimpleRuntimeException;
import com.fugary.simple.api.service.ai.AiConfigService;
import com.fugary.simple.api.service.ai.AiService;
import com.fugary.simple.api.service.ai.provider.AiChatResponse;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.AiGenericTaskReq;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.SimpleQueryVo;
import com.fugary.simple.api.web.vo.query.ai.AiConfigQueryVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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

    @Autowired
    private AiService aiService;

    @Autowired
    private AiSseSupport aiSseSupport;

    @PostMapping(value = {"/test/stream", "/{id}/test/stream"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTest(
            @PathVariable(value = "id", required = false) Integer id, @RequestBody AiGenericTaskReq req) {
        String prompt = req == null ? null : StringUtils.defaultIfBlank(req.getUserMessage(), req.getPrompt());
        if (StringUtils.isBlank(prompt)) throw new SimpleRuntimeException(SystemErrorConstants.CODE_2015);
        Integer configId = ObjectUtils.defaultIfNull(id, req.getConfigId());
        checkTestAccess(configId, req);
        return aiSseSupport.start((emitter, stream) -> aiService.streamTestAiConfig(configId, req, emitter, stream));
    }

    @PostMapping({"/test", "/{id}/test"})
    public SimpleResult<AiChatResponse> test(@PathVariable(value = "id", required = false) Integer id,
                                             @RequestBody AiGenericTaskReq req) {
        String prompt = req != null ? StringUtils.defaultIfBlank(req.getUserMessage(), req.getPrompt()) : null;
        if (StringUtils.isBlank(prompt)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2015);
        }
        Integer configId = ObjectUtils.defaultIfNull(id, req != null ? req.getConfigId() : null);
        checkTestAccess(configId, req);
        return testResult(() -> configId != null
                ? aiService.testAiConfig(configId, req)
                : aiService.testAiConfig(req != null ? req.getConfig() : null, req));
    }

    private SimpleResult<AiChatResponse> testResult(Supplier<AiChatResponse> supplier) {
        try {
            return SimpleResultUtils.createSimpleResult(supplier.get());
        } catch (SimpleRuntimeException e) {
            if (StringUtils.isNotBlank(e.getMessage())) {
                return SimpleResultUtils.createError(e.getCode() != null ? e.getCode() : SystemErrorConstants.CODE_500, e.getMessage());
            }
            return SimpleResultUtils.createSimpleResult(e.getCode() != null ? e.getCode() : SystemErrorConstants.CODE_500);
        } catch (Exception e) {
            return SimpleResultUtils.createError(e.getMessage());
        }
    }

    @GetMapping("/{id}/models")
    public SimpleResult<List<String>> loadModels(@PathVariable("id") Integer id) {
        checkUsableConfig(id);
        return loadModelsResult(() -> aiService.loadModels(id));
    }

    @PostMapping("/models")
    public SimpleResult<List<String>> loadModels(@RequestBody AiConfig config) {
        requireAdmin();
        return loadModelsResult(() -> aiService.loadModels(config));
    }

    private SimpleResult<List<String>> loadModelsResult(Supplier<List<String>> supplier) {
        try {
            return SimpleResultUtils.createSimpleResult(supplier.get());
        } catch (SimpleRuntimeException e) {
            if (StringUtils.isNotBlank(e.getMessage())) {
                return SimpleResultUtils.createError(e.getCode() != null ? e.getCode() : SystemErrorConstants.CODE_500, e.getMessage());
            }
            return SimpleResultUtils.createSimpleResult(e.getCode() != null ? e.getCode() : SystemErrorConstants.CODE_500);
        } catch (Exception e) {
            return SimpleResultUtils.createError(e.getMessage());
        }
    }

    @GetMapping
    public SimpleResult<List<AiConfig>> search(AiConfigQueryVo queryVo) {
        if (queryVo.getModifyFrom() != null) {
            requireAdmin();
        }
        Page<AiConfig> page = SimpleResultUtils.toPage(queryVo);
        QueryWrapper<AiConfig> queryWrapper = Wrappers.<AiConfig>query()
                .like(StringUtils.isNotBlank(queryVo.getConfigName()), "config_name", queryVo.getConfigName())
                .eq(StringUtils.isNotBlank(queryVo.getProvider()), "provider", queryVo.getProvider())
                .like(StringUtils.isNotBlank(queryVo.getDefaultModel()), "default_model", queryVo.getDefaultModel())
                .eq(queryVo.getStatus() != null, "status", queryVo.getStatus())
                .eq(queryVo.getIsDefault() != null, "is_default", queryVo.getIsDefault())
                .eq(queryVo.getModifyFrom() != null, "modify_from", queryVo.getModifyFrom())
                .isNull(queryVo.getModifyFrom() == null, "modify_from")
                .orderByDesc("id");
        aiConfigService.page(page, queryWrapper);
        page.setRecords(page.getRecords().stream().map(this::visibleConfig).collect(Collectors.toList()));
        return SimpleResultUtils.createSimpleResult(page);
    }

    @GetMapping("/{id}")
    public SimpleResult<AiConfig> get(@PathVariable("id") Integer id) {
        AiConfig config = aiConfigService.getAiConfig(id);
        if (config != null) {
            if (config.getModifyFrom() != null) {
                requireAdmin();
            }
            return SimpleResultUtils.createSimpleResult(visibleConfig(config));
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
    }

    @PostMapping
    public SimpleResult<AiConfig> save(@RequestBody AiConfig aiConfig) {
        requireAdmin();
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
            aiConfigService.update(Wrappers.<AiConfig>update()
                    .isNull("modify_from")
                    .set("is_default", 0)
                    .ne(aiConfig.getId() != null, "id", aiConfig.getId()));
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
        requireAdmin();
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
        requireAdmin();
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
        requireAdmin();
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
        requireAdmin();
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

    private void requireAdmin() {
        if (!SecurityUtils.isAdmin()) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_403);
        }
    }

    private AiConfig visibleConfig(AiConfig config) {
        if (SecurityUtils.isAdmin()) {
            return config;
        }
        AiConfig visible = SimpleModelUtils.copy(config, AiConfig.class);
        visible.setApiKey(null);
        return visible;
    }

    private void checkTestAccess(Integer configId, AiGenericTaskReq req) {
        if (!SecurityUtils.isAdmin() && req.getConfig() != null) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_403);
        }
        checkUsableConfig(configId);
    }

    // 普通用户仅能通过 ID 使用已启用的当前配置，密钥始终留在服务端。
    private void checkUsableConfig(Integer configId) {
        if (SecurityUtils.isAdmin()) {
            return;
        }
        AiConfig config = configId == null ? null : aiConfigService.getById(configId);
        if (config == null || config.getModifyFrom() != null || !Integer.valueOf(1).equals(config.getStatus())) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_403);
        }
    }
}
