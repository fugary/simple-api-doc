package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.entity.api.AiCache;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.mapper.api.AiCacheMapper;
import com.fugary.simple.api.service.apidoc.ApiDocService;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
import com.fugary.simple.api.service.apidoc.ApiProjectAccessService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiProjectShareService;
import com.fugary.simple.api.service.apidoc.ApiProjectTaskService;
import com.fugary.simple.api.service.apidoc.ApiUserService;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.dashboard.DashboardMetricsVo;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.web.vo.project.AdminProjectShareVo;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dashboard Controller
 *
 * @author gary.fu
 */
@RestController
@RequestMapping("/admin/dashboard")
public class DashboardController {

    @Autowired
    private ApiProjectService apiProjectService;

    @Autowired
    private ApiProjectAccessService apiProjectAccessService;

    @Autowired
    private ApiDocService apiDocService;

    @Autowired
    private ApiUserService apiUserService;

    @Autowired
    private ApiGroupService apiGroupService;

    @Autowired
    private ApiProjectShareService apiProjectShareService;

    @Autowired
    private ApiProjectTaskService apiProjectTaskService;

    @Autowired
    private AiCacheMapper aiCacheMapper;

    /**
     * 判断是否为查询全部数据的权限条件
     */
    private boolean shouldQueryAll(Boolean all) {
        return Boolean.TRUE.equals(all);
    }

    private String getQueryUser(Boolean all) {
        if (shouldQueryAll(all)) {
            return null;
        }
        return SecurityUtils.getLoginUserName();
    }

    @GetMapping("/metrics")
    public SimpleResult<DashboardMetricsVo> metrics(@RequestParam(value = "all", defaultValue = "false") Boolean all) {
        String userName = getQueryUser(all);
        DashboardMetricsVo vo = new DashboardMetricsVo();

        QueryWrapper<ApiProject> projectQuery = Wrappers.<ApiProject>query();
        if (!shouldQueryAll(all)) {
            apiProjectAccessService.addProjectGroupCodeQuery(projectQuery, null, SecurityUtils.getLoginUserName());
        }
        vo.setProjectCount(Math.toIntExact(apiProjectService.count(projectQuery)));

        List<Map<String, Object>> docCounts = apiDocService.listMaps(Wrappers.<ApiDoc>query()
                .select("doc_type", "count(1) as countValue")
                .isNull(ApiDocConstants.DB_MODIFY_FROM_KEY)
                .eq(StringUtils.isNotBlank(userName), "creator", userName)
                .groupBy("doc_type"));
        vo.setApiCount(0);
        vo.setDocCount(0);
        docCounts.forEach(map -> {
            String docType = (String) map.get("doc_type");
            Integer count = ((Number) map.get("countValue")).intValue();
            if (ApiDocConstants.DOC_TYPE_API.equals(docType)) {
                vo.setApiCount(count);
            } else if (ApiDocConstants.DOC_TYPE_MD.equals(docType)) {
                vo.setDocCount(count);
            }
        });

        QueryWrapper<ApiProjectShare> shareQuery = Wrappers.<ApiProjectShare>query();
        if (!shouldQueryAll(all)) {
            apiProjectAccessService.addProjectRelatedGroupCodeQuery(shareQuery, "t_api_project_share", "project_id", null, SecurityUtils.getLoginUserName());
        }
        vo.setShareCount(Math.toIntExact(apiProjectShareService.count(shareQuery)));

        if (shouldQueryAll(all)) {
            vo.setUserCount(Math.toIntExact(apiUserService.count()));
            vo.setGroupCount(Math.toIntExact(apiGroupService.count()));
        } else {
            vo.setUserCount(0);
            vo.setGroupCount(0);
        }

        vo.setAiCacheCount(Math.toIntExact(aiCacheMapper.selectCount(Wrappers.<AiCache>query()
                .eq(StringUtils.isNotBlank(userName), "user_name", userName))));

        vo.setTaskCount(Math.toIntExact(apiProjectTaskService.count(Wrappers.<ApiProjectTask>query()
                .eq(StringUtils.isNotBlank(userName), "creator", userName))));

        return SimpleResultUtils.createSimpleResult(vo);
    }

