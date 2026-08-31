package com.fugary.simple.api.utils.exports;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectInfoDetailVo;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDocParseUtilsTest {

    @Test
    void sameProjectInfoDetailDoesNotTriggerUpdateWhenOnlyIdDiffers() {
        ApiProjectInfoDetail existsDetail = detail(ApiProjectInfoDetail.class);
        existsDetail.setId(10);
        existsDetail.setVersion(3);
        existsDetail.setCreator("import");
        existsDetail.setCreateDate(new Date(1000));
        ExportApiProjectInfoDetailVo importDetail = detail(ExportApiProjectInfoDetailVo.class);

        Pair<ExportApiProjectInfoDetailVo, ApiProjectInfoDetail> result = ApiDocParseUtils.processProjectInfoDetail(
                Map.of(ApiDocParseUtils.getProjectInfoDetailKey(importDetail), existsDetail), importDetail, false);

        assertThat(result.getLeft()).isNull();
        assertThat(result.getRight()).isNull();
        assertThat(existsDetail.getModifyDate()).isNull();
        assertThat(importDetail.getId()).isEqualTo(existsDetail.getId());
    }

    @Test
    void testCalcNewDocKeyUsesFolderCodeOverRenamedFolderName() {
        com.fugary.simple.api.entity.api.ApiDoc doc = new com.fugary.simple.api.entity.api.ApiDoc();
        doc.setDocType(ApiDocConstants.DOC_TYPE_API);
        doc.setOperationId("getUserInfo");
        doc.setUrl("/api/user/info");
        doc.setMethod("GET");

        com.fugary.simple.api.entity.api.ApiFolder folder = new com.fugary.simple.api.entity.api.ApiFolder();
        folder.setFolderCode("user-controller");
        folder.setFolderName("用户接口管理"); // 改名后的名称

        ApiDocParseUtils.calcNewDocKey(doc, folder);

        // 验证 docKey 使用的是 folderCode 而非改名后的 folderName
        assertThat(doc.getDocKey()).isEqualTo("user-controller#getUserInfo");
    }

    @Test
    void testGenerateOperationId() {
        // 普通路径
        assertThat(ApiDocParseUtils.generateOperationId("GET", "/users")).isEqualTo("getUsers");
        assertThat(ApiDocParseUtils.generateOperationId("POST", "/api/v1/orders")).isEqualTo("postApiV1Orders");
        assertThat(ApiDocParseUtils.generateOperationId("DELETE", "/user-group/info")).isEqualTo("deleteUserGroupInfo");

        // 带路径参数
        assertThat(ApiDocParseUtils.generateOperationId("GET", "/users/{id}")).isEqualTo("getUsersById");
        assertThat(ApiDocParseUtils.generateOperationId("GET", "/users/{userId}")).isEqualTo("getUsersByUserId");
        assertThat(ApiDocParseUtils.generateOperationId("DELETE", "/users/{user_id}")).isEqualTo("deleteUsersByUserId");
        assertThat(ApiDocParseUtils.generateOperationId("PUT", "/orders/{orderId}/status")).isEqualTo("putOrdersStatusByOrderId");
        assertThat(ApiDocParseUtils.generateOperationId("GET", "/users/{userId}/orders/{orderId}")).isEqualTo("getUsersOrdersByUserIdAndOrderId");

        // 带 Query 参数与 hash
        assertThat(ApiDocParseUtils.generateOperationId("GET", "/api/search?keyword=test&page=1")).isEqualTo("getApiSearch");

        // 边界情况
        assertThat(ApiDocParseUtils.generateOperationId(null, "/users")).isEqualTo("apiUsers");
        assertThat(ApiDocParseUtils.generateOperationId("GET", "")).isNotBlank();
    }

    @Test
    void testToCamelCase() {
        assertThat(ApiDocParseUtils.toCamelCase("users")).isEqualTo("users");
        assertThat(ApiDocParseUtils.toCamelCase("user-info")).isEqualTo("userInfo");
        assertThat(ApiDocParseUtils.toCamelCase("order_detail_info")).isEqualTo("orderDetailInfo");
        assertThat(ApiDocParseUtils.toCamelCase("userId")).isEqualTo("userId");
    }

    @Test
    void testCalcApiPathFolderSetsFolderCodeAndName() {
        java.util.List<com.fugary.simple.api.web.vo.exports.ExportApiFolderVo> folders = new java.util.ArrayList<>();
        Pair<com.fugary.simple.api.web.vo.exports.ExportApiFolderVo, com.fugary.simple.api.web.vo.exports.ExportApiFolderVo> pair =
                ApiDocParseUtils.calcApiPathFolder(folders, "system/user");

        assertThat(pair.getLeft()).isNotNull();
        assertThat(pair.getLeft().getFolderCode()).isEqualTo("user");
        assertThat(pair.getLeft().getFolderName()).isEqualTo("user");
        assertThat(pair.getLeft().getFolderPath()).isEqualTo("system/user");

        assertThat(pair.getRight()).isNotNull();
        assertThat(pair.getRight().getFolderCode()).isEqualTo("system");
        assertThat(pair.getRight().getFolderName()).isEqualTo("system");
    }

    @Test
    void testCalcApiPathFolderWithDistinctFolderCodePath() {
        java.util.List<com.fugary.simple.api.web.vo.exports.ExportApiFolderVo> folders = new java.util.ArrayList<>();
        Pair<com.fugary.simple.api.web.vo.exports.ExportApiFolderVo, com.fugary.simple.api.web.vo.exports.ExportApiFolderVo> pair =
                ApiDocParseUtils.calcApiPathFolder(folders, "系统管理/用户中心", "system/user-api");

        assertThat(pair.getLeft()).isNotNull();
        assertThat(pair.getLeft().getFolderCode()).isEqualTo("user-api");
        assertThat(pair.getLeft().getFolderName()).isEqualTo("用户中心");
        assertThat(pair.getLeft().getFolderPath()).isEqualTo("system/user-api");

        assertThat(pair.getRight()).isNotNull();
        assertThat(pair.getRight().getFolderCode()).isEqualTo("system");
        assertThat(pair.getRight().getFolderName()).isEqualTo("系统管理");
        assertThat(pair.getRight().getFolderPath()).isEqualTo("system");
    }

    @Test
    void testCalcApiPathFolderSingleLevelWithDistinctFolderCode() {
        java.util.List<com.fugary.simple.api.web.vo.exports.ExportApiFolderVo> folders = new java.util.ArrayList<>();
        Pair<com.fugary.simple.api.web.vo.exports.ExportApiFolderVo, com.fugary.simple.api.web.vo.exports.ExportApiFolderVo> pair =
                ApiDocParseUtils.calcApiPathFolder(folders, "用户中心", "user-api");

        assertThat(pair.getLeft()).isNotNull();
        assertThat(pair.getLeft().getFolderCode()).isEqualTo("user-api");
        assertThat(pair.getLeft().getFolderName()).isEqualTo("用户中心");
        assertThat(pair.getLeft().getFolderPath()).isEqualTo("user-api");
    }

    @Test
    void testGetFolderSortKeyAndDocSortKeyOrder() {
        com.fugary.simple.api.entity.api.ApiFolder root1 = new com.fugary.simple.api.entity.api.ApiFolder();
        root1.setId(1);
        root1.setSortId(10);

        com.fugary.simple.api.entity.api.ApiFolder sub1 = new com.fugary.simple.api.entity.api.ApiFolder();
        sub1.setId(2);
        sub1.setParentId(1);
        sub1.setSortId(10);

        com.fugary.simple.api.entity.api.ApiFolder root2 = new com.fugary.simple.api.entity.api.ApiFolder();
        root2.setId(3);
        root2.setSortId(20);

        Map<Integer, com.fugary.simple.api.entity.api.ApiFolder> folderMap = Map.of(1, root1, 2, sub1, 3, root2);

        com.fugary.simple.api.entity.api.ApiDoc docInRoot1 = new com.fugary.simple.api.entity.api.ApiDoc();
        docInRoot1.setId(100);
        docInRoot1.setFolderId(1);
        docInRoot1.setSortId(10);

        com.fugary.simple.api.entity.api.ApiDoc doc1InSub1 = new com.fugary.simple.api.entity.api.ApiDoc();
        doc1InSub1.setId(101);
        doc1InSub1.setFolderId(2);
        doc1InSub1.setSortId(10);

        com.fugary.simple.api.entity.api.ApiDoc doc2InSub1 = new com.fugary.simple.api.entity.api.ApiDoc();
        doc2InSub1.setId(102);
        doc2InSub1.setFolderId(2);
        doc2InSub1.setSortId(20);

        com.fugary.simple.api.entity.api.ApiDoc docInRoot2 = new com.fugary.simple.api.entity.api.ApiDoc();
        docInRoot2.setId(103);
        docInRoot2.setFolderId(3);
        docInRoot2.setSortId(10);

        java.util.List<com.fugary.simple.api.entity.api.ApiDoc> docs = new java.util.ArrayList<>(
                java.util.List.of(doc2InSub1, docInRoot2, doc1InSub1, docInRoot1));

        docs.sort(java.util.Comparator.comparing(d -> ApiDocParseUtils.getDocSortKey(d, folderMap)));

        assertThat(docs).containsExactly(docInRoot1, doc1InSub1, doc2InSub1, docInRoot2);
    }

    @Test
    void testGetFolderNames() {
        com.fugary.simple.api.entity.api.ApiFolder root = new com.fugary.simple.api.entity.api.ApiFolder();
        root.setId(1);
        root.setFolderName("ProjectRoot");
        root.setRootFlag(true);

        com.fugary.simple.api.entity.api.ApiFolder system = new com.fugary.simple.api.entity.api.ApiFolder();
        system.setId(2);
        system.setParentId(1);
        system.setFolderName("系统管理");

        com.fugary.simple.api.entity.api.ApiFolder user = new com.fugary.simple.api.entity.api.ApiFolder();
        user.setId(3);
        user.setParentId(2);
        user.setFolderName("用户中心");

        Map<Integer, com.fugary.simple.api.entity.api.ApiFolder> folderMap = Map.of(1, root, 2, system, 3, user);

        assertThat(ApiDocParseUtils.getFolderNames(3, folderMap)).containsExactly("系统管理", "用户中心");
        assertThat(ApiDocParseUtils.getFolderNames(1, folderMap)).isEmpty();
        assertThat(ApiDocParseUtils.getFolderNames(null, folderMap)).isEmpty();
    }

    private <T extends ApiProjectInfoDetail> T detail(Class<T> type) {
        try {
            T detail = type.getDeclaredConstructor().newInstance();
            detail.setProjectId(1);
            detail.setInfoId(2);
            detail.setBodyType(ApiDocConstants.PROJECT_SCHEMA_TYPE_COMPONENT);
            detail.setSchemaName("User");
            detail.setSchemaContent("{\"type\":\"object\"}");
            return detail;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
