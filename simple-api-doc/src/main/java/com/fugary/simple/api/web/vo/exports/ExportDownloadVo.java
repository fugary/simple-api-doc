package com.fugary.simple.api.web.vo.exports;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Create date 2024/10/23<br>
 *
 * @author gary.fu
 */
@Data
public class ExportDownloadVo implements Serializable {

    private static final long serialVersionUID = 6212516039447396202L;
    private String type;
    private String shareId;
    private List<Integer> docIds = new ArrayList<>();
}
