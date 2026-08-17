package com.fugary.simple.api.imports;

import com.fugary.simple.api.utils.SimpleModelUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ApiProjectImportFileTest {

    @Test
    public void testSupportedImportFile() {
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.json"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.JSON"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.yaml"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.YAML"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.yml"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.YML"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("openapi.v3.json"));

        Assertions.assertFalse(SimpleModelUtils.isSupportedImportFile("test.xlsx"));
        Assertions.assertFalse(SimpleModelUtils.isSupportedImportFile("test.xls"));
        Assertions.assertFalse(SimpleModelUtils.isSupportedImportFile("test.csv"));
        Assertions.assertFalse(SimpleModelUtils.isSupportedImportFile("test.txt"));
        Assertions.assertFalse(SimpleModelUtils.isSupportedImportFile("test.docx"));
        Assertions.assertFalse(SimpleModelUtils.isSupportedImportFile(""));
        Assertions.assertFalse(SimpleModelUtils.isSupportedImportFile(null));
    }
}
