package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.web.vo.doc.ApiDocSearchResultVo;
import com.fugary.simple.api.web.vo.query.ApiDocSearchQueryVo;
import com.fugary.simple.api.web.vo.query.SimplePage;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.ParsedSql;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApiDocSearchServiceImplTest {
    private final ApiDocService docs = mock(ApiDocService.class);
    private final ApiProjectService projects = mock(ApiProjectService.class);
    private final ApiFolderService folders = mock(ApiFolderService.class);
    private final ApiProjectAccessService access = mock(ApiProjectAccessService.class);
    private final ApiDocSearchServiceImpl service = new ApiDocSearchServiceImpl();
    private NamedParameterJdbcTemplate jdbc;
    private JdbcTemplate sql;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        sql = new JdbcTemplate(source);
        jdbc = new NamedParameterJdbcTemplate(source);
        sql.execute("CREATE TABLE t_api_project (id INT, project_name VARCHAR, project_code VARCHAR, user_name VARCHAR, group_code VARCHAR, status INT DEFAULT 1)");
        sql.execute("CREATE TABLE t_api_doc (id INT, project_id INT, folder_id INT, doc_name VARCHAR, doc_type VARCHAR, "
                + "url VARCHAR, method VARCHAR, status INT, modify_from INT, modify_date TIMESTAMP, doc_content CLOB, description CLOB)");
        sql.execute("INSERT INTO t_api_project (id, project_name, project_code, user_name, group_code) VALUES (10, 'Mine', 'mine', 'alice', NULL), (20, 'Team', 'team', 'bob', 'readable'), "
                + "(30, 'Private', 'private', 'bob', NULL), (40, 'Revoked', 'revoked', 'alice', 'revoked')");
        addDoc(1, 10, "订单指南", "md", null, null, 1, null, "前言 ".repeat(100) + "签名 TOKEN 100% a_b", null);
        addDoc(2, 10, "创建订单", "api", "/v2/orders", "POST", 1, null, "SchemaOnly", "退款时需要签名 Token");
        addDoc(3, 20, "团队订单", "api", "/v1/orders", "GET", 1, null, "SchemaOnly", "签名 Token");
        addDoc(4, 30, "私有订单", "md", null, null, 1, null, "签名 Token", null);
        addDoc(5, 40, "失权订单", "md", null, null, 1, null, "签名 Token", null);
        addDoc(6, 10, "历史订单", "md", null, null, 1, 1, "签名 Token", null);
        addDoc(7, 10, "禁用订单", "md", null, null, 0, null, "签名 Token", null);
        ReflectionTestUtils.setField(service, "apiDocService", docs);
        ReflectionTestUtils.setField(service, "apiProjectService", projects);
        ReflectionTestUtils.setField(service, "apiFolderService", folders);
        ReflectionTestUtils.setField(service, "apiProjectAccessService", access);
        doCallRealMethod().when(access).addProjectRelatedGroupCodeQuery(any(), anyString(), anyString(), any(), any());
        when(access.loadReadableGroupCodesSql("alice")).thenReturn("readable");
        doAnswer(call -> queryPage("t_api_doc", call.getArgument(0), call.getArgument(1), ApiDoc.class))
                .when(docs).page(any(Page.class), any());
        doAnswer(call -> queryList("t_api_doc", call.getArgument(0), ApiDoc.class))
                .when(docs).list(any(Wrapper.class));
        doAnswer(call -> queryPage("t_api_project", call.getArgument(0), call.getArgument(1), ApiProject.class))
                .when(projects).page(any(Page.class), any());
        doAnswer(call -> queryList("t_api_project", call.getArgument(0), ApiProject.class))
                .when(projects).list(any(Wrapper.class));
        when(folders.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(folders.calcFolderNameMap(anyList())).thenAnswer(call ->
                new ApiFolderServiceImpl().calcFolderNameMap(call.getArgument(0)));
        loginAs("alice");
    }

    private void addDoc(int id, int project, String name, String type, String url, String method, int status,
                        Integer history, String body, String description) {
        sql.update("INSERT INTO t_api_doc VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?)",
                id, project, name, type, url, method, status, history, body, description);
    }

    private String segment(QueryWrapper<?> query) {
        return query.getCustomSqlSegment().replaceAll("#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}", ":$1");
    }

    private <T> List<T> queryList(String table, QueryWrapper<T> query, Class<T> type) {
        String suffix = segment(query);
        return jdbc.query("SELECT " + query.getSqlSelect() + " FROM " + table + " " + suffix,
                query.getParamNameValuePairs(), new BeanPropertyRowMapper<>(type));
    }

    private <T> Page<T> queryPage(String table, Page<T> page, QueryWrapper<T> query, Class<T> type) {
        String suffix = segment(query);
        // 使用真实分页插件生成 COUNT SQL，并按 JDBC 占位符绑定参数，不能手工剥离 ORDER BY。
        ParsedSql parsed = NamedParameterUtils.parseSqlStatement(
                "SELECT " + query.getSqlSelect() + " FROM " + table + " " + suffix);
        MapSqlParameterSource source = new MapSqlParameterSource(query.getParamNameValuePairs());
        String countSql = new PaginationInnerInterceptor().autoCountSql(page,
                NamedParameterUtils.substituteNamedParameters(parsed, source));
        page.setTotal(sql.queryForObject(countSql, Long.class,
                NamedParameterUtils.buildValueArray(parsed, source, null)));
        Map<String, Object> params = new HashMap<>(query.getParamNameValuePairs());
        params.put("limit", page.getSize());
        params.put("offset", (page.getCurrent() - 1) * page.getSize());
        page.setRecords(jdbc.query("SELECT " + query.getSqlSelect() + " FROM " + table + " " + suffix + " LIMIT :limit OFFSET :offset",
                params, new BeanPropertyRowMapper<>(type)));
        return page;
    }

    private void loginAs(String name) {
        ApiUserVo user = new ApiUserVo();
        user.setUserName(name);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiDocConstants.API_USER_KEY, user);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void cleanUp() {
        RequestContextHolder.resetRequestAttributes();
        sql.execute("DROP ALL OBJECTS");
    }

    @Test
    void filtersPermissionsBeforePaginationAndIgnoresClientUserName() {
        ApiDocSearchQueryVo query = new ApiDocSearchQueryVo();
        query.setUserName("admin");
        query.setPage(new SimplePage(1, 2, 0, 0));
        Page<ApiDocSearchResultVo> result = service.search(query, null);
        assertThat(result.getTotal()).isEqualTo(4);
        assertThat(result.getRecords()).extracting(ApiDocSearchResultVo::getId).containsExactly(7, 3);
        assertThat(service.searchProjects(query).getTotal()).isEqualTo(2);
        query.setProjectId(40);
        assertThat(service.search(query, null).getTotal()).isZero();
    }

    @Test
    void combinesIndependentConditionsAndOnlySearchesDocumentContent() {
        ApiDocSearchQueryVo query = new ApiDocSearchQueryVo();
        query.setDocName("订单");
        query.setUrl("/V2/");
        query.setContent("退款");
        Page<ApiDocSearchResultVo> result = service.search(query, null);
        assertThat(result.getRecords()).extracting(ApiDocSearchResultVo::getId).containsExactly(2);
        assertThat(result.getRecords().get(0).getSnippet()).contains("退款");
        query.setContent("SchemaOnly");
        assertThat(service.search(query, null).getRecords()).isEmpty();
        query.setContent("token");
        query.setDocType("md");
        assertThat(service.search(query, null).getRecords()).isEmpty();
    }

    @Test
    void treatsWildcardsLiterallyAndBoundsBodySnippets() {
        ApiDocSearchQueryVo query = new ApiDocSearchQueryVo();
        query.setContent("%");
        Page<ApiDocSearchResultVo> result = service.search(query, null);
        assertThat(result.getRecords()).extracting(ApiDocSearchResultVo::getId).containsExactly(1);
        assertThat(result.getRecords().get(0).getSnippet()).contains("100%").hasSizeLessThan(245);
        query.setContent("_");
        assertThat(service.search(query, null).getTotal()).isEqualTo(1);
        query.setContent("' OR 1=1 --");
        assertThat(service.search(query, null).getTotal()).isZero();
    }

    @Test
    void shareCannotBroadenProjectDocumentsStatusOrHistory() {
        ApiProjectShare share = new ApiProjectShare();
        share.setProjectId(10);
        share.setShareDocs("[2, 4, 6, 7]");
        ApiDocSearchQueryVo query = new ApiDocSearchQueryVo();
        query.setProjectId(30);
        query.setStatus(0);
        assertThat(service.search(query, share).getRecords()).extracting(ApiDocSearchResultVo::getId).containsExactly(2);
        share.setShareDocs("[]");
        assertThat(service.search(query, share).getRecords()).extracting(ApiDocSearchResultVo::getId).containsExactly(2, 1);
    }

    @Test
    void adminCanSearchAllProjectsButAnonymousCannot() {
        loginAs("admin");
        ApiDocSearchQueryVo query = new ApiDocSearchQueryVo();
        assertThat(service.search(query, null).getTotal()).isEqualTo(6);
        RequestContextHolder.resetRequestAttributes();
        assertThat(service.search(query, null).getTotal()).isZero();
    }

    @Test
    void shareCannotSearchDisabledProjects() {
        ApiProjectShare share = new ApiProjectShare();
        share.setProjectId(10);
        ApiDocSearchQueryVo query = new ApiDocSearchQueryVo();
        query.setContent("Token");
        assertThat(service.search(query, share).getTotal()).isEqualTo(2);
        sql.update("UPDATE t_api_project SET status = 0 WHERE id = 10");
        assertThat(service.search(query, share).getTotal()).isZero();
        query.setProjectId(10);
        assertThat(service.search(query, null).getTotal()).isEqualTo(3);
    }

    @Test
    void returnsProjectAndFolderLocationWithCycleProtection() {
        ApiFolder root = new ApiFolder();
        root.setId(100);
        root.setFolderName("根目录");
        ApiFolder child = new ApiFolder();
        child.setId(1);
        child.setParentId(100);
        child.setFolderName("订单接口");
        when(folders.list(any(Wrapper.class))).thenReturn(List.of(root, child));
        ApiDocSearchQueryVo query = new ApiDocSearchQueryVo();
        query.setProjectId(10);
        ApiDocSearchResultVo result = service.search(query, null).getRecords().get(0);
        assertThat(result.getProjectName()).isEqualTo("Mine");
        assertThat(result.getProjectCode()).isEqualTo("mine");
        assertThat(result.getFolderPath()).isEqualTo("根目录/订单接口");
        assertThat(result.getSnippet()).isNull();
        root.setParentId(1);
        assertThat(service.search(query, null).getRecords().get(0).getFolderPath()).isEqualTo("根目录/订单接口");
    }

    @Test
    void searchWithMultiKeywordsSplitByWhitespace() {
        ApiDocSearchQueryVo query = new ApiDocSearchQueryVo();
        query.setKeyword("指南 orders");
        assertThat(service.search(query, null).getRecords())
                .extracting(ApiDocSearchResultVo::getId)
                .containsExactly(1, 3, 2);
    }

    @Test
    void relevancePaginationKeepsTotalsAcrossPagesAndEmptyResults() {
        ApiDocSearchQueryVo query = new ApiDocSearchQueryVo();
        query.setKeyword("指南 orders");
        query.setPage(new SimplePage(1, 2, 0, 0));
        Page<ApiDocSearchResultVo> first = service.search(query, null);
        assertThat(first.getTotal()).isEqualTo(3);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.getRecords()).extracting(ApiDocSearchResultVo::getId).containsExactly(1, 3);

        query.setPage(new SimplePage(2, 2, 0, 0));
        Page<ApiDocSearchResultVo> second = service.search(query, null);
        assertThat(second.getTotal()).isEqualTo(3);
        assertThat(second.hasNext()).isFalse();
        assertThat(second.getRecords()).extracting(ApiDocSearchResultVo::getId).containsExactly(2);

        query.setKeyword("不存在的关键词");
        Page<ApiDocSearchResultVo> empty = service.search(query, null);
        assertThat(empty.getTotal()).isZero();
        assertThat(empty.getRecords()).isEmpty();
    }

    @Test
    void keywordSearchDoesNotInferSynonymsOrStripSuffixes() {
        addDoc(8, 10, "用户登陆", "api", "/session", "POST", 1, null, null, "认证");
        addDoc(9, 10, "用户登录接口", "api", "/loginapi", "POST", 1, null, null, "认证");
        addDoc(10, 10, "退出登录", "api", "/logout", "POST", 1, null, null, "认证");
        ApiDocSearchQueryVo query = new ApiDocSearchQueryVo();
        query.setKeyword("登录接口");
        assertThat(service.search(query, null).getRecords()).extracting(ApiDocSearchResultVo::getId)
                .containsExactly(9);
        query.setKeyword("注销");
        assertThat(service.search(query, null).getRecords()).isEmpty();
        query.setKeyword("/logoutapi");
        assertThat(service.search(query, null).getRecords()).isEmpty();
        query.setKeyword("  登陆\t/loginapi\n登陆  ");
        assertThat(service.search(query, null).getRecords()).extracting(ApiDocSearchResultVo::getId)
                .containsExactly(8, 9);
    }

    @Test
    void explicitKeywordsRespectProjectAndShareFilters() {
        addDoc(8, 10, "用户登陆", "api", "/session", "POST", 1, null, null, "认证");
        addDoc(9, 30, "私有登录", "api", "/login", "POST", 1, null, null, "认证");
        ApiDocSearchQueryVo query = new ApiDocSearchQueryVo();
        query.setKeyword("登陆 login");
        query.setProjectId(10);
        query.setMethod("POST");
        assertThat(service.search(query, null).getRecords()).extracting(ApiDocSearchResultVo::getId)
                .containsExactly(8);
        ApiProjectShare share = new ApiProjectShare();
        share.setProjectId(10);
        share.setShareDocs("[2]");
        assertThat(service.search(query, share).getRecords()).isEmpty();
    }

    @Test
    void ranksExactMatchesAndCoverageBeforePaginationAndModificationTime() {
        addDoc(8, 10, "退款", "api", "/refund", "POST", 1, null, null, "退款");
        sql.update("UPDATE t_api_doc SET modify_date = TIMESTAMP '2020-01-01 00:00:00' WHERE id = 8");
        for (int id = 9; id < 20; id++) {
            addDoc(id, 10, "其他说明" + id, "md", null, null, 1, null, "附带提及退款", null);
        }
        ApiDocSearchQueryVo query = new ApiDocSearchQueryVo();
        query.setKeyword("退款");
        query.setPage(new SimplePage(1, 8, 0, 0));
        assertThat(service.search(query, null).getRecords().get(0).getId()).isEqualTo(8);
        addDoc(20, 10, "订单退款", "api", "/orders/refund", "POST", 1, null, null, "支持撤销");
        query.setKeyword("订单 退款");
        assertThat(service.search(query, null).getRecords().get(0).getId()).isEqualTo(20);
    }

    @Test
    void allKeywordsCanMatchAcrossFieldsWithoutBroadeningPermissions() {
        ApiDocSearchQueryVo query = new ApiDocSearchQueryVo();
        query.setKeyword("订单 退款");
        query.setKeywordMatch("all");
        assertThat(service.search(query, null).getRecords()).extracting(ApiDocSearchResultVo::getId).containsExactly(2);
        query.setKeyword("订单 token");
        assertThat(service.search(query, null).getRecords()).extracting(ApiDocSearchResultVo::getId)
                .containsExactlyInAnyOrder(1, 2, 3, 7);
        ApiProjectShare share = new ApiProjectShare();
        share.setProjectId(10);
        share.setShareDocs("[1,2,7]");
        assertThat(service.search(query, share).getRecords()).extracting(ApiDocSearchResultVo::getId)
                .containsExactlyInAnyOrder(1, 2);
        query.setKeyword("' OR 1=1 --");
        assertThat(service.search(query, share).getTotal()).isZero();
    }

    @Test
    void duplicateCaseVariantsDoNotInflateRelevanceAndWildcardsRemainLiteral() {
        addDoc(8, 10, "Token", "api", "/auth", "POST", 1, null, null, "授权");
        ApiDocSearchQueryVo query = new ApiDocSearchQueryVo();
        query.setKeyword("token TOKEN");
        assertThat(service.search(query, null).getRecords().get(0).getId()).isEqualTo(8);
        query.setKeyword("100%");
        assertThat(service.search(query, null).getRecords()).extracting(ApiDocSearchResultVo::getId).containsExactly(1);
    }

    @Test
    void contentSnippetKeepsTheLiteralPhraseInsteadOfAnEarlierSynonym() {
        addDoc(8, 10, "认证说明", "md", null, null, 1, null,
                "login " + "前言 ".repeat(100) + "登录接口说明", null);
        ApiDocSearchQueryVo query = new ApiDocSearchQueryVo();
        query.setContent("登录接口说明");
        assertThat(service.search(query, null).getRecords().get(0).getSnippet()).contains("登录接口说明");
    }
}
