package com.fugary.simple.api.service;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.service.impl.apidoc.ApiFolderServiceImpl;
import com.fugary.simple.api.web.vo.exports.ExportApiDocVo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ApiFolderDocSyncTest {

    static class TestableApiFolderService extends ApiFolderServiceImpl {
        @Override
        public void processModifiedApiDoc(ExportApiDocVo apiDocVo, ApiDoc existsDoc) {
            super.processModifiedApiDoc(apiDocVo, existsDoc);
        }
    }

    @Test
    public void testMarkdownDocContentUpdateOnReImport() {
        TestableApiFolderService service = new TestableApiFolderService();

        // 模拟已存在于数据库中的 Markdown 文档（旧版本）
        ApiDoc existsDoc = new ApiDoc();
        existsDoc.setId(100);
        existsDoc.setDocType(ApiDocConstants.DOC_TYPE_MD);
        existsDoc.setDocName("快速上手");
        existsDoc.setSummary("快速上手");
        existsDoc.setDocContent("# 快速上手\n\n旧的正文内容。");
        existsDoc.setVersion(1);

        // 模拟从 Git 重新拉取并解析到的新 Markdown 文档数据（内容已更新）
        ExportApiDocVo newDocVo = new ExportApiDocVo();
        newDocVo.setDocType(ApiDocConstants.DOC_TYPE_MD);
        newDocVo.setDocName("快速上手");
        newDocVo.setSummary("快速上手");
        newDocVo.setDocContent("# 快速上手\n\n从 Git 更新后的最新正文内容！");

        service.processModifiedApiDoc(newDocVo, existsDoc);

        // 验证：Markdown 文档应当保留并覆盖为从 Git 抓取的最新正文，而不会被数据库旧内容覆盖
        Assertions.assertEquals("# 快速上手\n\n从 Git 更新后的最新正文内容！", newDocVo.getDocContent());
        Assertions.assertEquals("快速上手", newDocVo.getDocName());
    }

    @Test
    public void testMarkdownDocNamePreservedWhenModifiedInUi() {
        TestableApiFolderService service = new TestableApiFolderService();

        // 模拟用户在 UI 中对文档进行了重命名 (docName != summary)
        ApiDoc existsDoc = new ApiDoc();
        existsDoc.setId(101);
        existsDoc.setDocType(ApiDocConstants.DOC_TYPE_MD);
        existsDoc.setDocName("用户自定义重命名");
        existsDoc.setSummary("原标题");
        existsDoc.setDocContent("# 原标题\n\n正文。");

        // 从 Git 重新拉取的文档
        ExportApiDocVo newDocVo = new ExportApiDocVo();
        newDocVo.setDocType(ApiDocConstants.DOC_TYPE_MD);
        newDocVo.setDocName("原标题");
        newDocVo.setSummary("原标题");
        newDocVo.setDocContent("# 原标题\n\n更新后的正文。");

        service.processModifiedApiDoc(newDocVo, existsDoc);

        // 验证：用户自定义的 docName 得到保护和保留，同时正文内容更新为 Git 最新内容
        Assertions.assertEquals("用户自定义重命名", newDocVo.getDocName());
        Assertions.assertEquals("# 原标题\n\n更新后的正文。", newDocVo.getDocContent());
    }

    @Test
    public void testMarkdownDocTitleUpdatedWhenModifiedInGit() {
        TestableApiFolderService service = new TestableApiFolderService();

        // 用户未在 UI 中重命名 (docName == summary)
        ApiDoc existsDoc = new ApiDoc();
        existsDoc.setId(102);
        existsDoc.setDocType(ApiDocConstants.DOC_TYPE_MD);
        existsDoc.setDocName("旧标题");
        existsDoc.setSummary("旧标题");
        existsDoc.setDocContent("# 旧标题\n\n正文。");

        // Git 中 Markdown 文件的标题被修改为 "新标题"
        ExportApiDocVo newDocVo = new ExportApiDocVo();
        newDocVo.setDocType(ApiDocConstants.DOC_TYPE_MD);
        newDocVo.setDocName("新标题");
        newDocVo.setSummary("新标题");
        newDocVo.setDocContent("# 新标题\n\n正文。");

        service.processModifiedApiDoc(newDocVo, existsDoc);

        // 验证：docName 自动同步为 Git 中的新标题
        Assertions.assertEquals("新标题", newDocVo.getDocName());
    }

    @Test
    public void testMarkdownDocLockedAndDeprecatedPreserved() {
        TestableApiFolderService service = new TestableApiFolderService();

        ApiDoc existsDoc = new ApiDoc();
        existsDoc.setId(103);
        existsDoc.setDocType(ApiDocConstants.DOC_TYPE_MD);
        existsDoc.setDocName("配置说明");
        existsDoc.setSummary("配置说明");
        existsDoc.setDocContent("# 配置说明");
        existsDoc.setLocked(true);
        existsDoc.setDeprecated(true);

        ExportApiDocVo newDocVo = new ExportApiDocVo();
        newDocVo.setDocType(ApiDocConstants.DOC_TYPE_MD);
        newDocVo.setDocName("配置说明");
        newDocVo.setSummary("配置说明");
        newDocVo.setDocContent("# 配置说明\n\n新内容");

        service.processModifiedApiDoc(newDocVo, existsDoc);

        // 验证：导入未指定 locked/deprecated 时，保留已有状态
        Assertions.assertTrue(Boolean.TRUE.equals(newDocVo.getLocked()));
        Assertions.assertTrue(Boolean.TRUE.equals(newDocVo.getDeprecated()));
    }

    @Test
    public void testApiDocCustomDocContentPreservedOnReImport() {
        TestableApiFolderService service = new TestableApiFolderService();

        // 模拟 API 接口文档，用户在 UI 中编写了自定义说明 docContent
        ApiDoc existsDoc = new ApiDoc();
        existsDoc.setId(200);
        existsDoc.setDocType(ApiDocConstants.DOC_TYPE_API);
        existsDoc.setDocName("查询用户列表");
        existsDoc.setSummary("查询用户列表");
        existsDoc.setDocContent("这是开发人员在 UI 中手动补充的接口调用注意事项");

        // 模拟从 Swagger/OpenAPI 重新导入的 API 接口（Swagger 导入的 docContent 通常为空）
        ExportApiDocVo apiDocVo = new ExportApiDocVo();
        apiDocVo.setDocType(ApiDocConstants.DOC_TYPE_API);
        apiDocVo.setDocName("查询用户列表");
        apiDocVo.setSummary("查询用户列表");
        apiDocVo.setDocContent(null);

        service.processModifiedApiDoc(apiDocVo, existsDoc);

        // 验证：API 接口文档的用户自定义 docContent 被完好保留
        Assertions.assertEquals("这是开发人员在 UI 中手动补充的接口调用注意事项", apiDocVo.getDocContent());
    }

    static class TestableApiDocService extends com.fugary.simple.api.service.impl.apidoc.ApiDocServiceImpl {
        @Override
        public boolean isSameApiDoc(ApiDoc apiDoc, ApiDoc existsDoc) {
            return super.isSameApiDoc(apiDoc, existsDoc);
        }
    }

    @Test
    public void testIsSameApiDoc() {
        TestableApiDocService docService = new TestableApiDocService();

        ApiDoc doc1 = new ApiDoc();
        doc1.setId(1);
        doc1.setDocName("Doc1");
        doc1.setDocContent("Content1");
        doc1.setVersion(1);

        ApiDoc doc2 = new ApiDoc();
        doc2.setId(1);
        doc2.setDocName("Doc1");
        doc2.setDocContent("Content1");
        doc2.setVersion(2); // 忽略 version
        doc2.setModifier("admin"); // 忽略 audit

        Assertions.assertTrue(docService.isSameApiDoc(doc1, doc2));

        doc2.setDocContent("Content2"); // 内容改变
        Assertions.assertFalse(docService.isSameApiDoc(doc1, doc2));
    }
}
