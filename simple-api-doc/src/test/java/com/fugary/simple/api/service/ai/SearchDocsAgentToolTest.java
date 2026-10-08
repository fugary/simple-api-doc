package com.fugary.simple.api.service.ai;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fugary.simple.api.service.ai.agent.tool.AgentContext;
import com.fugary.simple.api.service.ai.agent.tool.impl.SearchDocsAgentTool;
import com.fugary.simple.api.service.apidoc.ApiDocSearchService;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.web.vo.doc.ApiDocSearchResultVo;
import com.fugary.simple.api.web.vo.query.ApiDocSearchQueryVo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchDocsAgentToolTest {
    @Test
    void returnsPagingMetadataAndFullSnippetWhileKeepingProjectScope() throws Exception {
        ApiDocSearchService search = mock(ApiDocSearchService.class);
        SearchDocsAgentTool tool = new SearchDocsAgentTool();
        ReflectionTestUtils.setField(tool, "apiDocSearchService", search);
        ApiDocSearchResultVo doc = new ApiDocSearchResultVo();
        doc.setId(42);
        doc.setSnippet("正文".repeat(110) + "退款");
        Page<ApiDocSearchResultVo> page = new Page<>(2, 8, 24);
        page.setRecords(List.of(doc));
        when(search.search(any(), isNull())).thenReturn(page);
        AgentContext context = AgentContext.builder().defaultProjectId(10).build();
        JsonNode result = JsonUtils.getMapper().readTree(tool.execute(
                "{\"keywords\":\"订单 退款\",\"match\":\"all\",\"page\":2,\"projectId\":30}", context));
        ArgumentCaptor<ApiDocSearchQueryVo> query = ArgumentCaptor.forClass(ApiDocSearchQueryVo.class);
        verify(search).search(query.capture(), isNull());
        assertThat(query.getValue().getProjectId()).isEqualTo(10);
        assertThat(query.getValue().getKeywordMatch()).isEqualTo("all");
        assertThat(query.getValue().getPage().getPageNumber()).isEqualTo(2);
        assertThat(result.path("total").asLong()).isEqualTo(24);
        assertThat(result.path("hasMore").asBoolean()).isTrue();
        assertThat(result.path("docs").get(0).path("description").asText()).endsWith("退款");
        assertThat(context.getDiscoveredDocIds()).containsExactly(42);
    }

    @Test
    void emptyPageStillReportsTotalAndInvalidQueryDoesNotRunSearch() throws Exception {
        ApiDocSearchService search = mock(ApiDocSearchService.class);
        SearchDocsAgentTool tool = new SearchDocsAgentTool();
        ReflectionTestUtils.setField(tool, "apiDocSearchService", search);
        when(search.search(any(), isNull())).thenReturn(new Page<>(2, 8, 8));
        AgentContext context = AgentContext.builder().defaultProjectId(10).build();
        JsonNode result = JsonUtils.getMapper().readTree(tool.execute("{\"keywords\":\"退款\",\"page\":2}", context));
        assertThat(result.path("total").asLong()).isEqualTo(8);
        assertThat(result.path("hasMore").asBoolean()).isFalse();
        assertThat(result.path("docs").size()).isZero();
        tool.execute("{\"keywords\":\"\"}", context);
        tool.execute("{\"keywords\":\"退款\",\"match\":\"invalid\"}", context);
        verify(search, times(1)).search(any(), any());
    }
}
