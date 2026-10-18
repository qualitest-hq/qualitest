package com.qualitest.flow.graph;

import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * 测试流画布自动排版。
 * <p>
 * 有边：按拓扑从左到右分层；同层按父子重心排序，列内相对父节点纵向对齐。
 * 列数按节点规模折行，避免拉成一行。回边不参与抬层。
 * 无边：按固定列数网格换行。
 * 落位间距按每个节点的占位宽高计算（按类型估算：条件节点更宽，并随分支数增高），避免卡片互相重叠。
 * <p>
 * 对外能力：
 * <ul>
 *   <li>apply：重排整图全部节点坐标</li>
 *   <li>placeNewNodes：只给指定新增节点写坐标，已有节点不动</li>
 * </ul>
 */
public final class FlowGraphLayeredLayout {

    /** 画布排版原点横坐标 */
    public static final double ORIGIN_X = 40.0;
    /** 画布排版原点纵坐标 */
    public static final double ORIGIN_Y = 80.0;
    /** 标准节点占位宽度 */
    public static final double NODE_W = 300.0;
    /** 条件节点占位宽度 */
    public static final double COND_NODE_W = 340.0;
    /** 标准节点占位最小高度 */
    public static final double NODE_MIN_H = 108.0;
    /** 节点标题区高度（估算条件卡高度用） */
    public static final double NODE_HEAD_H = 52.0;
    /** 条件分支单行高度 */
    public static final double COND_ROW_H = 40.0;
    /** 条件卡内容区内边距 */
    public static final double COND_BODY_PAD = 12.0;
    /** 相邻节点右缘到下一节点左缘的水平空隙 */
    public static final double GAP_X = 160.0;
    /** 相邻节点下缘到下一节点上缘的垂直空隙 */
    public static final double GAP_Y = 52.0;
    /**
     * 默认层间距（标准宽节点左缘到左缘）。
     * 有按类型估算的宽高时按实际宽度累加，不再固定用此值。
     */
    public static final double LAYER_GAP_X = NODE_W + GAP_X;
    /** 默认同层行距（标准高节点上缘到上缘） */
    public static final double NODE_GAP_Y = NODE_MIN_H + GAP_Y;
    /** 互不连通的子图之间额外空出的垂直间距 */
    public static final double COMPONENT_GAP_Y = 80.0;
    /** 折行后的行带与行带之间的垂直间距 */
    public static final double BAND_GAP_Y = 80.0;
    /** 无边网格排版时，每一行最多放几个节点 */
    public static final int WRAP_COLUMNS = 4;
    /** 有边分层时，单行带至少保留的列数 */
    public static final int MIN_LAYER_COLUMNS = 3;
    /** 有边分层时，单行带最多列数，超出则折到下一行带 */
    public static final int MAX_LAYER_COLUMNS = 5;

    private FlowGraphLayeredLayout() {
    }

    /**
     * 对整图所有节点重新计算坐标并写回。
     * 无节点或图为空时直接返回入参，不抛异常。
     *
     * @param graph 待排版的图，可为 null
     * @return 同一图实例（坐标已改写）
     */
    public static GraphJson apply(GraphJson graph) {
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            return graph;
        }
        Map<String, GraphNode> byId = indexById(graph.getNodes());
        if (byId.isEmpty()) {
            return graph;
        }

        Map<String, Size> sizes = new HashMap<>();
        for (Map.Entry<String, GraphNode> e : byId.entrySet()) {
            sizes.put(e.getKey(), resolveSize(e.getValue()));
        }

        List<GraphEdge> edges = graph.getEdges() != null ? graph.getEdges() : List.of();
        Map<String, List<String>> outs = new HashMap<>();
        for (String id : byId.keySet()) {
            outs.put(id, new ArrayList<>());
        }
        boolean hasEdge = false;
        for (GraphEdge e : edges) {
            if (e == null) {
                continue;
            }
            String s = e.getSource();
            String t = e.getTarget();
            if (s == null || t == null || !byId.containsKey(s) || !byId.containsKey(t) || s.equals(t)) {
                continue;
            }
            outs.get(s).add(t);
            hasEdge = true;
        }

        if (!hasEdge) {
            layoutWrap(byId, sizes);
            return graph;
        }

