package com.fugary.simple.api.service.impl.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.AiCache;
import com.fugary.simple.api.mapper.api.AiCacheMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectAccessService;
import com.fugary.simple.api.web.controllers.admin.AiCacheController;
import com.fugary.simple.api.web.vo.query.ai.AiCacheQueryVo;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-record-access;DB_CLOSE_DELAY=-1",
        "dbs.h2.data-dir=target/ai-record-access-test",
        "logging.file.name=target/ai-record-access-test/test.log"
})
class AiCacheAccessTest {
    @Autowired AiCacheMapper mapper;
    @Autowired AiCacheController controller;
    @MockBean ApiProjectAccessService access;

    @BeforeEach
    void setUp() {
        mapper.delete(new QueryWrapper<>());
        ApiUserVo user = new ApiUserVo();
        user.setUserName("alice");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiDocConstants.API_USER_KEY, user);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        doAnswer(call -> {
            QueryWrapper<?> wrapper = call.getArgument(0);
            wrapper.eq("project_id", "10");
            return null;
        }).when(access).addProjectRelatedGroupCodeQuery(any(), eq("t_ai_cache"), eq("project_id"), isNull(), eq("alice"));
    }

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void configFilterDoesNotBypassVisibilityAndDetailsLoadHeavyFieldsOnlyOnDemand() {
        insert("own", "alice", "20", 1);
        insert("readable", "bob", "10", 1);
        insert("hidden", "bob", "20", 1);
        AiCacheQueryVo query = new AiCacheQueryVo();
        query.setConfigId(99);
        query.setCacheType("agent_chat");
        assertThat(controller.search(query).getResultData()).extracting(AiCache::getCacheKey).containsExactlyInAnyOrder("own", "readable");
        AiCache listed = controller.search(query).getResultData().get(0);
        assertThat(listed.getCacheValue()).hasSize(200);
        assertThat(listed.getPrompt()).isNull();
        assertThat(listed.getRawResponse()).isNull();
        assertThat(listed.getExecutionDetail()).isNull();
        assertThat(controller.detail("readable").getResultData().getPrompt()).isEqualTo("private prompt");
        assertThat(controller.detail("hidden").getResultData()).isNull();
        query.setConfigId(100);
        assertThat(controller.search(query).getResultData()).isEmpty();
    }

    @Test
    void pendingRecordCannotBeDeletedEvenByOwner() {
        insert("pending", "alice", "20", 0);
        controller.remove("pending");
        assertThat(mapper.selectById("pending")).isNotNull();
    }

    private void insert(String key, String user, String project, int status) {
        AiCache row = new AiCache();
        row.setCacheKey(key);
        row.setConfigId(99);
        row.setCacheType("agent_chat");
        row.setUserName(user);
        row.setProjectId(project);
        row.setStatus(status);
        row.setCreatedAt(new Date());
        row.setCacheValue("answer".repeat(100));
        row.setPrompt("private prompt");
        row.setRawResponse("raw");
        row.setExecutionDetail("{\"version\":1}");
        mapper.insert(row);
    }
}
