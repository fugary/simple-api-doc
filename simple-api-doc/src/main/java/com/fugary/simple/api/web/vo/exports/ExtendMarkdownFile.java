package com.fugary.simple.api.web.vo.exports;

import lombok.Data;

import java.io.Serializable;

/**
 * Create date 2024/9/25<br>
 *
 * @author gary.fu
 */
@Data
public class ExtendMarkdownFile implements Serializable {

    private static final long serialVersionUID = -4507696422000908071L;
    private String folderName;
    private String name;
    private String content;
}