    @GetMapping("/trend")
    public SimpleResult<Map<String, Object>> trend(@RequestParam(value = "all", defaultValue = "false") Boolean all,
                                                   @RequestParam(value = "days", defaultValue = "30") Integer days) {
        String userName = getQueryUser(all);
        Date now = new Date();
        Date startDate = DateUtils.addDays(now, -days);

        // 获取日期范围内的数据以规避 H2 不支持 DATE_FORMAT 的问题
        List<ApiDoc> docs = apiDocService.list(Wrappers.<ApiDoc>query()
                .select("create_date")
                .isNull(ApiDocConstants.DB_MODIFY_FROM_KEY)
                .eq(StringUtils.isNotBlank(userName), "creator", userName)
                .ge("create_date", startDate));

        List<ApiProject> projects = apiProjectService.list(Wrappers.<ApiProject>query()
                .select("create_date")
                .eq(StringUtils.isNotBlank(userName), "user_name", userName)
                .ge("create_date", startDate));

        // 内存中按日期分组
        Map<String, Long> docsMap = docs.stream().filter(d -> d.getCreateDate() != null)
                .collect(Collectors.groupingBy(d -> DateFormatUtils.format(d.getCreateDate(), "yyyy-MM-dd"), Collectors.counting()));
        
        Map<String, Long> projectsMap = projects.stream().filter(p -> p.getCreateDate() != null)
                .collect(Collectors.groupingBy(p -> DateFormatUtils.format(p.getCreateDate(), "yyyy-MM-dd"), Collectors.counting()));

        Map<String, Object> result = new HashMap<>();
        List<String> dates = new ArrayList<>();
        List<Integer> docCounts = new ArrayList<>();
        List<Integer> projectCounts = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            String dateStr = DateFormatUtils.format(DateUtils.addDays(now, -i), "yyyy-MM-dd");
            dates.add(dateStr);
            docCounts.add(docsMap.getOrDefault(dateStr, 0L).intValue());
            projectCounts.add(projectsMap.getOrDefault(dateStr, 0L).intValue());
        }

        result.put("dates", dates);
        result.put("docs", docCounts);
        result.put("projects", projectCounts);

        return SimpleResultUtils.createSimpleResult(result);
    }

    @GetMapping("/recentProjects")
    public SimpleResult<List<ApiProject>> recentProjects(@RequestParam(value = "all", defaultValue = "false") Boolean all) {
        Page<ApiProject> page = new Page<>(1, 10);
        QueryWrapper<ApiProject> query = Wrappers.<ApiProject>query()
                .orderByDesc("modify_date");

        if (!shouldQueryAll(all)) {
            apiProjectAccessService.addProjectGroupCodeQuery(query, null, SecurityUtils.getLoginUserName());
        }

        apiProjectService.page(page, query);
        return SimpleResultUtils.createSimpleResult(page.getRecords());
    }

    @GetMapping("/recentShares")
    public SimpleResult<List<AdminProjectShareVo>> recentShares(@RequestParam(value = "all", defaultValue = "false") Boolean all) {
        String userName = getQueryUser(all);
        Page<ApiProjectShare> page = new Page<>(1, 10);
        QueryWrapper<ApiProjectShare> query = Wrappers.<ApiProjectShare>query()
                .orderByDesc("create_date");
        
        if (!shouldQueryAll(all)) {
            apiProjectAccessService.addProjectRelatedGroupCodeQuery(query, "t_api_project_share", "project_id", null, SecurityUtils.getLoginUserName());
        }

        Page<ApiProjectShare> pageResult = apiProjectShareService.page(page, query);
        List<AdminProjectShareVo> shareList = new ArrayList<>();
        if (!pageResult.getRecords().isEmpty()) {
            Map<Integer, ApiProject> projectMap = apiProjectService.list(Wrappers.<ApiProject>query().in("id",
                            pageResult.getRecords().stream().map(ApiProjectShare::getProjectId).collect(Collectors.toList())))
                    .stream().collect(Collectors.toMap(ApiProject::getId, Function.identity()));
            shareList = pageResult.getRecords().stream().map(share -> {
                AdminProjectShareVo shareVo = com.fugary.simple.api.utils.SimpleModelUtils.copy(share, AdminProjectShareVo.class);
                shareVo.setProject(projectMap.get(shareVo.getProjectId()));
                return shareVo;
            }).collect(Collectors.toList());
        }
        return SimpleResultUtils.createSimpleResult(shareList);
    }

    @GetMapping("/recentImports")
    public SimpleResult<List<ApiProjectTask>> recentImports(@RequestParam(value = "all", defaultValue = "false") Boolean all) {
        String userName = getQueryUser(all);
        Page<ApiProjectTask> page = new Page<>(1, 10);
        QueryWrapper<ApiProjectTask> query = Wrappers.<ApiProjectTask>query()
                .eq(StringUtils.isNotBlank(userName), "creator", userName)
                .orderByDesc("modify_date");
        apiProjectTaskService.page(page, query);
        return SimpleResultUtils.createSimpleResult(page.getRecords());
    }
}
