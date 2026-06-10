package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.service.apidoc.ApiUserService;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.SimpleQueryVo;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private ApiUserService apiUserService;

    @InjectMocks
    private UserController userController;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void normalUserCanReadUsersAndBasicInfo() {
        loginAs(2, "alice");
        ApiUser bob = user(3, "bob");
        Page<ApiUser> page = new Page<>(1, 10);
        page.setRecords(List.of(bob));
        page.setTotal(1);
        when(apiUserService.page(any(Page.class), any())).thenReturn(page);
        when(apiUserService.getById(3)).thenReturn(bob);

        SimpleResult<List<ApiUser>> searchResult = userController.search(new SimpleQueryVo());
        SimpleResult<ApiUser> getResult = userController.get(3);

        assertThat(searchResult.getResultData()).containsExactly(bob);
        assertThat(getResult.getResultData()).isSameAs(bob);
    }

    @Test
    void removeRequiresAdminAndKeepsAdminAccount() {
        loginAs(2, "alice");

        SimpleResult<Boolean> notAdminResult = userController.remove(3);

        assertThat(notAdminResult.getCode()).isEqualTo(SystemErrorConstants.CODE_403);
        verify(apiUserService, never()).deleteUser(any());

        loginAs(1, "admin");
        ApiUser admin = user(1, "admin");
        when(apiUserService.getById(1)).thenReturn(admin);

        SimpleResult<Boolean> adminDeleteResult = userController.remove(1);

        assertThat(adminDeleteResult.getCode()).isEqualTo(SystemErrorConstants.CODE_403);
        verify(apiUserService, never()).deleteUser(1);
        ApiUser alice = user(2, "alice");
        when(apiUserService.getById(2)).thenReturn(alice);
        when(apiUserService.deleteUser(2)).thenReturn(true);

        SimpleResult<Boolean> result = userController.remove(2);

        assertThat(result.getCode()).isEqualTo(SystemErrorConstants.CODE_0);
        verify(apiUserService).deleteUser(2);
    }

    @Test
    void normalUserCannotSaveOtherUser() {
        loginAs(2, "alice");
        ApiUser bob = user(3, "bob");
        when(apiUserService.getById(3)).thenReturn(bob);

        ApiUser updateUser = user(3, "bob");
        updateUser.setUserPassword("password");
        SimpleResult<Boolean> result = userController.save(updateUser);

        assertThat(result.getCode()).isEqualTo(SystemErrorConstants.CODE_403);
        verify(apiUserService, never()).saveOrUpdate(any());
    }

    @Test
    void normalUserSaveSelfPreservesManagedFieldsAndExistingPassword() {
        loginAs(2, "alice");
        ApiUser existsUser = user(2, "alice");
        existsUser.setStatus(ApiDocConstants.STATUS_ENABLED);
        existsUser.setUserPassword("old-hash");
        when(apiUserService.getById(2)).thenReturn(existsUser);
        when(apiUserService.existsUser(any())).thenReturn(false);
        when(apiUserService.saveOrUpdate(any())).thenReturn(true);

        ApiUser updateUser = user(2, "mallory");
        updateUser.setStatus(ApiDocConstants.STATUS_DISABLED);
        updateUser.setUserPassword("");
        updateUser.setNickName("Alice Updated");
        SimpleResult<Boolean> result = userController.save(updateUser);

        ArgumentCaptor<ApiUser> userCaptor = ArgumentCaptor.forClass(ApiUser.class);
        verify(apiUserService).saveOrUpdate(userCaptor.capture());
        ApiUser savedUser = userCaptor.getValue();
        assertThat(result.getCode()).isEqualTo(SystemErrorConstants.CODE_0);
        assertThat(savedUser.getUserName()).isEqualTo("alice");
        assertThat(savedUser.getStatus()).isEqualTo(ApiDocConstants.STATUS_ENABLED);
        assertThat(savedUser.getUserPassword()).isEqualTo("old-hash");
        assertThat(savedUser.getNickName()).isEqualTo("Alice Updated");
    }

    @Test
    void userPasswordIsWriteOnlyInJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ApiUser user = user(2, "alice");
        user.setUserPassword("stored-hash");

        String json = mapper.writeValueAsString(user);
        ApiUser parsedUser = mapper.readValue("{\"userName\":\"alice\",\"userPassword\":\"plain-password\"}", ApiUser.class);

        assertThat(json).doesNotContain("userPassword");
        assertThat(json).doesNotContain("stored-hash");
        assertThat(parsedUser.getUserPassword()).isEqualTo("plain-password");
    }

    private void loginAs(Integer id, String userName) {
        ApiUserVo loginUser = new ApiUserVo();
        loginUser.setId(id);
        loginUser.setUserName(userName);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiDocConstants.API_USER_KEY, loginUser);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private ApiUser user(Integer id, String userName) {
        ApiUser user = new ApiUser();
        user.setId(id);
        user.setUserName(userName);
        user.setNickName(userName);
        user.setUserEmail(userName + "@example.com");
        return user;
    }
}
