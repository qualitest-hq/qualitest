package com.qualitest.project.params;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 从已有测试项目另存为鉴权项目模板的请求体。
 * 测试流为主轴：流内绑定的接口与引用素材由服务端强制打包；其余可通过 extra* / promptIds 追加。
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SaveAsAuthTemplateParams {

    /** 新模板名称；必填。 */
    private String templateName;

    /** 要导出的 Auth Profile id。 */
    private String profileId;

    /** 要带走的测试流主键；可空（会 warning）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private List<Long> flowIds;

    /** 可追加接口主键（不含流已关联的亦可；服务端会与锁定集合并）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private List<Long> extraApiIds;

    /** 可追加素材 key（流引用的 key 服务端强制并入）。 */
    private List<String> extraAssetKeys;

    /** 是否包含项目首环境；缺省 true。 */
    private Boolean includeEnv;

    /** 可追加的项目级 AI 提示词主键。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private List<Long> promptIds;

    /** true 时同名自定义模板覆盖更新。 */
    private Boolean overwriteByName;

    /** true 时只预览不写库。 */
    private Boolean dryRun;
}
