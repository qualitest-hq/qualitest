package com.qualitest.ai.mcp;

import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.enums.TestProjectMemberPerm;
import com.qualitest.project.service.ITestProjectMemberRoleService;
import com.qualitest.project.service.ITestProjectMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 按项目成员角色权限解析 MCP 门控。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpProjectGateResolver {

    /** 解析操作者在项目中的成员角色 */
    private final ITestProjectMemberService testProjectMemberService;

    /** 按 role_key 加载角色权限 */
    private final ITestProjectMemberRoleService testProjectMemberRoleService;

    /**
     * 按项目与操作者角色权限推导 MCP 能力。
     *
     * @param testProjectId  项目 id
     * @param operatorUserId 操作者用户 id；空则无能力
     * @return 门控快照
     */
    public McpProjectGates resolve(Long testProjectId, Long operatorUserId) {
        if (testProjectId == null || operatorUserId == null) {
            return McpProjectGates.NONE;
        }
        try {
            String roleKey = testProjectMemberService.getCheckProjectMemberRole(testProjectId, operatorUserId);
            Set<String> caps = testProjectMemberRoleService.listPermCodesByRoleKey(roleKey);
            boolean read = caps.contains(TestProjectMemberPerm.MCP_READ.getCode());
            boolean write = caps.contains(TestProjectMemberPerm.MCP_FLOW_WRITE.getCode());
            boolean run = caps.contains(TestProjectMemberPerm.MCP_RUN.getCode());
            boolean importApis = caps.contains(TestProjectMemberPerm.MCP_API_IMPORT.getCode());
            if (!read && !write && !run && !importApis) {
                return McpProjectGates.NONE;
            }
            return new McpProjectGates(read || write || run || importApis, write, run, importApis);
        } catch (ServiceException ex) {
            log.warn("MCP 解析角色权限失败 projectId={} userId={}: {}", testProjectId, operatorUserId, ex.getMessage());
            return McpProjectGates.NONE;
        }
    }
}
