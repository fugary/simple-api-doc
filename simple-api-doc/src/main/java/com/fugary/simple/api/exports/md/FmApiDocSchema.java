package com.fugary.simple.api.exports.md;

import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import io.swagger.v3.oas.models.media.Schema;
import lombok.Data;

@Data
public class FmApiDocSchema extends ApiProjectInfoDetail {

    private Schema<?> schema;
}
