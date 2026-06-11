package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.service.apidoc.ApiLogService;
import com.fugary.simple.api.service.apidoc.ApiProjectAccessService;
import com.fugary.simple.api.web.vo.query.log.ApiLogQueryVo;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiLogControllerTest {

    @Mock
    private ApiLogService apiLogService;

    @Mock
    private ApiProjectAccessService apiProjectAccessService;

    @InjectMocks
    private ApiLogController apiLogController;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void normalUserDefaultSearchIncludesOwnAndReadableProjectLogs() {
        loginAs(2, "alice");
        when(apiProjectAccessService.loadReadableGroupCodesSql("alice")).thenReturn("team");
        when(apiLogService.page(any(Page.class), any())).thenReturn(new Page<>());

        apiLogController.search(new ApiLogQueryVo());

        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(apiLogService).page(any(Page.class), queryCaptor.capture());
        String sqlSegment = queryCaptor.getValue().getCustomSqlSegment();
        assertThat(sqlSegment)
                .contains("user_name", "t_api_log.project_id", "p.user_name", "group_code in ('team')");
    }

    private void loginAs(Integer id, String userName) {
        ApiUserVo loginUser = new ApiUserVo();
        loginUser.setId(id);
        loginUser.setUserName(userName);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiDocConstants.API_USER_KEY, loginUser);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
