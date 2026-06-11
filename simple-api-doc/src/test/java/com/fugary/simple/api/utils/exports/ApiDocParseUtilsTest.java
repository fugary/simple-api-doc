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
