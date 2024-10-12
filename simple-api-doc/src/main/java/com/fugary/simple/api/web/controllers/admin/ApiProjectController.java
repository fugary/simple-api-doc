package com.fugary.simple.api.web.controllers.admin;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.query.JwtParamVo;
import com.fugary.simple.api.web.vo.query.ProjectDetailQueryVo;
import com.fugary.simple.api.web.vo.query.ProjectQueryVo;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.fugary.simple.api.utils.security.SecurityUtils.getLoginUser;

/**
 * Create date 2024/7/15<br>
 *
 * @author gary.fu
 */
@RestController
@RequestMapping("/admin/projects")
public class ApiProjectController {

    @Autowired
    private ApiProjectService apiProjectService;

    @GetMapping
    public SimpleResult<List<ApiProject>> search(@ModelAttribute ProjectQueryVo queryVo) {
        Page<ApiProject> page = SimpleResultUtils.toPage(queryVo);
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        String userName = SecurityUtils.getUserName(queryVo.getUserName());
        QueryWrapper<ApiProject> queryWrapper = Wrappers.<ApiProject>query()
                .like(StringUtils.isNotBlank(keyword), "project_name", keyword)
                .eq(queryVo.getStatus() != null, "status", queryVo.getStatus())
                .eq("user_name", userName);
        return SimpleResultUtils.createSimpleResult(apiProjectService.page(page, queryWrapper));
    }

    @GetMapping("/{id}")
    public SimpleResult<ApiProject> get(@PathVariable("id") Integer id) {
        return SimpleResultUtils.createSimpleResult(apiProjectService.getById(id));
    }

    @PostMapping("/copy/{id}")
    public SimpleResult<ApiProject> copy(@PathVariable("id") Integer id) {
        ApiProject project = apiProjectService.getById(id);
        if (project == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        return SimpleResultUtils.createSimpleResult(apiProjectService.copyProject(project));
    }

    @GetMapping("/loadDetail/{projectCode}")
    public SimpleResult<ApiProjectDetailVo> loadDetail(@PathVariable("projectCode") String projectCode) {
        ApiProjectDetailVo detailVo = apiProjectService.loadProjectVo(ProjectDetailQueryVo.builder()
                .projectCode(projectCode)
                .includeDocs(true)
                .includeTasks(true)
                .includesShares(true).build());
        if (detailVo == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        return SimpleResultUtils.createSimpleResult(detailVo);
    }

    @GetMapping("/loadBasic/{projectCode}")
    public SimpleResult<ApiProjectDetailVo> loadBasic(@PathVariable("projectCode") String projectCode) throws Exception {
        ApiProjectDetailVo detailVo = apiProjectService.loadProjectVo(ProjectDetailQueryVo.builder()
                .projectCode(projectCode).build());
        if (detailVo == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        return SimpleResultUtils.createSimpleResult(detailVo);
    }

    @DeleteMapping("/{id}")
    public SimpleResult<Boolean> remove(@PathVariable("id") Integer id) {
        ApiProject project = apiProjectService.getById(id);
        if (project == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!SecurityUtils.validateUserUpdate(project.getUserName())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiProjectService.deleteApiProject(id));
    }

    @DeleteMapping("/removeByIds/{ids}")
    public SimpleResult<Boolean> removeByIds(@PathVariable("ids") List<Integer> ids) {
        return SimpleResultUtils.createSimpleResult(apiProjectService.deleteApiProjects(ids));
    }

    @PostMapping
    public SimpleResult save(@RequestBody ApiProject project) {
        ApiUser loginUser = getLoginUser();
        if (StringUtils.isBlank(project.getUserName()) && loginUser != null) {
            project.setUserName(loginUser.getUserName());
        }
        if (!SecurityUtils.validateUserUpdate(project.getUserName())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        project.setProjectCode(StringUtils.defaultIfBlank(project.getProjectCode(), SimpleModelUtils.uuid()));
        if (apiProjectService.existsApiProject(project)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_1001);
        }
        project.setPrivateFlag(ObjectUtils.defaultIfNull(project.getPrivateFlag(), true));
        return SimpleResultUtils.createSimpleResult(apiProjectService.saveProject(SimpleModelUtils.addAuditInfo(project)));
    }

    @PostMapping("/selectProjects")
    public SimpleResult<List<ApiProject>> selectProjects(@RequestBody ProjectQueryVo queryVo) {
        QueryWrapper<ApiProject> queryWrapper = Wrappers.<ApiProject>query();
        String userName = SecurityUtils.getUserName(queryVo.getUserName());
        queryWrapper.eq(ApiDocConstants.STATUS_KEY, ApiDocConstants.STATUS_ENABLED)
                .and(wrapper -> wrapper.and(wrapper1 -> wrapper1.eq("user_name", userName)));
        return SimpleResultUtils.createSimpleResult(apiProjectService.list(queryWrapper));
    }

    /**
     * 生成mock jwt token
     * @param jwtParam
     * @return
     */
    @PostMapping("/generateJwt")
    public SimpleResult<String> generateJwt(@RequestBody JwtParamVo jwtParam) {
        Algorithm algorithm = getAlgorithm(jwtParam.getAlgorithm(), jwtParam.getSecret());
        JWTCreator.Builder builder = JWT.create()
                .withPayload(jwtParam.getPayload());
        if (StringUtils.isNotBlank(jwtParam.getIssuer())) {
            builder.withIssuer(jwtParam.getIssuer());
        }
        if (jwtParam.getExpireTime() != null) {
            builder.withExpiresAt(jwtParam.getExpireTime());
        }
        String token = builder.sign(algorithm);
        return SimpleResultUtils.createSimpleResult(token);
    }

    protected Algorithm getAlgorithm(String algorithm, String secret) {
        Map<String, Function<String, Algorithm>> config = new HashMap<>();
        config.put("HS256", Algorithm::HMAC256);
        config.put("HS384", Algorithm::HMAC384);
        config.put("HS512", Algorithm::HMAC512);
        Function<String, Algorithm> func = config.get(algorithm);
        if (func != null) {
            return func.apply(secret);
        }
        return Algorithm.HMAC256(secret);
    }
}
