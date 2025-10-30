package com.fugary.simple.api.web.controllers.generator.store;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.nio.file.Path;

/**
 * Create date 2025/10/28<br>
 *
 * @author gary.fu
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class GeneratedVo implements Serializable {

    private static final long serialVersionUID = 8533871357646249569L;
    private String type;
    private String language;
    private Path path;

}
