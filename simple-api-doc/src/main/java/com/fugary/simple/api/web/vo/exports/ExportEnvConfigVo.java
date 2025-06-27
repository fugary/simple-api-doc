package com.fugary.simple.api.web.vo.exports;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Create date 2024/9/24<br>
 *
 * @author gary.fu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportEnvConfigVo implements Serializable {

    private static final long serialVersionUID = 8028323405588182555L;
    private String name;
    private String url;
    private Boolean manual;
    private Boolean disabled;
}
