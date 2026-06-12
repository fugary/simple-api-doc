package com.fugary.simple.api.web.controllers.admin;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiGroup;
import com.fugary.simple.api.entity.api.ApiUserGroup;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiUserService;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.ProjectQueryVo;
import com.fugary.simple.api.web.vo.user.ApiGroupVo;
import com.fugary.simple.api.web.vo.user.ApiUserGroupVo;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiGroupControllerTest {

    @Mock
    private ApiGroupService apiGroupService;

    @Mock
    private ApiUserService apiUserService;

    @Mock
    private ApiProjectService apiProjectService;

    @InjectMocks
    private ApiGroupController apiGroupController;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void readableUserLoadsVisibleGroupsWithAllGroupUsers() {
        ApiUserVo alice = loginAs(2, "alice");
        ApiGroupVo team = groupVo("team", "bob", "readable");
        ApiGroupVo hidden = groupVo("hidden", "carol", "");
        List<ApiUserGroup> teamUsers = List.of(userGroup(1, "team", "readable,writable,deletable"),
                userGroup(2, "team", "readable"));
        when(apiUserService.loadUser("alice")).thenReturn(alice);
        when(apiGroupService.loadUserGroups(alice)).thenReturn(List.of(team, hidden));
        when(apiGroupService.checkGroupAccess(alice, "team", ApiGroupAuthority.READABLE)).thenReturn(true);
        when(apiGroupService.checkGroupAccess(alice, "hidden", ApiGroupAuthority.READABLE)).thenReturn(false);
        when(apiGroupService.loadGroupUsers(List.of("team"))).thenReturn(teamUsers);

        SimpleResult<List<ApiGroupVo>> result = apiGroupController.loadProjectGroups(new ProjectQueryVo());

        assertThat(result.getResultData()).containsExactly(team);
        assertThat(result.getResultData().get(0).getAuthorities()).isEqualTo("readable");
        assertThat(result.getResultData().get(0).getUserGroups()).containsExactlyElementsOf(teamUsers);
    }

    @Test
    void nonOwnerCannotLoadOrSaveGroupUsers() {
        loginAs(2, "alice");
        ApiGroup team = group("team", "bob");
        when(apiGroupService.loadGroup("team")).thenReturn(team);
        ApiUserGroupVo userGroupVo = new ApiUserGroupVo();
        userGroupVo.setGroupCode("team");

        SimpleResult<List<ApiUserGroup>> loadResult = apiGroupController.loadGroupUsers("team");
        SimpleResult<Boolean> saveResult = apiGroupController.saveUserGroups(userGroupVo);

        assertThat(loadResult.getCode()).isEqualTo(SystemErrorConstants.CODE_403);
        assertThat(saveResult.getCode()).isEqualTo(SystemErrorConstants.CODE_403);
        verify(apiGroupService, never()).loadGroupUsers("team");
        verify(apiGroupService, never()).saveUserGroups(any());
    }

    private ApiUserVo loginAs(Integer id, String userName) {
        ApiUserVo loginUser = new ApiUserVo();
        loginUser.setId(id);
        loginUser.setUserName(userName);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiDocConstants.API_USER_KEY, loginUser);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return loginUser;
    }

    private ApiGroup group(String groupCode, String userName) {
        ApiGroup group = new ApiGroup();
        group.setGroupCode(groupCode);
        group.setUserName(userName);
        return group;
    }

    private ApiGroupVo groupVo(String groupCode, String userName, String authorities) {
        ApiGroupVo groupVo = new ApiGroupVo();
        groupVo.setGroupCode(groupCode);
        groupVo.setUserName(userName);
        groupVo.setAuthorities(authorities);
        return groupVo;
    }

    private ApiUserGroup userGroup(Integer userId, String groupCode, String authorities) {
        ApiUserGroup userGroup = new ApiUserGroup();
        userGroup.setUserId(userId);
        userGroup.setGroupCode(groupCode);
        userGroup.setAuthorities(authorities);
        return userGroup;
    }
}
