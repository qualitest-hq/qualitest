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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测谁：Apply 把模板拷进项目 Profile 并种子接口。
 * 边界：同名整份跳过、method+path 跨条去重、库内已有接口不覆盖。
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
                ProjectAuthConfigSupport.defaultBearerTemplate()));
        when(testProjectMapper.selectTestProjectById(PROJECT_ID)).thenReturn(project);
        when(templateService.selectTestProjectTemplateById(TPL_DEFAULT)).thenReturn(
                template(TPL_DEFAULT, "默认 Bearer", loginAndCaptchaApis()));

        service.apply(PROJECT_ID, List.of(TPL_DEFAULT));

        ArgumentCaptor<TestProject> update = ArgumentCaptor.forClass(TestProject.class);
        verify(testProjectMapper).updateTestProject(update.capture());
        ProjectAuthConfig stored = ProjectAuthConfigSupport.parse(update.getValue().getAuthConfig());
        assertEquals(1, stored.getAuthProfiles().size());
        assertEquals("默认 Bearer", stored.getAuthProfiles().get(0).getName());
        verify(testProjectApiService, never()).batchInsertTestProjectApi(any());
    }

    /**
     * 前提：先勾管理端（POST /login），再勾默认 Bearer（同 path 的登录 + captcha）。
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
                template(TPL_DEFAULT, "默认 Bearer", loginAndCaptchaApis()));

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
                template(TPL_DEFAULT, "默认 Bearer", loginOnlyApis("token")));
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

    private static String loginOnlyApis(String flowKey) {
        return "[{\"apiName\":\"登录\",\"apiPath\":\"/login\",\"apiGroup\":\"登录\","
                + "\"protocolType\":\"http\",\"apiStatus\":\"normal\","
                + "\"requestConfig\":{\"configVersion\":1,\"method\":\"POST\"},"
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
}
