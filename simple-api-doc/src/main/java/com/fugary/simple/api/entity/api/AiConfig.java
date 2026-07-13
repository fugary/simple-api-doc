package com.fugary.simple.api.entity.api;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI Service Configuration Entity
 *
 * @author gary.fu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ai_config")
public class AiConfig extends ModelBase implements HistoryBase {

    private static final long serialVersionUID = 1L;

    private String configName;
    private String baseUrl;
    private String apiKey;
    private String defaultModel;
    private String provider;
    private Integer isDefault;

    @Version
    @TableField("config_version")
    private Integer version;

    private Integer modifyFrom;
}
