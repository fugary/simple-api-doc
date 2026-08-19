package com.fugary.simple.api.service.ai;

import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.web.vo.AiGenericTaskReq;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AiGenericTaskReqTest {

    @Test
    public void testAiGenericTaskReqProperties() {
        AiGenericTaskReq req = new AiGenericTaskReq();
        req.setSchemaContent("{\"type\":\"object\"}");
        req.setPrompt("生成用户模型");
        req.setLang("zh-CN");
        req.setMode("all");
        req.setWithExample(true);
        req.setProjectId("proj-1");
        req.setDocId("doc-1");
        req.setConfigId(10);
        req.setModel("gpt-4o");

        AiConfig config = new AiConfig();
        config.setConfigName("test-config");
        req.setConfig(config);

        Assertions.assertEquals("{\"type\":\"object\"}", req.getSchemaContent());
        Assertions.assertEquals("生成用户模型", req.getPrompt());
        Assertions.assertEquals("zh-CN", req.getLang());
        Assertions.assertEquals("all", req.getMode());
        Assertions.assertTrue(req.getWithExample());
        Assertions.assertEquals("proj-1", req.getProjectId());
        Assertions.assertEquals("doc-1", req.getDocId());
        Assertions.assertEquals(10, req.getConfigId());
        Assertions.assertEquals("gpt-4o", req.getModel());
        Assertions.assertNotNull(req.getConfig());
    }
}
