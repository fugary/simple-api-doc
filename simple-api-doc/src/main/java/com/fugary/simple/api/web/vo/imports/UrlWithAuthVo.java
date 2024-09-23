package com.fugary.simple.api.web.vo.imports;

import lombok.Data;

import java.io.Serializable;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Data
public class UrlWithAuthVo implements Serializable {

    private static final long serialVersionUID = 5217829935532386060L;
    private String url;
    private String authType;
    private String authContent;
}
