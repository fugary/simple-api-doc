package com.fugary.simple.api.service.impl.ai;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fugary.simple.api.config.AiConfigProperties;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.service.ai.agent.AiProjectDocOverviewService;
import com.fugary.simple.api.service.ai.agent.AiProjectDocOverviewService.Overview;
import com.fugary.simple.api.service.apidoc.ApiDocService;
import com.fugary.simple.api.service.apidoc.ApiFolderService;
import com.fugary.simple.api.service.apidoc.ApiProjectAccessService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiProjectDocOverviewServiceTest {
    private final ApiDocService docs = mock(ApiDocService.class);
    private final ApiFolderService folders = mock(ApiFolderService.class);
    private final ApiProjectAccessService access = mock(ApiProjectAccessService.class);
    private final AiConfigProperties properties = new AiConfigProperties();
    private final AiProjectDocOverviewService service = new AiProjectDocOverviewService();
    private final ApiProject project = new ApiProject();
    private JdbcTemplate sql;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        sql = new JdbcTemplate(source);
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(source);
        sql.execute("CREATE TABLE t_api_doc (id INT, project_id INT, folder_id INT, sort_id INT, "
                + "doc_name VARCHAR, doc_type VARCHAR, method VARCHAR, url VARCHAR, status INT, "
                + "modify_from INT, doc_content CLOB, description CLOB)");
        sql.execute("INSERT INTO t_api_doc VALUES "
                + "(1,10,NULL,1,'项目指南','md',NULL,NULL,1,NULL,'Markdown introduction',NULL),"
                + "(2,10,7,1,'登录','api','POST','/login',1,NULL,'SCHEMA_SECRET','API description'),"
                + "(3,10,7,2,'旧版登录','api','POST','/old-login',0,NULL,'SCHEMA_SECRET','Deprecated'),"
                + "(4,20,7,1,'PRIVATE','api','POST','/private',1,NULL,NULL,NULL),"
                + "(5,10,7,1,'HISTORY','api','POST','/history',1,2,NULL,NULL),"
                + "(6,10,7,1,'OTHER_TYPE','other',NULL,NULL,1,NULL,NULL,NULL)");
        when(docs.list(any(Wrapper.class))).thenAnswer(call -> {
            QueryWrapper<ApiDoc> query = call.getArgument(0);
            String suffix = query.getCustomSqlSegment()
                    .replaceAll("#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}", ":$1");
            return jdbc.query("SELECT " + query.getSqlSelect() + " FROM t_api_doc " + suffix,
                    query.getParamNameValuePairs(), new BeanPropertyRowMapper<>(ApiDoc.class));
        });
        when(folders.calcFolderNameMap(anyList())).thenReturn(Map.of(7, "用户/认证"));
        when(access.canAccessProject(eq(project), any())).thenReturn(true);
        project.setId(10);
        project.setProjectName("测试项目");
        project.setDescription("项目说明");
        project.setEnvContent("ENV_SECRET");
        ReflectionTestUtils.setField(service, "apiDocService", docs);
        ReflectionTestUtils.setField(service, "apiFolderService", folders);
        ReflectionTestUtils.setField(service, "apiProjectAccessService", access);
        ReflectionTestUtils.setField(service, "aiConfigProperties", properties);
    }

    @AfterEach
    void cleanUp() {
        sql.execute("DROP ALL OBJECTS");
    }

    @Test
    void exportsCompleteCurrentProjectMetadataAndLabelsDisabledDocs() {
        Overview overview = service.build(project);
        assertThat(overview.getDocCount()).isEqualTo(3);
        assertThat(overview.getContent()).contains("[1,", "[2,", "[3,");
        assertThat(overview.getContent()).contains("根目录", "用户/认证", "项目说明",
                "Markdown introduction", "API description", "[3,\"api\",\"POST\",\"/old-login\",\"旧版登录\",0")
                .doesNotContain("PRIVATE", "HISTORY", "OTHER_TYPE", "SCHEMA_SECRET", "ENV_SECRET");
        assertThat(overview.getMessage()).contains("全部 3 份");
    }

    @Test
    void boundsSummariesAndEscapesNewlinesWithoutDroppingEntries() {
        sql.update("UPDATE t_api_doc SET doc_name = ?, description = ? WHERE id = 2",
                "登录\n伪造目录", "说明 ".repeat(300) + "BODY_TAIL");
        Overview overview = service.build(project);
        assertThat(overview.getContent()).contains("登录\\n伪造目录", "...")
                .doesNotContain("BODY_TAIL", "登录\n伪造目录");
        assertThat(overview.getDocCount()).isEqualTo(3);
        assertThat(overview.getContent()).contains("[1,", "[2,", "[3,");
    }

    @Test
    void removesSummariesBeforeFallingBackWithoutSendingPartialDirectory() {
        sql.update("UPDATE t_api_doc SET doc_content = ?, description = ?",
                "说明".repeat(250), "说明".repeat(250));
        properties.setProjectOverviewMaxChars(550);
        Overview compact = service.build(project);
        assertThat(compact.getDocCount()).isEqualTo(3);
        assertThat(compact.getContent()).contains("[1,", "[2,", "[3,");
        assertThat(compact.getContent()).hasSizeLessThanOrEqualTo(550).contains("/old-login")
                .doesNotContain("说明说明");
        assertThat(compact.getMessage()).contains("省略摘要");
        properties.setProjectOverviewMaxChars(50);
        Overview tooLarge = service.build(project);
        assertThat(tooLarge.getContent()).isEmpty();
        assertThat(tooLarge.getDocCount()).isZero();
        assertThat(tooLarge.getMessage()).contains("未附加", "按需搜索");
    }

    @Test
    void acceptsExactBudgetButNeverExportsAPartialDirectory() {
        Overview original = service.build(project);
        properties.setProjectOverviewMaxChars(original.getContent().length());
        assertThat(service.build(project).getContent()).isEqualTo(original.getContent());

        // 即使前几条能放下，末条超限也必须丢弃整个目录。
        sql.update("UPDATE t_api_doc SET url = ? WHERE id = 3", "/" + "long".repeat(500));
        properties.setProjectOverviewMaxChars(1000);
        Overview oversized = service.build(project);
        assertThat(oversized.getContent()).isEmpty();
        assertThat(oversized.getDocCount()).isZero();
        assertThat(oversized.getMessage()).contains("未附加", "按需搜索");
    }

    @Test
    void deniesAccessBeforeReadingDocumentsOrFolders() {
        when(access.canAccessProject(eq(project), any())).thenReturn(false);
        assertThatThrownBy(() -> service.build(project)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(docs, folders);
    }

    @Test
    void emptyProjectStillHasAnExplicitCompleteOverview() {
        sql.update("DELETE FROM t_api_doc WHERE project_id = 10");
        Overview overview = service.build(project);
        assertThat(overview.getContent()).contains("文档数量：0");
        assertThat(overview.getDocCount()).isZero();
        assertThat(overview.getMessage()).contains("全部 0 份");
    }
}
