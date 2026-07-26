package com.qualitest.flow.run;

import com.qualitest.flow.context.ResolvedRunScenario;
import com.qualitest.project.domain.TestProjectEnv;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 单次 Run 启动时携带的元数据。
 * <p>
 * scenario、envName 用于写入 step_index=0 的场景加载步；
 * env 完整对象供 checkpoint 读取 envUrl、allowDestructiveReset 等。
 */
@Getter
@AllArgsConstructor
public class RunBootstrapMeta {

    /** 本次 Run 绑定的运行场景（环境 id、flow 初值、失败策略等） */
    private final ResolvedRunScenario scenario;

    /** 环境展示名，写入 runConfig 步 */
    private final String envName;

    /** 测试项目环境实体；checkpoint 需要 envUrl 与还原开关 */
    private final TestProjectEnv env;
}
