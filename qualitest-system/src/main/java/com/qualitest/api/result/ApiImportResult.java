package com.qualitest.api.result;

import com.qualitest.flow.diagnose.ApiSyncImpactSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * API 批量导入的返回结果。
 * 含处理总数、新增/更新/跳过/失败计数，以及每条接口的处理明细。
 * 明细含路径、名称、状态、API id、简短说明；不含结构合并过程细节。
 * 本批若有接口被更新，还会带上 syncImpact：哪些测试流可能受影响。
 *
 * @author qualitest
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiImportResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 本批实际处理的接口条数 */
    @Builder.Default
    private Integer totalCount = 0;

    /** 成功条数（新增 + 更新） */
    @Builder.Default
    private Integer successCount = 0;

    /** 新增条数 */
    @Builder.Default
    private Integer insertCount = 0;

    /** 更新条数 */
    @Builder.Default
    private Integer updateCount = 0;

    /** 跳过条数 */
    @Builder.Default
    private Integer skipCount = 0;

    /** 失败条数 */
    @Builder.Default
    private Integer failCount = 0;

    /** 每条接口的处理明细 */
    @Builder.Default
    private List<ApiImportDetail> details = new ArrayList<>();

    /**
     * 本批更新成功的 API 对项目内测试流的影响汇总。
     * 仅在存在 update 成功项时尝试填充；扫描失败不影响导入本身成功。
     */
    private ApiSyncImpactSummary syncImpact;

    /**
     * 单条接口的导入处理结果。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiImportDetail implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** API 路径 */
        private String apiPath;

        /** API 名称 */
        private String apiName;

        /** 处理状态：insert / update / skip / fail */
        private String status;

        /** 库内 API 主键 */
        private Long testProjectApiId;

        /** 处理说明，例如「新增成功」「更新成功」或失败原因 */
        private String message;
    }

    /**
     * 追加一条明细，并累加 total/success/insert/update/skip/fail 计数。
     */
    public void addDetail(ApiImportDetail detail) {
        if (this.details == null) {
            this.details = new ArrayList<>();
        }
        this.details.add(detail);

        this.totalCount++;
        if ("insert".equals(detail.getStatus())) {
            this.insertCount++;
            this.successCount++;
        } else if ("update".equals(detail.getStatus())) {
            this.updateCount++;
            this.successCount++;
        } else if ("skip".equals(detail.getStatus())) {
            this.skipCount++;
        } else if ("fail".equals(detail.getStatus())) {
            this.failCount++;
        }
    }

}
