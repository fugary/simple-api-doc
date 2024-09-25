package com.fugary.simple.api.web.vo.exports;

import com.fugary.simple.api.entity.api.ApiFolder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Data
public class ExportApiFolderVo extends ApiFolder {

    private String folderPath;
    private transient ExportApiFolderVo parentFolder; // 父级仅作为计算使用不序列化，循环引用会引起栈溢出
    private List<ExportApiFolderVo> folders = new ArrayList<>();
    private List<ExportApiDocVo> docs = new ArrayList<>();
}
