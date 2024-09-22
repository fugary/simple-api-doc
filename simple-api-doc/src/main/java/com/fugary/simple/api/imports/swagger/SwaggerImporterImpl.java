package com.fugary.simple.api.imports.swagger;

import com.fugary.simple.api.imports.ApiDocImporter;
import com.fugary.simple.api.web.vo.imports.ApiProjectDetailVo;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SwaggerImporterImpl implements ApiDocImporter {
    @Override
    public boolean isSupport(String type) {
        return "swagger".equals(type);
    }

    @Override
    public ApiProjectDetailVo doImport(String data) {
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        parseOptions.setResolveRequestBody(true);
        parseOptions.setResolveFully(true);
        SwaggerParseResult result = new OpenAPIParser().readContents(data, null, parseOptions);
        OpenAPI openAPI = result.getOpenAPI();
        if (openAPI != null && openAPI.getTags() != null) {
        }
        return null;
    }
}
