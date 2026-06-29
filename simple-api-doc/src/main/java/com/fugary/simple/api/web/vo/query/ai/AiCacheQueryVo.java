package com.fugary.simple.api.web.vo.query.ai;

import com.fugary.simple.api.web.vo.query.SimpleQueryVo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * AI Cache 列表查询 VO
 * 
 * @author gary.fu
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiCacheQueryVo extends SimpleQueryVo {

    private static final long serialVersionUID = 1L;

    /**
     * AI 模型名称
     */
    private String modelName;

    /**
     * 开始时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startDate;

    /**
     * 结束时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endDate;
}
