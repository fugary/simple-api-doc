package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.service.apidoc.ApiUserService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.SimpleQueryVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Created on 2020/5/5 18:40 .<br>
 *
 * @author gary.fu
 */
@Slf4j
@RestController
@RequestMapping("/admin/users")
public class UserController {

    @Autowired
    private ApiUserService apiUserService;

    @GetMapping
    public SimpleResult<List<ApiUser>> search(@ModelAttribute SimpleQueryVo queryVo) {
        Page<ApiUser> page = SimpleResultUtils.toPage(queryVo);
        QueryWrapper<ApiUser> queryWrapper = Wrappers.<ApiUser>query()
                .eq(queryVo.getStatus() != null, ApiDocConstants.STATUS_KEY, queryVo.getStatus());
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        queryWrapper.and(StringUtils.isNotBlank(keyword), wrapper -> wrapper.like("user_name", keyword)
                .or().like("nick_name", keyword)
                .or().like("user_email", keyword));
        return SimpleResultUtils.createSimpleResult(apiUserService.page(page, queryWrapper));
    }

    @GetMapping("/{id}")
    public SimpleResult<ApiUser> get(@PathVariable("id") Integer id) {
        return SimpleResultUtils.createSimpleResult(apiUserService.getById(id));
    }

    @DeleteMapping("/{id}")
    public SimpleResult<Boolean> remove(@PathVariable("id") Integer id) {
        if (!SecurityUtils.isAdmin()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        ApiUser user = apiUserService.getById(id);
        if (user == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (SecurityUtils.isAdmin(user.getUserName())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiUserService.deleteUser(id));
    }

    @PostMapping
    public SimpleResult<Boolean> save(@RequestBody ApiUser user) {
        ApiUser existUser = null;
        if (user.getId() != null) {
            existUser = apiUserService.getById(user.getId());
            if (existUser == null) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
            }
        }
        if (!SecurityUtils.isAdmin() && (existUser == null || !SecurityUtils.validateUserUpdate(existUser.getUserName()))) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        if (!SecurityUtils.isAdmin()) {
            user.setUserName(existUser.getUserName());
            user.setStatus(existUser.getStatus());
        }
        if (StringUtils.isBlank(user.getUserName())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_400);
        }
        if (apiUserService.existsUser(user)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_1001);
        }
        if (StringUtils.isBlank(user.getUserPassword())) {
            if (existUser == null) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_400);
            }
            user.setUserPassword(existUser.getUserPassword());
        } else if (existUser == null || !StringUtils.equalsIgnoreCase(existUser.getUserPassword(), user.getUserPassword())) {
            user.setUserPassword(apiUserService.encryptPassword(user.getUserPassword()));
        }
        if (existUser != null && SimpleModelUtils.isSameData(user, existUser)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2000);
        }
        SimpleModelUtils.mergeCreateInfo(user, existUser);
        boolean saveResult = apiUserService.saveOrUpdate(SimpleModelUtils.addAuditInfo(user));
        apiUserService.updateUserName(user, existUser);
        return SimpleResultUtils.createSimpleResult(saveResult);
    }

    @GetMapping("/info")
    public SimpleResult<ApiUser> info() {
        ApiUser loginUser = SecurityUtils.getLoginUser();
        return loginUser == null ? SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_401)
                : SimpleResultUtils.createSimpleResult(loginUser);
    }
}
