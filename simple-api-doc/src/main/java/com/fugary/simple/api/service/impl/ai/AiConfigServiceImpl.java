package com.fugary.simple.api.service.impl.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.config.AiConfigProperties;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.mapper.api.AiConfigMapper;
import com.fugary.simple.api.service.ai.AiConfigService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Date;
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
            AiConfig defaultConfig = new AiConfig();
            defaultConfig.setConfigName("Env Default");
            defaultConfig.setBaseUrl(aiConfigProperties.getBaseUrl());
            defaultConfig.setApiKey(aiConfigProperties.getApiKey());
            defaultConfig.setDefaultModel(aiConfigProperties.getModel());
            defaultConfig.setProvider(aiConfigProperties.getProvider());
            defaultConfig.setIsDefault(count == 0 ? 1 : 0);
            defaultConfig.setStatus(1);
            SimpleModelUtils.addAuditInfo(defaultConfig);
            defaultConfig.setCreator("system");
            this.save(defaultConfig);
        } else {
            AiConfig newConfig = SimpleModelUtils.copy(systemConfig, AiConfig.class);
            newConfig.setBaseUrl(aiConfigProperties.getBaseUrl());
            newConfig.setApiKey(aiConfigProperties.getApiKey());
            newConfig.setDefaultModel(aiConfigProperties.getModel());
            newConfig.setProvider(aiConfigProperties.getProvider());

            if (!SimpleModelUtils.isSameData(newConfig, systemConfig)) {
                this.saveByAiConfig(systemConfig);
                newConfig.setModifier("system");
                newConfig.setModifyDate(new Date());
                this.updateById(newConfig);
            }
        }
    }

    @Override
    public AiConfig getDefaultAiConfig() {
        return this.getOne(Wrappers.<AiConfig>query()
                .eq("is_default", 1)
                .eq("status", 1)
                .orderByDesc("id")
                .last("limit 1"));
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
