package com.qualitest.web.controller.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.ai.domain.AiChatSession;
import com.qualitest.ai.params.AiChatSessionParams;
import com.qualitest.ai.result.AiChatSessionResult;
import com.qualitest.ai.service.IAiChatSessionService;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.utils.poi.ExcelUtil;
import com.qualitest.common.core.page.TableDataInfo;

/**
 * AI 会话Controller
 * 
 * @author qualitest
 * @date 2026-06-15
 */
@RestController
@RequestMapping("/ai/aiChatSession")
@AllArgsConstructor
public class AiChatSessionController extends BaseController {

    private final IAiChatSessionService aiChatSessionService;

    /**
     * 查询AI 会话列表
     */
    @PreAuthorize("@ss.hasPermi('ai:aiChatSession:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiChatSessionParams params) {
        startPage();
        List<AiChatSessionResult> list = aiChatSessionService.selectAiChatSessionResultList(params);
        return getDataTable(list);
    }

    /**
     * 导出AI 会话列表
     */
    @PreAuthorize("@ss.hasPermi('ai:aiChatSession:export')")
    @Log(title = "AI 会话", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiChatSessionParams params) {
        List<AiChatSessionResult> list = aiChatSessionService.selectAiChatSessionResultList(params);
        ExcelUtil<AiChatSessionResult> util = new ExcelUtil<>(AiChatSessionResult.class);
        util.exportExcel(response, list, "AI 会话数据");
    }

    /**
     * 获取AI 会话详细信息
     */
    @PreAuthorize("@ss.hasPermi('ai:aiChatSession:query')")
    @GetMapping(value = "/{aiChatSessionId}")
    public R<AiChatSessionResult> getInfo(@PathVariable("aiChatSessionId") Long aiChatSessionId) {
        return ok(aiChatSessionService.selectAiChatSessionResult(aiChatSessionId));
    }

    /**
     * 新增AI 会话
     */
    @PreAuthorize("@ss.hasPermi('ai:aiChatSession:add')")
    @Log(title = "AI 会话", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody AiChatSession aiChatSession) {
        return toR(aiChatSessionService.insertAiChatSession(aiChatSession));
    }

    /**
     * 修改AI 会话
     */
    @PreAuthorize("@ss.hasPermi('ai:aiChatSession:edit')")
    @Log(title = "AI 会话", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody AiChatSession aiChatSession) {
        return toR(aiChatSessionService.updateAiChatSession(aiChatSession));
    }

    /**
     * 删除AI 会话
     */
    @PreAuthorize("@ss.hasPermi('ai:aiChatSession:remove')")
    @Log(title = "AI 会话", businessType = BusinessType.DELETE)
    @DeleteMapping("/{aiChatSessionIds}")
    public R<Void> remove(@PathVariable Long[] aiChatSessionIds) {
        List<Long> idList = Convert.toLongList(aiChatSessionIds);
        return toR(aiChatSessionService.deleteAiChatSessionByIdList(idList));
    }
}
