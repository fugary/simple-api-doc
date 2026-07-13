package com.fugary.simple.api.service.ai;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.AiConfig;

/**
 * @author gary.fu
 */
public interface AiConfigService extends IService<AiConfig> {

    /**
     * Get the default configuration, with YAML fallback support
     *
     * @return default AiConfig
     */
    AiConfig getDefaultAiConfig();

    /**
     * Get AI config by ID, or default if id is null
     *
     * @param id config id
     * @return AiConfig
     */
    AiConfig getAiConfig(Integer id);

    /**
     * Auto migration from application.yml config
     */
    void autoMigrateFromYaml();

    /**
     * Save snapshot of the AI config to history
     *
     * @param aiConfig
     * @return
     */
    boolean saveByAiConfig(AiConfig aiConfig);

    /**
     * Copy fields from history to current config
     *
     * @param aiConfigHistory
     * @param aiConfig
     * @return
     */
    AiConfig copyFromHistory(AiConfig aiConfigHistory, AiConfig aiConfig);
}
