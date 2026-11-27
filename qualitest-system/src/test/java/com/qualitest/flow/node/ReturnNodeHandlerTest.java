package com.qualitest.flow.node;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.PlaceholderResolver;
import com.qualitest.flow.delivery.DeliverySink;
import com.qualitest.flow.delivery.EntryKind;
import com.qualitest.flow.delivery.InboundSlotRegistry;
import com.qualitest.flow.delivery.OutPayload;
import com.qualitest.flow.delivery.RunDelivery;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.ReturnNodeHandler;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.model.FlowNodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 ReturnNodeHandler：from 晋升 out、http-return status、缺 from 失败。
 * 边界：内存 DeliverySink；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ReturnNodeHandlerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReturnNodeHandlerTest {

    private final RunDelivery delivery = new RunDelivery(new InboundSlotRegistry(4));
    private final ReturnNodeHandler handler = new ReturnNodeHandler(delivery, PlaceholderResolver.strict());

    /**
     * 前提：flow.pkg 已有对象，http-return data.from=flow.pkg。
     * 期望：passed；out 与 pkg 一致；sink 收到交付包。
     */
    @Test
    @Order(1)
    @DisplayName("from flow.pkg 晋升 out 并交付")
    void execute_fromFlowPkg_promotesAndDelivers() {
        AtomicReference<OutPayload> got = new AtomicReference<>();
        Map<String, Object> pkg = new HashMap<>();
        pkg.put("status", 200);
        pkg.put("body", Map.of("ok", true));

        FlowRunContext ctx = FlowRunContext.builder()
                .entryKind(EntryKind.HTTP.getCode())
                .trigger(EntryKind.HTTP.getCode())
                .inboundSlotToken("slot-a")
                .deliverySink(new DeliverySink() {
                    @Override
                    public boolean complete(OutPayload out) {
                        got.set(out.copy());
                        return true;
                    }

                    @Override
                    public boolean isConnected() {
                        return true;
                    }
                })
                .build();
        ctx.getFlow().put("pkg", pkg);

        GraphNode node = GraphNode.builder()
                .id("r1")
                .type(FlowNodeType.HTTP_RETURN.getCode())
                .name("返回")
                .data(Map.of("from", "flow.pkg"))
                .build();

        StepResult result = handler.execute(ctx, node, "e1");
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertEquals(200, ctx.getOut().get("status"));
        assertEquals(true, ((Map<?, ?>) ctx.getOut().get("body")).get("ok"));
        assertNotNull(got.get());
        assertEquals(200, got.get().getStatus());
        assertTrue(ctx.isDelivered());
    }

    /**
     * 前提：flow.pkg 无 status，节点 data.status=400。
     * 期望：out.status=400 并写入交付包。
     */
    @Test
    @Order(2)
    @DisplayName("http-return status 覆盖为 400")
    void execute_httpReturnStatus_overridesTo400() {
        AtomicReference<OutPayload> got = new AtomicReference<>();
        FlowRunContext ctx = FlowRunContext.builder()
                .entryKind(EntryKind.HTTP.getCode())
                .trigger(EntryKind.HTTP.getCode())
                .inboundSlotToken("slot-b")
                .deliverySink(new DeliverySink() {
                    @Override
                    public boolean complete(OutPayload out) {
                        got.set(out.copy());
                        return true;
                    }

                    @Override
                    public boolean isConnected() {
                        return true;
                    }
                })
                .build();
        ctx.getFlow().put("pkg", Map.of("body", Map.of("code", "ERR")));

        Map<String, Object> data = new HashMap<>();
        data.put("from", "flow.pkg");
        data.put("status", 400);
        GraphNode node = GraphNode.builder()
                .id("r1")
                .type(FlowNodeType.HTTP_RETURN.getCode())
                .name("错误返回")
                .data(data)
                .build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertEquals(400, ctx.getOut().get("status"));
        assertNotNull(got.get());
        assertEquals(400, got.get().getStatus());
    }

    /**
     * 前提：返回节点 data 无 from。
     * 期望：failed，错误信息含「缺少 data.from」。
     */
    @Test
    @Order(3)
    @DisplayName("缺 from 时步骤失败")
    void execute_missingFrom_failsStep() {
        FlowRunContext ctx = FlowRunContext.builder()
                .entryKind(EntryKind.HTTP.getCode())
                .trigger(EntryKind.HTTP.getCode())
                .build();

        GraphNode node = GraphNode.builder()
                .id("r1")
                .type(FlowNodeType.HTTP_RETURN.getCode())
                .name("空返回")
                .data(new HashMap<>())
                .build();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.FAILED.getCode(), result.getStatus());
        assertNotNull(result.getError());
        assertEquals(FlowErrorCode.TF_STEP_ERROR.getCode(), result.getError().getCode());
        assertTrue(result.getError().getMessage().contains("缺少 data.from"));
    }
}