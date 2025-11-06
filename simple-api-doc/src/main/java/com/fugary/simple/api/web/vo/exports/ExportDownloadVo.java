package com.fugary.simple.api.web.vo.exports;

import com.fugary.simple.api.exports.ApiExportFilter;
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
public class ExportDownloadVo implements Serializable, ApiExportFilter {

    private static final long serialVersionUID = 6212516039447396202L;
    private String type;
    private String shareId;
    private String projectCode;
    private List<Integer> docIds = new ArrayList<>();
    private String envContent;
    private boolean returnContent;
}
