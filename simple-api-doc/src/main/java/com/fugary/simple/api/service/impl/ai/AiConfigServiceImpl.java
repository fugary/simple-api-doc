package com.fugary.simple.api.service.impl.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.config.AiConfigProperties;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.mapper.AiConfigMapper;
import com.fugary.simple.api.service.ai.AiConfigService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Objects;

/**
 * @author gary.fu
 */
@Service
public class AiConfigServiceImpl extends ServiceImpl<AiConfigMapper, AiConfig> implements AiConfigService {

    @Autowired
    private AiConfigProperties aiConfigProperties;

    @PostConstruct
    @Override
    public void autoMigrateFromYaml() {
        if (!aiConfigProperties.isEnabled() || StringUtils.isBlank(aiConfigProperties.getApiKey())) {
            return;
        }
        AiConfig systemConfig = this.getOne(Wrappers.<AiConfig>query().eq("creator", "system").orderByAsc("id").last("limit 1"));
        if (systemConfig == null) {
            long count = this.count();
            if (count == 0) {
                AiConfig defaultConfig = new AiConfig();
                defaultConfig.setConfigName("默认配置文件迁移");
                defaultConfig.setBaseUrl(aiConfigProperties.getBaseUrl());
                defaultConfig.setApiKey(aiConfigProperties.getApiKey());
                defaultConfig.setDefaultModel(aiConfigProperties.getModel());
                defaultConfig.setProvider("OPENAI");
                defaultConfig.setIsDefault(1);
                defaultConfig.setStatus(1);
                SimpleModelUtils.addAuditInfo(defaultConfig);
                defaultConfig.setCreator("system");
                this.save(defaultConfig);
            }
        } else {
            if (Integer.valueOf(1).equals(systemConfig.getVersion())) {
                boolean changed = false;
                if (!Objects.equals(systemConfig.getBaseUrl(), aiConfigProperties.getBaseUrl())) {
                    systemConfig.setBaseUrl(aiConfigProperties.getBaseUrl());
                    changed = true;
                }
                if (!Objects.equals(systemConfig.getApiKey(), aiConfigProperties.getApiKey())) {
                    systemConfig.setApiKey(aiConfigProperties.getApiKey());
                    changed = true;
                }
                if (!Objects.equals(systemConfig.getDefaultModel(), aiConfigProperties.getModel())) {
                    systemConfig.setDefaultModel(aiConfigProperties.getModel());
                    changed = true;
                }
                if (changed) {
                    this.update(Wrappers.<AiConfig>update()
                            .set("base_url", systemConfig.getBaseUrl())
                            .set("api_key", systemConfig.getApiKey())
                            .set("default_model", systemConfig.getDefaultModel())
                            .eq("id", systemConfig.getId()));
                }
            }
        }
    }

    @Override
    public AiConfig getDefaultAiConfig() {
        AiConfig dbConfig = this.getOne(Wrappers.<AiConfig>query()
                .eq("is_default", 1)
                .eq("status", 1)
                .orderByDesc("id")
                .last("limit 1"));
        
        if (dbConfig != null) {
            return dbConfig;
        }

        // YAML Fallback
        AiConfig fallbackConfig = new AiConfig();
        fallbackConfig.setConfigName("配置文件兜底配置");
        fallbackConfig.setBaseUrl(aiConfigProperties.getBaseUrl());
        fallbackConfig.setApiKey(aiConfigProperties.getApiKey());
        fallbackConfig.setDefaultModel(aiConfigProperties.getModel());
        fallbackConfig.setProvider("OPENAI");
        fallbackConfig.setStatus(1);
        return fallbackConfig;
    }

    @Override
    public AiConfig getAiConfig(Integer id) {
        if (id == null) {
            return getDefaultAiConfig();
        }
        return this.getById(id);
    }

    @Override
    public boolean saveByAiConfig(AiConfig aiConfig) {
        if (aiConfig != null) {
            AiConfig aiConfigHistory = SimpleModelUtils.copy(aiConfig, AiConfig.class);
            aiConfigHistory.setId(null);
            aiConfigHistory.setModifyFrom(aiConfig.getId());
            aiConfigHistory.setCreator(StringUtils.defaultIfBlank(aiConfig.getModifier(), aiConfig.getCreator()));
            aiConfigHistory.setCreateDate(Objects.requireNonNullElse(aiConfig.getModifyDate(), aiConfig.getCreateDate()));
            return this.save(aiConfigHistory);
        }
        return true;
    }

    @Override
    public AiConfig copyFromHistory(AiConfig aiConfigHistory, AiConfig aiConfig) {
        AiConfig resultConfig = SimpleModelUtils.copy(aiConfig, AiConfig.class);
        SimpleModelUtils.copy(aiConfigHistory, resultConfig);
        resultConfig.setId(aiConfig.getId());
        resultConfig.setVersion(aiConfig.getVersion());
        resultConfig.setModifyFrom(null);
        SimpleModelUtils.addAuditInfo(resultConfig);
        return resultConfig;
    }
}
