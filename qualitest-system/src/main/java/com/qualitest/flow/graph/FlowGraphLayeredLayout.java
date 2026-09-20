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
 * 有边：按拓扑从左到右分层，同层节点从上到下排列；层数过多时按列折成多行带。
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
    /** 有边分层时，单行带最多列数 */
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
     * 列的 x：上一列最大宽度 + 水平空隙；同层 y：上一节点高度 + 垂直空隙。
     */
    private static double layoutComponent(
            Map<String, GraphNode> byId,
            List<String> component,
            Map<String, List<String>> outs,
            double originY,
            Map<String, Size> sizes) {
        Set<String> inComp = new HashSet<>(component);
        Map<String, Integer> layerOf = assignLayers(component, outs, inComp);

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

        int layerSpan = maxLayer + 1;
        int cols = resolveLayerColumns(component.size(), layerSpan);
        int bandCount = (layerSpan + cols - 1) / cols;

        double[] bandOriginYs = new double[bandCount];
        double[][] bandColXs = new double[bandCount][];

        double cursorBandY = originY;
        for (int band = 0; band < bandCount; band++) {
            int layerStart = band * cols;
            int layerEnd = Math.min(layerStart + cols - 1, maxLayer);
            int colCount = layerEnd - layerStart + 1;

            double[] colMaxW = new double[colCount];
            double[] colStackH = new double[colCount];
            for (int c = 0; c < colCount; c++) {
                List<String> layerNodes = byLayer.getOrDefault(layerStart + c, List.of());
                double maxW = NODE_W;
                double stackH = 0;
                for (int row = 0; row < layerNodes.size(); row++) {
                    Size s = sizes.getOrDefault(layerNodes.get(row), Size.of(NODE_W, NODE_MIN_H));
                    maxW = Math.max(maxW, s.w);
                    stackH += s.h;
                    if (row < layerNodes.size() - 1) {
                        stackH += GAP_Y;
                    }
                }
                if (layerNodes.isEmpty()) {
                    stackH = NODE_MIN_H;
                }
                colMaxW[c] = maxW;
                colStackH[c] = stackH;
            }

            double[] colXs = new double[colCount];
            double x = ORIGIN_X;
            for (int c = 0; c < colCount; c++) {
                colXs[c] = x;
                x += colMaxW[c] + GAP_X;
            }
            bandColXs[band] = colXs;

            double height = NODE_MIN_H;
            for (double h : colStackH) {
                height = Math.max(height, h);
            }
            bandOriginYs[band] = cursorBandY;
            cursorBandY += height + BAND_GAP_Y;
        }

        for (int layer = 0; layer <= maxLayer; layer++) {
            List<String> layerNodes = byLayer.getOrDefault(layer, List.of());
            int col = layer % cols;
            int band = layer / cols;
            double bandOriginY = bandOriginYs[band];
            double colX = bandColXs[band].length > col ? bandColXs[band][col] : ORIGIN_X;
            double y = bandOriginY;
            for (String id : layerNodes) {
                GraphNode node = byId.get(id);
                Size s = sizes.getOrDefault(id, Size.of(NODE_W, NODE_MIN_H));
                if (node != null) {
                    node.setPosition(pos(colX, y));
                }
                y += s.h + GAP_Y;
            }
        }

        return cursorBandY - originY - BAND_GAP_Y;
    }

    /**
     * 估算单行带应放几列，让整体更接近方正。
     */
    static int resolveLayerColumns(int nodeCount, int layerSpan) {
        if (layerSpan <= 1) {
            return 1;
        }
        int ideal = (int) Math.ceil(Math.sqrt(Math.max(1, nodeCount)));
        int cols = Math.min(MAX_LAYER_COLUMNS, Math.max(MIN_LAYER_COLUMNS, ideal));
        return Math.min(cols, layerSpan);
    }

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
