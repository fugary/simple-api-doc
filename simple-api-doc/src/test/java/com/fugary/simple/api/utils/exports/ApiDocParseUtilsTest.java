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
