package com.qualitest.project.support;

import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.domain.TestProjectApiGroup;
import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.service.ITestProjectApiGroupService;
import com.qualitest.project.service.ITestProjectApiService;
import com.qualitest.project.service.ITestProjectService;
import com.qualitest.project.service.ITestProjectTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测谁：Apply 把模板拷进项目 Profile 并种子接口。
 * 边界：同名整份跳过、method+path 跨条去重、库内已有不覆盖、全字段拷贝、瘦 JSON 填缺省。
 * 单跑：{@code mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ProjectAuthTemplateApplyServiceTest}
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectAuthTemplateApplyServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long TPL_ADMIN = 2100000000000000004L;
    private static final Long TPL_DEFAULT = 2100000000000000001L;

    @Mock
    private ITestProjectTemplateService templateService;
    @Mock
    private TestProjectMapper testProjectMapper;
    @Mock
    private ITestProjectApiService testProjectApiService;
    @Mock
    private ITestProjectApiGroupService testProjectApiGroupService;
    @Mock
    private ITestProjectService testProjectService;

    private ProjectAuthTemplateApplyService service;

    @BeforeEach
    void setUp() {
        service = new ProjectAuthTemplateApplyService(
                templateService,
                testProjectMapper,
                testProjectApiService,
                testProjectApiGroupService,
                testProjectService);
    }

    /**
     * 前提：项目已有同名 Profile。
     * 期望：整份跳过；不种子接口。
     */
    @Test
    @Order(1)
    @DisplayName("同名 Profile 整份跳过")
    void apply_skipsSameNameProfile() {
        TestProject project = new TestProject();
        project.setTestProjectId(PROJECT_ID);
        project.setAuthConfig(ProjectAuthConfigSupport.toJson(
                ProjectAuthConfigSupport.ruoyiBearerTemplate()));
        when(testProjectMapper.selectTestProjectById(PROJECT_ID)).thenReturn(project);
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(
                template(TPL_DEFAULT, "RuoYi Bearer", loginAndCaptchaApis()));

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        ArgumentCaptor<TestProject> update = ArgumentCaptor.forClass(TestProject.class);
        verify(testProjectMapper).updateTestProject(update.capture());
        ProjectAuthConfig stored = ProjectAuthConfigSupport.parse(update.getValue().getAuthConfig());
        assertEquals(1, stored.getAuthProfiles().size());
        assertEquals("RuoYi Bearer", stored.getAuthProfiles().get(0).getName());
        verify(testProjectApiService, never()).batchInsertTestProjectApi(any());
    }

    /**
     * 前提：先勾管理端（POST /login），再勾 RuoYi Bearer（同 path 的登录 + captcha）。
     * 期望：两条 Profile；后勾登录口跳过；只种子 login + captcha。
     */
    @Test
    @Order(2)
    @DisplayName("method+path 跨条去重")
    void apply_dedupsMethodAndPathAcrossProfiles() {
        stubEmptyProject();
        stubGroupCreate();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());
        when(testProjectApiService.batchInsertTestProjectApi(any())).thenReturn(1);
        when(templateService.selectTestProjectTemplateById(TPL_ADMIN)).thenReturn(
                template(TPL_ADMIN, "管理端 Bearer", loginOnlyApis("adminToken")));
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(
                template(TPL_DEFAULT, "RuoYi Bearer", loginAndCaptchaApis()));

        service.apply(PROJECT_ID, List.of(TPL_ADMIN, TPL_DEFAULT));

        ArgumentCaptor<TestProject> update = ArgumentCaptor.forClass(TestProject.class);
        verify(testProjectMapper).updateTestProject(update.capture());
        ProjectAuthConfig stored = ProjectAuthConfigSupport.parse(update.getValue().getAuthConfig());
        assertEquals(2, stored.getAuthProfiles().size());
        assertEquals("管理端 Bearer", stored.getAuthProfiles().get(0).getName());
        assertEquals(1, stored.getAuthProfiles().get(0).getApis().size());
        assertEquals("/login", stored.getAuthProfiles().get(0).getApis().get(0).getApiPath());
        assertEquals(1, stored.getAuthProfiles().get(1).getApis().size());
        assertEquals("/captchaImage", stored.getAuthProfiles().get(1).getApis().get(0).getApiPath());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TestProjectApi>> inserts = ArgumentCaptor.forClass(List.class);
        verify(testProjectApiService).batchInsertTestProjectApi(inserts.capture());
        Set<String> paths = inserts.getValue().stream()
                .map(TestProjectApi::getApiPath)
                .collect(Collectors.toSet());
        assertEquals(Set.of("/login", "/captchaImage"), paths);
    }

    /**
     * 前提：库内已有 POST /login。
     * 期望：Profile 仍写入；不 insert 已有接口。
     */
    @Test
    @Order(3)
    @DisplayName("库内已有 method+path 不覆盖")
    void apply_skipsExistingDbApi() {
        stubEmptyProject();
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(
                template(TPL_DEFAULT, "RuoYi Bearer", loginOnlyApis("token")));
        TestProjectApi existing = TestProjectApi.builder()
                .apiPath("/login")
                .requestConfig("{\"method\":\"POST\"}")
                .delStatus(0)
                .build();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of(existing));

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        ArgumentCaptor<TestProject> update = ArgumentCaptor.forClass(TestProject.class);
        verify(testProjectMapper).updateTestProject(update.capture());
        assertEquals(1, ProjectAuthConfigSupport.parse(update.getValue().getAuthConfig())
                .getAuthProfiles().size());
        verify(testProjectApiService, never()).batchInsertTestProjectApi(any());
    }

    /**
     * 前提：模板预制口带描述、头、响应、测值、脚本等同名字段。
     * 期望：insert 行这些列与模板一致，而不是写死空 JSON。
     */
    @Test
    @Order(4)
    @DisplayName("全字段预制口按同名字段 insert")
    void apply_copiesFullPrefabFields() {
        stubEmptyProject();
        stubGroupCreate();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());
        when(testProjectApiService.batchInsertTestProjectApi(any())).thenReturn(1);
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(
                template(TPL_DEFAULT, "RuoYi Bearer", fullLoginPrefabApis()));

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TestProjectApi>> inserts = ArgumentCaptor.forClass(List.class);
        verify(testProjectApiService).batchInsertTestProjectApi(inserts.capture());
        assertEquals(1, inserts.getValue().size());
        TestProjectApi row = inserts.getValue().get(0);
        assertEquals("登录", row.getApiName());
        assertEquals("/login", row.getApiPath());
        assertEquals("管理端登录", row.getApiDescription());
        assertTrue(row.getHeaders().contains("X-Client"));
        assertTrue(row.getCookies().contains("sid"));
        assertTrue(row.getResponseConfig().contains("\"ok\""));
        assertTrue(row.getTestValueConfig().contains("admin"));
        assertTrue(row.getBizCodeConfig().contains("200"));
        assertEquals("pre()", row.getPreRequestScript());
        assertEquals("post()", row.getPostRequestScript());
        assertTrue(row.getDesignHints().contains("$.token"));
        assertTrue(row.getAuthConfig().contains("\"none\""));
        assertFalse(row.getAuthConfig().contains("loginHint"));
    }

    /**
     * 前提：内置瘦 JSON（无 headers/response/描述/脚本）。
     * 期望：headers/cookies 为 {}，responseConfig 为空 responses，可选列为空。
     */
    @Test
    @Order(5)
    @DisplayName("瘦 JSON 种子填缺省列")
    void apply_fillsDefaultJsonColumnsForSlimPrefab() {
        stubEmptyProject();
        stubGroupCreate();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());
        when(testProjectApiService.batchInsertTestProjectApi(any())).thenReturn(1);
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(
                template(TPL_DEFAULT, "RuoYi Bearer", slimLoginApis()));

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TestProjectApi>> inserts = ArgumentCaptor.forClass(List.class);
        verify(testProjectApiService).batchInsertTestProjectApi(inserts.capture());
        TestProjectApi row = inserts.getValue().get(0);
        assertEquals("{}", row.getHeaders());
        assertEquals("{}", row.getCookies());
        assertEquals("{\"configVersion\":1,\"responses\":[]}", row.getResponseConfig());
        assertNull(row.getApiDescription());
        assertNull(row.getTestValueConfig());
        assertNull(row.getBizCodeConfig());
        assertNull(row.getPreRequestScript());
        assertNull(row.getPostRequestScript());
    }

    /**
     * 前提：模板登录口带 loginHint。
     * 期望：接口行只写 mode=none；项目 Profile 上有 loginHint 与 credentialApi。
     */
    @Test
    @Order(6)
    @DisplayName("种子：hint 在 Profile，接口行只有 none")
    void apply_seedsLoginModeNoneHintOnProfile() {
        stubEmptyProject();
        stubGroupCreate();
        when(testProjectApiService.selectTestProjectApiList(any())).thenReturn(List.of());
        when(testProjectApiService.batchInsertTestProjectApi(any())).thenReturn(1);
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(
                template(TPL_DEFAULT, "RuoYi Bearer", loginOnlyApis("adminToken")));

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        ArgumentCaptor<TestProject> update = ArgumentCaptor.forClass(TestProject.class);
        verify(testProjectMapper).updateTestProject(update.capture());
        ProjectAuthConfig stored = ProjectAuthConfigSupport.parse(update.getValue().getAuthConfig());
        assertEquals("adminToken", stored.getAuthProfiles().get(0).getLoginHint().getFlowKey());
        assertEquals("/login", stored.getAuthProfiles().get(0).getCredentialApi().getPath());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TestProjectApi>> inserts = ArgumentCaptor.forClass(List.class);
        verify(testProjectApiService).batchInsertTestProjectApi(inserts.capture());
        String authJson = inserts.getValue().get(0).getAuthConfig();
        assertTrue(authJson.contains("\"none\""));
        assertFalse(authJson.contains("adminToken"));
        assertFalse(authJson.contains("loginHint"));
        assertTrue(inserts.getValue().get(0).getTestValueConfig().contains("admin"));
        assertTrue(inserts.getValue().get(0).getResponseConfig().contains("token"));
    }

    private void stubEmptyProject() {
        TestProject project = new TestProject();
        project.setTestProjectId(PROJECT_ID);
        project.setAuthConfig("{}");
        when(testProjectMapper.selectTestProjectById(PROJECT_ID)).thenReturn(project);
    }

    private void stubGroupCreate() {
        when(testProjectApiGroupService.selectTestProjectApiGroupList(any())).thenReturn(List.of());
        when(testProjectApiGroupService.insertTestProjectApiGroup(any())).thenAnswer(invocation -> {
            TestProjectApiGroup group = invocation.getArgument(0);
            group.setApiGroupId(100L);
            return 1;
        });
    }

    private static TestProjectTemplate template(Long id, String name, String apis) {
        return TestProjectTemplate.builder()
                .testProjectTemplateId(id)
                .templateName(name)
                .headerName("Authorization")
                .headerValueTemplate("Bearer {{flow.token}}")
                .apis(apis)
                .enableStatus(1)
                .delStatus(0)
                .build();
    }

    private static String slimLoginApis() {
        return "[{\"apiName\":\"登录\",\"apiPath\":\"/login\",\"apiGroup\":\"登录\","
                + "\"protocolType\":\"http\",\"apiStatus\":\"normal\","
                + "\"requestConfig\":{\"configVersion\":1,\"method\":\"POST\"},"
                + "\"authConfig\":{\"mode\":\"none\"}}]";
    }

    private static String loginOnlyApis(String flowKey) {
        return "[{\"apiName\":\"登录\",\"apiPath\":\"/login\",\"apiGroup\":\"登录\","
                + "\"protocolType\":\"http\",\"apiStatus\":\"normal\","
                + "\"requestConfig\":{\"configVersion\":1,\"method\":\"POST\"},"
                + "\"testValueConfig\":{\"request\":{\"bodyExample\":{\"username\":\"admin\",\"password\":\"admin123\"}}},"
                + "\"responseConfig\":{\"configVersion\":1,\"responses\":[{\"httpStatus\":200,\"example\":{\"code\":200,\"token\":\"...\"}}]},"
                + "\"authConfig\":{\"mode\":\"none\",\"loginHint\":{\"flowKey\":\""
                + flowKey + "\",\"from\":\"body\",\"expr\":\"$.token\"}}}]";
    }

    private static String loginAndCaptchaApis() {
        return "[{\"apiName\":\"登录\",\"apiPath\":\"/login\",\"apiGroup\":\"登录\","
                + "\"protocolType\":\"http\",\"apiStatus\":\"normal\","
                + "\"requestConfig\":{\"configVersion\":1,\"method\":\"POST\"},"
                + "\"authConfig\":{\"mode\":\"none\",\"loginHint\":{\"flowKey\":\"token\","
                + "\"from\":\"body\",\"expr\":\"$.token\"}}},"
                + "{\"apiName\":\"验证码\",\"apiPath\":\"/captchaImage\",\"apiGroup\":\"登录\","
                + "\"protocolType\":\"http\",\"apiStatus\":\"normal\","
                + "\"requestConfig\":{\"configVersion\":1,\"method\":\"GET\"},"
                + "\"authConfig\":{\"mode\":\"none\"}}]";
    }

    private static String fullLoginPrefabApis() {
        return """
                [{"apiName":"登录","apiPath":"/login","apiGroup":"系统.登录",
                  "protocolType":"http","apiStatus":"normal",
                  "apiDescription":"管理端登录",
                  "requestConfig":{"configVersion":1,"method":"POST"},
                  "headers":{"X-Client":"qualitest"},
                  "cookies":{"sid":"1"},
                  "responseConfig":{"configVersion":1,"responses":[{"id":"ok"}]},
                  "testValueConfig":{"bodyExample":{"username":"admin"}},
                  "bizCodeConfig":{"successValues":[200]},
                  "authConfig":{"mode":"none","loginHint":{"flowKey":"token","from":"body","expr":"$.token"}},
                  "designHints":{"hints":["token 在 $.token"]},
                  "preRequestScript":"pre()","postRequestScript":"post()"}]
                """;
    }
}