        List<List<String>> components = connectedComponents(byId.keySet(), outs);
        double cursorY = ORIGIN_Y;
        for (List<String> component : components) {
            double usedHeight = layoutComponent(byId, component, outs, cursorY, sizes);
            cursorY += usedHeight + COMPONENT_GAP_Y;
        }
        return graph;
    }

    /**
     * 只给指定 id 的节点计算并写入坐标，其余节点坐标保持不变。
     * <p>
     * 落位策略：
     * <ul>
     *   <li>有已定位的上游源节点：放在其右缘 + 水平空隙；超出单行带最大宽度则折到下一行</li>
     *   <li>无上游：按换行网格相对已有节点避让</li>
     * </ul>
     * 放置顺序：上游已就绪的节点优先；重叠判断使用各方占位宽高。
     *
     * @param graph      已包含待排节点与边的完整图
     * @param newNodeIds 允许改动坐标的节点 id 集合
     */
    public static void placeNewNodes(GraphJson graph, Collection<String> newNodeIds) {
        if (graph == null || graph.getNodes() == null || newNodeIds == null || newNodeIds.isEmpty()) {
            return;
        }
        Set<String> movable = new LinkedHashSet<>();
        for (String id : newNodeIds) {
            if (id != null && !id.isBlank()) {
                movable.add(id);
            }
        }
        if (movable.isEmpty()) {
            return;
        }

        Map<String, GraphNode> byId = indexById(graph.getNodes());
        Map<String, Size> sizes = new HashMap<>();
        for (Map.Entry<String, GraphNode> e : byId.entrySet()) {
            sizes.put(e.getKey(), resolveSize(e.getValue()));
        }

        List<Rect> obstacles = new ArrayList<>();
        for (Map.Entry<String, GraphNode> e : byId.entrySet()) {
            if (movable.contains(e.getKey())) {
                continue;
            }
            GraphNodePosition p = e.getValue().getPosition();
            if (p != null) {
                Size s = sizes.getOrDefault(e.getKey(), Size.of(NODE_W, NODE_MIN_H));
                obstacles.add(new Rect(p.getX(), p.getY(), s.w, s.h));
            }
        }

        Map<String, List<String>> sourcesOf = new HashMap<>();
        List<GraphEdge> edges = graph.getEdges() != null ? graph.getEdges() : List.of();
        for (GraphEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            String s = edge.getSource();
            String t = edge.getTarget();
            if (s == null || t == null || !movable.contains(t) || !byId.containsKey(s)) {
                continue;
            }
            sourcesOf.computeIfAbsent(t, k -> new ArrayList<>()).add(s);
        }

        List<String> ordered = new ArrayList<>();
        Set<String> pending = new LinkedHashSet<>(movable);
        pending.retainAll(byId.keySet());
        while (!pending.isEmpty()) {
            String pick = null;
            for (String id : pending) {
                List<String> srcs = sourcesOf.getOrDefault(id, List.of());
                boolean ready = true;
                for (String s : srcs) {
                    if (pending.contains(s)) {
                        ready = false;
                        break;
                    }
                }
                if (ready || srcs.isEmpty()) {
                    pick = id;
                    break;
                }
            }
            if (pick == null) {
                pick = pending.iterator().next();
            }
            pending.remove(pick);
            ordered.add(pick);
        }

        for (String id : ordered) {
            GraphNode node = byId.get(id);
            if (node == null) {
                continue;
            }
            Size size = sizes.getOrDefault(id, Size.of(NODE_W, NODE_MIN_H));
            GraphNodePosition preferred = preferredForNewNode(id, sourcesOf, byId, sizes, obstacles.size());
            GraphNodePosition free = findFreePosition(preferred, obstacles, size);
            node.setPosition(free);
            obstacles.add(new Rect(free.getX(), free.getY(), size.w, size.h));
        }
    }

    /**
     * 按节点类型估算占位宽高。
     * 条件节点更宽，高度随 branches 条数增加；其它类型用标准宽高。
     */
    static Size resolveSize(GraphNode node) {
        if (node == null) {
            return Size.of(NODE_W, NODE_MIN_H);
        }
        String type = node.getType() != null ? node.getType().trim() : "";
        if ("condition".equals(type)) {
            int branches = countBranches(node.getData());
            double h = Math.max(
                    NODE_HEAD_H + COND_ROW_H + COND_BODY_PAD,
                    NODE_HEAD_H + Math.max(1, branches) * COND_ROW_H + COND_BODY_PAD);
            return Size.of(COND_NODE_W, h);
        }
        return Size.of(NODE_W, NODE_MIN_H);
    }

    /** 读取 data.branches 列表长度；缺失时按 1 条估算 */
    private static int countBranches(Map<String, Object> data) {
        if (data == null) {
            return 1;
        }
        Object raw = data.get("branches");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return list.size();
        }
        return 1;
    }

    /**
     * 为单个新增节点推算理想落点（尚未做重叠避让）。
     * 有上游：取所有已定位上游中「右缘 + 水平空隙」的最大值，y 取该上游的 y；
     * 若理想 x 超出单行带最大宽度预算则折到下一行左侧。
     * 无上游：按当前障碍数量走换行网格。
     */
    private static GraphNodePosition preferredForNewNode(
            String id,
            Map<String, List<String>> sourcesOf,
            Map<String, GraphNode> byId,
            Map<String, Size> sizes,
            int obstacleCount) {
        double bestX = ORIGIN_X;
        double bestY = ORIGIN_Y;
        double sourceH = NODE_MIN_H;
        boolean fromSource = false;
        for (String s : sourcesOf.getOrDefault(id, List.of())) {
            GraphNode sn = byId.get(s);
            if (sn == null || sn.getPosition() == null) {
                continue;
            }
            fromSource = true;
            Size ss = sizes.getOrDefault(s, Size.of(NODE_W, NODE_MIN_H));
            double right = sn.getPosition().getX() + ss.w + GAP_X;
            if (right >= bestX - 0.01) {
                bestX = Math.max(bestX, right);
                bestY = sn.getPosition().getY();
                sourceH = ss.h;
            }
        }
        if (fromSource) {
            // 单行带最大右缘预算：原点 + 最大列数×标准步距
            double maxX = ORIGIN_X + (MAX_LAYER_COLUMNS - 1) * LAYER_GAP_X;
            if (bestX > maxX + 0.01) {
                return pos(ORIGIN_X, bestY + sourceH + GAP_Y);
            }
            return pos(bestX, bestY);
        }
        return wrapPreferred(obstacleCount);
    }

    /**
     * 从 preferred 出发找不与 obstacles 重叠的空位。
     * 试探步长 = 候选宽/高 + 边距。
     */
    private static GraphNodePosition findFreePosition(
            GraphNodePosition preferred, List<Rect> obstacles, Size candidate) {
        double baseX = preferred != null ? preferred.getX() : ORIGIN_X;
        double baseY = preferred != null ? preferred.getY() : ORIGIN_Y;
        Size size = candidate != null ? candidate : Size.of(NODE_W, NODE_MIN_H);
        double stepX = size.w + GAP_X;
        double stepY = size.h + GAP_Y;
        final int maxShift = 40;
        for (int row = 0; row < maxShift; row++) {
            double y = baseY + row * stepY;
            for (int i = 0; i < maxShift; i++) {
                double x = baseX + i * stepX;
                if (!overlapsAny(x, y, size, obstacles)) {
                    return pos(x, y);
                }
            }
        }
        return pos(baseX + maxShift * stepX, baseY + maxShift * stepY);
    }

    /** 候选矩形是否与任一障碍矩形相交（边贴合不算重叠） */
    private static boolean overlapsAny(double x, double y, Size size, List<Rect> obstacles) {
        if (obstacles == null || obstacles.isEmpty()) {
            return false;
        }
        for (Rect o : obstacles) {
            if (o == null) {
                continue;
            }
            if (!(x + size.w <= o.x
                    || o.x + o.w <= x
                    || y + size.h <= o.y
                    || o.y + o.h <= y)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 无边时按行换列网格排布：同行内按节点宽度累加 x，行高取该行最高节点。
     */
    private static void layoutWrap(Map<String, GraphNode> byId, Map<String, Size> sizes) {
        List<String> ids = new ArrayList<>(byId.keySet());
        double rowY = ORIGIN_Y;
        for (int i = 0; i < ids.size(); i += WRAP_COLUMNS) {
            int end = Math.min(i + WRAP_COLUMNS, ids.size());
            double x = ORIGIN_X;
            double maxH = NODE_MIN_H;
            for (int j = i; j < end; j++) {
                String id = ids.get(j);
                GraphNode node = byId.get(id);
                Size s = sizes.getOrDefault(id, Size.of(NODE_W, NODE_MIN_H));
                if (node != null) {
                    node.setPosition(pos(x, rowY));
                }
                x += s.w + GAP_X;
                maxH = Math.max(maxH, s.h);
            }
            rowY += maxH + GAP_Y;
        }
    }

    /** 无边或无上游时的换行网格落点（标准节点步距，仅作候选起点） */
    private static GraphNodePosition wrapPreferred(int index) {
        int col = index % WRAP_COLUMNS;
        int row = index / WRAP_COLUMNS;
        return pos(ORIGIN_X + col * LAYER_GAP_X, ORIGIN_Y + row * NODE_GAP_Y);
    }

    /**
     * 对单个连通分量做分层落位。
     * 同层先按父节点重心排序（出边顺序作次序），再相对上一列父节点中心纵向对齐后紧凑堆叠。
     * 列的 x：上一列最大宽度 + 水平空隙。行带高度按对齐后的实际上下界计算。
     */
    private static double layoutComponent(
            Map<String, GraphNode> byId,
            List<String> component,
            Map<String, List<String>> outs,
            double originY,
            Map<String, Size> sizes) {
        Set<String> inComp = new HashSet<>(component);
        Map<String, List<String>> forward = forwardOuts(component, outs, inComp);
        Map<String, Integer> layerOf = assignLayers(component, forward, inComp);

        int maxLayer = 0;
        Map<Integer, List<String>> byLayer = new HashMap<>();
        Map<String, Integer> orderIndex = new HashMap<>();
        for (int i = 0; i < component.size(); i++) {
            orderIndex.put(component.get(i), i);
        }
        for (String id : component) {
            int layer = layerOf.getOrDefault(id, 0);
            maxLayer = Math.max(maxLayer, layer);
            byLayer.computeIfAbsent(layer, k -> new ArrayList<>()).add(id);
        }
        for (List<String> layerNodes : byLayer.values()) {
            layerNodes.sort((a, b) -> Integer.compare(
                    orderIndex.getOrDefault(a, 0),
                    orderIndex.getOrDefault(b, 0)));
        }
        Map<String, List<String>> ins = invertOuts(component, outs);
        orderLayersByBarycenter(byLayer, maxLayer, outs, ins, layerOf);

        int layerSpan = maxLayer + 1;
        int cols = resolveLayerColumns(component.size(), layerSpan);
        int bandCount = (layerSpan + cols - 1) / cols;

        double cursorBandY = originY;
        for (int band = 0; band < bandCount; band++) {
            int layerStart = band * cols;
            int layerEnd = Math.min(layerStart + cols - 1, maxLayer);
            int colCount = layerEnd - layerStart + 1;

            double[] colXs = new double[colCount];
            double x = ORIGIN_X;
            for (int c = 0; c < colCount; c++) {
                colXs[c] = x;
                List<String> layerNodes = byLayer.getOrDefault(layerStart + c, List.of());
                double maxW = NODE_W;
                for (String id : layerNodes) {
                    maxW = Math.max(maxW, sizes.getOrDefault(id, Size.of(NODE_W, NODE_MIN_H)).w);
                }
                x += maxW + GAP_X;
            }

            List<String> placed = new ArrayList<>();
            Map<String, Double> centerY = new HashMap<>();
            double minY = Double.POSITIVE_INFINITY;
            double maxBottom = Double.NEGATIVE_INFINITY;

            for (int c = 0; c < colCount; c++) {
                int layer = layerStart + c;
                List<String> layerNodes = byLayer.getOrDefault(layer, List.of());
                double colX = colXs[c];
                double stackH = columnStackHeight(layerNodes, sizes);
                double top = cursorBandY;
                if (c > 0 && !layerNodes.isEmpty()) {
                    List<Double> ideals = new ArrayList<>();
                    for (String id : layerNodes) {
                        Double ideal = parentCenterMedian(id, layer - 1, ins, layerOf, centerY);
                        if (ideal != null) {
                            ideals.add(ideal);
                        }
                    }
                    if (!ideals.isEmpty()) {
                        top = median(ideals) - stackH / 2.0;
                    }
                }
                double y = top;
                if (layerNodes.isEmpty()) {
                    minY = Math.min(minY, cursorBandY);
                    maxBottom = Math.max(maxBottom, cursorBandY + NODE_MIN_H);
                }
                for (String id : layerNodes) {
                    Size s = sizes.getOrDefault(id, Size.of(NODE_W, NODE_MIN_H));
                    GraphNode node = byId.get(id);
                    if (node != null) {
                        node.setPosition(pos(colX, y));
                    }
                    centerY.put(id, y + s.h / 2.0);
                    placed.add(id);
                    minY = Math.min(minY, y);
                    maxBottom = Math.max(maxBottom, y + s.h);
                    y += s.h + GAP_Y;
                }
            }

            if (minY < cursorBandY && minY != Double.POSITIVE_INFINITY) {
                double shift = cursorBandY - minY;
                for (String id : placed) {
                    GraphNode node = byId.get(id);
                    if (node == null || node.getPosition() == null) {
                        continue;
                    }
                    double ny = node.getPosition().getY() + shift;
                    node.setPosition(pos(node.getPosition().getX(), ny));
                    Size s = sizes.getOrDefault(id, Size.of(NODE_W, NODE_MIN_H));
                    centerY.put(id, ny + s.h / 2.0);
                }
                maxBottom += shift;
            }

            double height = Math.max(NODE_MIN_H, (maxBottom == Double.NEGATIVE_INFINITY ? cursorBandY : maxBottom) - cursorBandY);
            cursorBandY += height + BAND_GAP_Y;
        }

        return cursorBandY - originY - BAND_GAP_Y;
    }

    /** 同层节点自上而下紧凑堆叠后的总高度（含节点间距） */
    private static double columnStackHeight(List<String> ids, Map<String, Size> sizes) {
        if (ids == null || ids.isEmpty()) {
            return NODE_MIN_H;
        }
        double h = 0;
        for (int i = 0; i < ids.size(); i++) {
            h += sizes.getOrDefault(ids.get(i), Size.of(NODE_W, NODE_MIN_H)).h;
            if (i < ids.size() - 1) {
                h += GAP_Y;
            }
        }
        return h;
    }

    /**
     * 上一层父节点垂直中心的中位数。
     * 没有落在指定层的父节点时返回 null，调用方改为顶对齐。
     */
    private static Double parentCenterMedian(
            String id,
            int parentLayer,
            Map<String, List<String>> ins,
            Map<String, Integer> layerOf,
            Map<String, Double> centerY) {
        List<Double> centers = new ArrayList<>();
        for (String p : ins.getOrDefault(id, List.of())) {
            if (layerOf.getOrDefault(p, -1) != parentLayer) {
                continue;
            }
            Double c = centerY.get(p);
            if (c != null) {
                centers.add(c);
            }
        }
        if (centers.isEmpty()) {
            return null;
        }
        return median(centers);
    }

    /** 数值中位数；偶数个取中间两数平均 */
    private static double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compare);
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    /**
     * 同层重心排序：先按上一层父节点序号的中位数从上到下，
     * 重心相同时按父节点出边顺序；再按下一层子节点重心回扫一次。
     * 重心相同则保持当前相对顺序。
     */
    private static void orderLayersByBarycenter(
            Map<Integer, List<String>> byLayer,
            int maxLayer,
            Map<String, List<String>> outs,
            Map<String, List<String>> ins,
            Map<String, Integer> layerOf) {
        Map<String, Integer> rank = new HashMap<>();
        Runnable refresh = () -> {
            rank.clear();
            for (int layer = 0; layer <= maxLayer; layer++) {
                List<String> list = byLayer.getOrDefault(layer, List.of());
                for (int i = 0; i < list.size(); i++) {
                    rank.put(list.get(i), i);
                }
            }
        };
        refresh.run();

        for (int layer = 1; layer <= maxLayer; layer++) {
            List<String> list = byLayer.get(layer);
            if (list == null || list.size() < 2) {
                continue;
            }
            Map<String, Integer> cur = new HashMap<>();
            for (int i = 0; i < list.size(); i++) {
                cur.put(list.get(i), i);
            }
            final int parentLayer = layer - 1;
            list.sort((a, b) -> {
                List<String> pa = onLayer(ins.get(a), parentLayer, layerOf);
                List<String> pb = onLayer(ins.get(b), parentLayer, layerOf);
                double ba = bary(pa, a, rank);
                double bb = bary(pb, b, rank);
                if (ba != bb) {
                    return Double.compare(ba, bb);
                }
                int ea = edgeOrder(a, pa, outs);
                int eb = edgeOrder(b, pb, outs);
                if (ea != eb) {
                    return Integer.compare(ea, eb);
                }
                return Integer.compare(cur.getOrDefault(a, 0), cur.getOrDefault(b, 0));
            });
            refresh.run();
        }

        for (int layer = maxLayer - 1; layer >= 0; layer--) {
            List<String> list = byLayer.get(layer);
            if (list == null || list.size() < 2) {
                continue;
            }
            Map<String, Integer> cur = new HashMap<>();
            for (int i = 0; i < list.size(); i++) {
                cur.put(list.get(i), i);
            }
            final int childLayer = layer + 1;
            list.sort((a, b) -> {
                List<String> ca = onLayer(outs.get(a), childLayer, layerOf);
                List<String> cb = onLayer(outs.get(b), childLayer, layerOf);
                double ba = bary(ca, a, rank);
                double bb = bary(cb, b, rank);
                if (ba != bb) {
                    return Double.compare(ba, bb);
                }
                return Integer.compare(cur.getOrDefault(a, 0), cur.getOrDefault(b, 0));
            });
            refresh.run();
        }
    }

    /** 只保留落在指定层的邻居 */
    private static List<String> onLayer(List<String> pool, int layer, Map<String, Integer> layerOf) {
        List<String> hit = new ArrayList<>();
        if (pool == null) {
            return hit;
        }
        for (String n : pool) {
            if (layerOf.getOrDefault(n, -1) == layer) {
                hit.add(n);
            }
        }
        return hit;
    }

    /** 邻居在各层中的序号中位数；没有邻居时用自身当前序号 */
    private static double bary(List<String> ids, String self, Map<String, Integer> rank) {
        List<Double> rs = new ArrayList<>();
        for (String id : ids) {
            Integer r = rank.get(id);
            if (r != null) {
                rs.add(r.doubleValue());
            }
        }
        if (rs.isEmpty()) {
            return rank.getOrDefault(self, 0);
        }
        return median(rs);
    }

    /** 节点在若干父节点出边列表中的最靠前序号；没有则视为最后 */
    private static int edgeOrder(String id, List<String> parents, Map<String, List<String>> outs) {
        int best = Integer.MAX_VALUE;
        for (String p : parents) {
            int idx = outs.getOrDefault(p, List.of()).indexOf(id);
            if (idx >= 0 && idx < best) {
                best = idx;
            }
        }
        return best;
    }

    /** 由出边表得到入边表（仅分量内节点） */
    private static Map<String, List<String>> invertOuts(List<String> component, Map<String, List<String>> outs) {
        Map<String, List<String>> ins = new HashMap<>();
        for (String id : component) {
            ins.put(id, new ArrayList<>());
        }
        for (Map.Entry<String, List<String>> e : outs.entrySet()) {
            if (!ins.containsKey(e.getKey())) {
                continue;
            }
            for (String v : e.getValue()) {
                List<String> parents = ins.get(v);
                if (parents != null) {
                    parents.add(e.getKey());
                }
            }
        }
        return ins;
    }

    /**
     * 估算单行带应放几列，让整图更接近方块，而不是拉成一行。
     * 层数不超过 1 时返回 1；否则取 ceil(√节点数)，夹在最小列数与最大列数之间，且不超过实际层数。
     */
    static int resolveLayerColumns(int nodeCount, int layerSpan) {
        if (layerSpan <= 1) {
            return 1;
        }
        int ideal = (int) Math.ceil(Math.sqrt(Math.max(1, nodeCount)));
        int cols = Math.min(MAX_LAYER_COLUMNS, Math.max(MIN_LAYER_COLUMNS, ideal));
        return Math.min(cols, layerSpan);
    }

    /**
     * 去掉回边后的出边表。
     * 深度优先时指向仍在栈上的节点视为回边，该边不参与分层，避免环把节点层层拉开。
     */
    private static Map<String, List<String>> forwardOuts(
            List<String> component,
            Map<String, List<String>> outs,
            Set<String> inComp) {
        Map<String, List<String>> forward = new HashMap<>();
        for (String id : component) {
            forward.put(id, new ArrayList<>());
        }
        Map<String, Integer> color = new HashMap<>();
        for (String id : component) {
            if (color.getOrDefault(id, 0) == 0) {
                dfsForward(id, outs, inComp, forward, color);
            }
        }
        return forward;
    }

    private static void dfsForward(
            String u,
            Map<String, List<String>> outs,
            Set<String> inComp,
            Map<String, List<String>> forward,
            Map<String, Integer> color) {
        color.put(u, 1);
        for (String v : outs.getOrDefault(u, List.of())) {
            if (!inComp.contains(v)) {
                continue;
            }
            int c = color.getOrDefault(v, 0);
            if (c == 1) {
                continue;
            }
            forward.get(u).add(v);
            if (c == 0) {
                dfsForward(v, outs, inComp, forward, color);
            }
        }
        color.put(u, 2);
    }

    /**
     * 在连通分量内用 BFS 分层（只沿非回边）。
     * 无入边为第 0 层；沿出边层号 +1；多父时取较大层号。
     * 层号达到节点数时不再抬高，防止残余环无限加深。
     */
    private static Map<String, Integer> assignLayers(
            List<String> component,
            Map<String, List<String>> outs,
            Set<String> inComp) {
        Map<String, Integer> layerOf = new HashMap<>();
        List<String> roots = new ArrayList<>();
        for (String id : component) {
            if (countInEdgesFromComponent(id, component, outs) == 0) {
                roots.add(id);
            }
        }
        if (roots.isEmpty() && !component.isEmpty()) {
            roots.add(component.get(0));
        }

        int limit = component.size();
        Queue<String> q = new ArrayDeque<>();
        for (String r : roots) {
            layerOf.put(r, 0);
            q.add(r);
        }
        while (!q.isEmpty()) {
            String u = q.poll();
            int lu = layerOf.getOrDefault(u, 0);
            for (String v : outs.getOrDefault(u, List.of())) {
                if (!inComp.contains(v)) {
                    continue;
                }
                int next = lu + 1;
                if (next >= limit) {
                    continue;
                }
                Integer existing = layerOf.get(v);
                if (existing == null || next > existing) {
                    layerOf.put(v, next);
                    q.add(v);
                }
            }
        }
        for (String id : component) {
            layerOf.putIfAbsent(id, 0);
        }
        return layerOf;
    }

    private static int countInEdgesFromComponent(
            String id, List<String> component, Map<String, List<String>> outs) {
        int in = 0;
        for (String u : component) {
            for (String v : outs.getOrDefault(u, List.of())) {
                if (Objects.equals(v, id)) {
                    in++;
                }
            }
        }
        return in;
    }

    private static List<List<String>> connectedComponents(Set<String> ids, Map<String, List<String>> outs) {
        Map<String, List<String>> undirected = new HashMap<>();
        for (String id : ids) {
            undirected.put(id, new ArrayList<>());
        }
        for (Map.Entry<String, List<String>> e : outs.entrySet()) {
            String u = e.getKey();
            for (String v : e.getValue()) {
                if (!undirected.containsKey(v)) {
                    continue;
                }
                undirected.get(u).add(v);
                undirected.get(v).add(u);
            }
        }

        Set<String> seen = new HashSet<>();
        List<List<String>> components = new ArrayList<>();
        for (String start : ids) {
            if (!seen.add(start)) {
                continue;
            }
            List<String> comp = new ArrayList<>();
            Queue<String> q = new ArrayDeque<>();
            q.add(start);
            while (!q.isEmpty()) {
                String u = q.poll();
                comp.add(u);
                for (String v : undirected.getOrDefault(u, List.of())) {
                    if (seen.add(v)) {
                        q.add(v);
                    }
                }
            }
            components.add(comp);
        }
        return components;
    }

    private static Map<String, GraphNode> indexById(List<GraphNode> nodes) {
        Map<String, GraphNode> byId = new LinkedHashMap<>();
        if (nodes == null) {
            return byId;
        }
        for (GraphNode n : nodes) {
            if (n != null && n.getId() != null && !n.getId().isBlank()) {
                byId.putIfAbsent(n.getId(), n);
            }
        }
        return byId;
    }

    private static GraphNodePosition pos(double x, double y) {
        return GraphNodePosition.builder().x(x).y(y).build();
    }

    /** 节点占位宽高 */
    static final class Size {
        final double w;
        final double h;

        private Size(double w, double h) {
            this.w = w;
            this.h = h;
        }

        static Size of(double w, double h) {
            return new Size(w, h);
        }
    }

    /** 障碍矩形 */
    private static final class Rect {
        final double x;
        final double y;
        final double w;
        final double h;

        Rect(double x, double y, double w, double h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }
}
