package com.fugary.simple.api.exports.md;

import com.fugary.simple.api.entity.api.ApiDocSchema;
import io.swagger.v3.oas.models.media.Schema;
import lombok.Data;

@Data
public class FmApiDocSchema extends ApiDocSchema {

    private Schema<?> schema;
}
