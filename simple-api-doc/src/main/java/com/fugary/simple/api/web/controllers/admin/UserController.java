package com.fugary.simple.api.web.controllers.admin;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.SimpleQueryVo;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.service.apidoc.ApiUserService;
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

    @Autowired
    private ApiGroupService apiGroupService;

    @GetMapping
    public SimpleResult<List<ApiUser>> search(@ModelAttribute SimpleQueryVo queryVo) {
        Page<ApiUser> page = SimpleResultUtils.toPage(queryVo);
        QueryWrapper<ApiUser> queryWrapper = Wrappers.query();
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        if (StringUtils.isNotBlank(keyword)) {
            queryWrapper.and(wrapper -> wrapper.like("user_name", keyword)
                    .or().like("nick_name", keyword));
        }
        return SimpleResultUtils.createSimpleResult(apiUserService.page(page, queryWrapper));
    }

    @GetMapping("/{id}")
    public SimpleResult<ApiUser> get(@PathVariable("id") Integer id) {
        return SimpleResultUtils.createSimpleResult(apiUserService.getById(id));
    }

    @DeleteMapping("/{id}")
    public SimpleResult remove(@PathVariable("id") Integer id) {
        apiGroupService.deleteUserGroupsByUid(id);
        return SimpleResultUtils.createSimpleResult(apiUserService.deleteUser(id));
    }

    @PostMapping
    public SimpleResult save(@RequestBody ApiUser user) {
        if (apiUserService.existsUser(user)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_1001);
        }
        if (!SecurityUtils.validateUserUpdate(user.getUserName())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        boolean needEncryptPassword= true;
        if (user.getId() != null) {
            ApiUser existUser = apiUserService.getById(user.getId());
            needEncryptPassword = existUser == null || !StringUtils.equalsIgnoreCase(existUser.getUserPassword(), user.getUserPassword());
        }
        if (needEncryptPassword) {
            user.setUserPassword(apiUserService.encryptPassword(user.getUserPassword()));
        }
        return SimpleResultUtils.createSimpleResult(apiUserService.saveOrUpdate(SimpleModelUtils.addAuditInfo(user)));
    }

    @GetMapping("/info")
    public SimpleResult<ApiUser> info(@RequestParam("token") String token) {
        DecodedJWT decode = JWT.decode(token);
        String name = decode.getClaim("name").asString();
        return SimpleResultUtils.createSimpleResult(apiUserService.getOne(Wrappers.<ApiUser>query().eq("user_name", name)));
    }
}
