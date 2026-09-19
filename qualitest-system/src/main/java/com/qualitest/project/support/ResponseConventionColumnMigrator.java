package com.qualitest.project.support;

import com.qualitest.api.util.ProjectAuthConfigSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 启动时一次性迁移：把旧表列 test_project.response_convention 灌入各多端 Profile，再删除该列。
 * <p>
 * 对每个项目：读出旧约定与 auth_config，给尚无响应约定的 Profile 写入旧约定（或代码缺省），
 * 写回 auth_config；全部处理完后 DROP response_convention 列。
 * 列已不存在时直接跳过，可重复启动。
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class ResponseConventionColumnMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!columnExists("test_project", "response_convention")) {
            return;
        }
        log.info("开始迁移 test_project.response_convention → auth_config.authProfiles[].responseConvention");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT test_project_id, response_convention, auth_config FROM test_project");
        int updated = 0;
        for (Map<String, Object> row : rows) {
            Object id = row.get("test_project_id");
            String convention = row.get("response_convention") != null
                    ? String.valueOf(row.get("response_convention"))
                    : null;
            String auth = row.get("auth_config") != null ? String.valueOf(row.get("auth_config")) : null;
            String merged = ProjectAuthConfigSupport.mergeProjectConventionIntoProfiles(auth, convention);
            if (merged != null && !merged.equals(auth)) {
                jdbcTemplate.update(
                        "UPDATE test_project SET auth_config = ? WHERE test_project_id = ?",
                        merged, id);
                updated++;
            } else if (auth == null || auth.isBlank()) {
                // 无 Profile 时仍写出规范化空配置，避免留脏数据
                jdbcTemplate.update(
                        "UPDATE test_project SET auth_config = ? WHERE test_project_id = ?",
                        merged != null ? merged : "{}", id);
            }
        }
        jdbcTemplate.execute("ALTER TABLE test_project DROP COLUMN response_convention");
        log.info("response_convention 迁移完成：更新 {} 个项目并已 DROP 列", updated);
    }

    /** 判断当前库中指定表是否仍存在某列。 */
    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        return count != null && count > 0;
    }
}
