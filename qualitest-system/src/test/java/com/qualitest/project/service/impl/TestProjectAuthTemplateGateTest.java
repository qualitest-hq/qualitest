package com.qualitest.project.service.impl;

import com.qualitest.api.model.ApiImportUploadType;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.params.ApiImportParams;
import com.qualitest.api.service.impl.ApiImportServiceImpl;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.service.ITestProjectService;
import com.qualitest.project.support.ProjectAuthTemplateApplyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测谁：新建拒绝空鉴权；项目级上传空配置仍种 RuoYi Bearer。
 * 边界：无 templateIds、auth 空或 {}；uploadType=project 且项目配置空。
 * 单跑：{@code mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestProjectAuthTemplateGateTest}
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestProjectAuthTemplateGateTest {

    @Mock
    private TestProjectMapper testProjectMapper;
    @Mock
    private ProjectAuthTemplateApplyService applyService;
    @Mock
    private ITestProjectService testProjectService;

    /**
     * 前提：新建未勾模板且 auth 空 / {}。
     * 期望：ServiceException 含「鉴权模板」。
     */
    @Test
    @Order(1)
    @DisplayName("新建项目空配置拒绝")
    void insert_emptyAuthWithoutTemplates_rejects() {
        TestProjectServiceImpl service = new TestProjectServiceImpl();
        ReflectionTestUtils.setField(service, "testProjectMapper", testProjectMapper);
        ReflectionTestUtils.setField(service, "projectAuthTemplateApplyService", applyService);

        TestProject blank = new TestProject();
        blank.setProjectName("demo");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertTestProject(blank));
        assertTrue(ex.getMessage().contains("鉴权模板"));

        TestProject emptyJson = new TestProject();
        emptyJson.setProjectName("demo");
        emptyJson.setAuthConfig("{}");
        ex = assertThrows(ServiceException.class, () -> service.insertTestProject(emptyJson));
        assertTrue(ex.getMessage().contains("鉴权模板"));
    }

    /**
     * 前提：项目级上传，当前 auth_config 为空。
     * 期望：写入 RuoYi Bearer 并种子三口。
     */
    @Test
    @Order(2)
    @DisplayName("项目级上传空配置仍种 RuoYi Bearer")
    void projectUpload_emptyAuth_seedsDefaultBearer() {
        ApiImportServiceImpl importService = new ApiImportServiceImpl();
        ReflectionTestUtils.setField(importService, "testProjectService", testProjectService);
        ReflectionTestUtils.setField(importService, "projectAuthTemplateApplyService", applyService);

        TestProject project = new TestProject();
        project.setTestProjectId(9L);
        project.setAuthConfig("{}");
        when(testProjectService.selectTestProjectById(9L)).thenReturn(project);

        ApiImportParams params = new ApiImportParams();
        params.setUploadType(ApiImportUploadType.PROJECT);

        ProjectAuthConfig seeded = ReflectionTestUtils.invokeMethod(
                importService, "resolveProjectAuthForImport", 9L, params);

        assertEquals("RuoYi Bearer", seeded.getAuthProfiles().get(0).getName());
        assertEquals(3, seeded.getAuthProfiles().get(0).getApis().size());
        ArgumentCaptor<TestProject> update = ArgumentCaptor.forClass(TestProject.class);
        verify(testProjectService).updateTestProject(update.capture());
        assertTrue(update.getValue().getAuthConfig().contains("RuoYi Bearer"));
        verify(applyService).seedPrefabricatedApis(eq(9L), any(ProjectAuthConfig.class));
    }
}
