package com.fugary.simple.api.utils;

import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.web.vo.exports.ExportApiDocSchemaVo;
import com.fugary.simple.api.web.vo.exports.ExportApiDocVo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Create date 2026/8/31<br>
 *
 * @author gary.fu
 */
class SimpleModelUtilsTest {

    @Test
    void testIsSameDataWithCopy() {
        ApiDoc doc = new ApiDoc();
        doc.setId(1);
        doc.setDocName("testDoc");
        doc.setUrl("/api/test");
        doc.setDocType("api");
        doc.setSortId(10);

        ExportApiDocVo exportVo = new ExportApiDocVo();
        exportVo.setId(1);
        exportVo.setDocName("testDoc");
        exportVo.setUrl("/api/test");
        exportVo.setDocType("api");
        exportVo.setSortId(20); // Different sortId
        exportVo.setRequestsSchemas(List.of(new ExportApiDocSchemaVo())); // Subclass specific field

        // Copy to ApiDoc.class then compare ignoring sortId -> should be same
        ApiDoc copiedDoc = SimpleModelUtils.copy(exportVo, ApiDoc.class);
        boolean same = SimpleModelUtils.isSameData(copiedDoc, doc, "sortId");
        Assertions.assertTrue(same);

        // Without ignoring sortId -> should be different
        boolean sameWithoutIgnore = SimpleModelUtils.isSameData(copiedDoc, doc);
        Assertions.assertFalse(sameWithoutIgnore);

        // Change docName -> should be different
        exportVo.setDocName("changedDocName");
        ApiDoc changedCopiedDoc = SimpleModelUtils.copy(exportVo, ApiDoc.class);
        boolean sameAfterChange = SimpleModelUtils.isSameData(changedCopiedDoc, doc, "sortId");
        Assertions.assertFalse(sameAfterChange);
    }
}
