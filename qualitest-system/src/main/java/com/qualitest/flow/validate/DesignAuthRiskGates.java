package com.qualitest.flow.validate;

import com.qualitest.flow.model.GraphJson;
import com.qualitest.project.domain.TestProjectApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 鉴权相关与 HTTP 必填风险检查（不含「图上是否已写出鉴权头路径」硬拦）。
 * <p>
 * 依次检查：登录口 flow 键碰撞、成功路径 HTTP 是否缺必填测值。
 * 返回带 CODE 前缀的错误文案列表；空列表表示通过。
 */
public final class DesignAuthRiskGates {

    private DesignAuthRiskGates() {
    }

    /**
     * 汇总鉴权碰撞与必填错误文案。
     *
     * @param graph           待检查的图；null 则返回空列表
     * @param projectAuthJson 项目鉴权配置 JSON；null 时部分检查会跳过
     * @param apiResolver     按接口 id 查项目接口；null 当作查不到
     * @param subflowResolver 按子流 id 加载子流图；保留参数以兼容调用方，本门不再展开认票
     * @return 错误文案列表
     */
    public static List<String> collect(
            GraphJson graph,
            String projectAuthJson,
            Function<Long, TestProjectApi> apiResolver,
            Function<Long, GraphJson> subflowResolver
    ) {
        List<String> errors = new ArrayList<>();
        if (graph == null) {
            return errors;
        }
        Function<Long, TestProjectApi> apis = apiResolver != null ? apiResolver : id -> null;
        errors.addAll(LoginFlowKeyCollisionGate.validate(graph, projectAuthJson, apis));
        errors.addAll(HttpRequiredParamGate.validate(graph, apis));
        return errors;
    }

    /**
     * 仅登录键碰撞等鉴权类文案（不含 HTTP 必填）。
     */
    public static List<String> collectAuthOnly(
            GraphJson graph,
            String projectAuthJson,
            Function<Long, TestProjectApi> apiResolver) {
        List<String> errors = new ArrayList<>();
        if (graph == null) {
            return errors;
        }
        Function<Long, TestProjectApi> apis = apiResolver != null ? apiResolver : id -> null;
        errors.addAll(LoginFlowKeyCollisionGate.validate(graph, projectAuthJson, apis));
        return errors;
    }

    /**
     * 仅 HTTP 必填测值类文案。
     */
    public static List<String> collectHttpRequiredOnly(
            GraphJson graph,
            Function<Long, TestProjectApi> apiResolver) {
        if (graph == null) {
            return List.of();
        }
        Function<Long, TestProjectApi> apis = apiResolver != null ? apiResolver : id -> null;
        return HttpRequiredParamGate.validate(graph, apis);
    }
}
