package com.excudo.core.mermaid.emitter;

import com.excudo.core.mermaid.ast.DiagramEdge;
import com.excudo.core.mermaid.ast.Direction;
import com.excudo.core.mermaid.ast.Subgraph;
import com.excudo.core.mermaid.layout.PositionedDiagram;
import com.excudo.core.mermaid.layout.PositionedEdge;
import com.excudo.core.mermaid.layout.PositionedNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Emits OOXML shape and connector specifications from a positioned diagram.
 */
public class OOXMLEmitter {

    private OOXMLEmitter() {}

    /** Padding around subgraph bounding boxes in EMUs (~0.15 inch). */
    private static final long SUBGRAPH_PADDING = 137160;
    /** Edge label text box size in EMUs. */
    private static final long LABEL_WIDTH = 914400;   // 1 inch
    private static final long LABEL_HEIGHT = 342900;  // ~0.375 inch

    public static DiagramOutput emit(PositionedDiagram diagram) {
        return emit(diagram, List.of());
    }

    public static DiagramOutput emit(PositionedDiagram diagram, List<Subgraph> subgraphs) {
        List<ShapeSpec> shapes = new ArrayList<>();
        List<ConnectorSpec> connectors = new ArrayList<>();
        List<ShapeSpec> labelShapes = new ArrayList<>();

        // Emit subgraph bounding boxes (behind nodes)
        for (Subgraph sub : subgraphs) {
            ShapeSpec subShape = computeSubgraphShape(sub, diagram);
            if (subShape != null) {
                shapes.add(subShape);
            }
        }

        // Emit shapes
        for (PositionedNode pn : diagram.nodes()) {
            String preset = ShapeHintMapper.toOoxmlPreset(pn.node().shapeHint());
            shapes.add(new ShapeSpec(
                pn.node().id(),
                preset,
                pn.node().label(),
                pn.x(), pn.y(),
                pn.width(), pn.height(),
                null, null
            ));
        }

        // Emit connectors
        for (PositionedEdge pe : diagram.edges()) {
            DiagramEdge edge = pe.edge();
            EdgeTypeMapper.ConnectorStyle style = EdgeTypeMapper.toConnectorStyle(
                edge.edgeType(), edge.bidirectional());

            // Compute connector geometry from source/target positions
            PositionedNode src = findNode(diagram, edge.fromId());
            PositionedNode tgt = findNode(diagram, edge.toId());

            int[] indices = getConnectionIndices(diagram.direction());
            int srcIdx = indices[0];
            int tgtIdx = indices[1];

            // Connector position: bounding box + flip flags from connection points
            ConnectorGeometry connGeom = computeConnectorGeometry(src, tgt, srcIdx, tgtIdx);

            connectors.add(new ConnectorSpec(
                edge.fromId(),
                edge.toId(),
                style.connectorType(),
                style.headEnd(),
                style.tailEnd(),
                style.lineStyle(),
                null,
                srcIdx,
                tgtIdx,
                connGeom.x, connGeom.y,
                connGeom.width, connGeom.height,
                connGeom.flipH, connGeom.flipV,
                edge.label()
            ));

            // Emit edge label as a text-box shape near connector midpoint.
            // For vertical flows, offset labels right of center to avoid overlapping
            // nodes that sit directly on the connector path.
            if (edge.label() != null && !edge.label().isBlank()) {
                long[] srcPt = connectionPoint(src, srcIdx);
                long[] tgtPt = connectionPoint(tgt, tgtIdx);
                boolean isVertical = (diagram.direction() == Direction.TB || diagram.direction() == Direction.BT);
                long labelOffsetX = isVertical ? src.width() / 2 + 91440 : 0;  // shift right of node + small gap
                long labelOffsetY = isVertical ? 0 : src.height() / 2 + 91440;
                long midX = (srcPt[0] + tgtPt[0]) / 2 - LABEL_WIDTH / 2 + labelOffsetX;
                long midY = (srcPt[1] + tgtPt[1]) / 2 - LABEL_HEIGHT / 2 + labelOffsetY;

                labelShapes.add(new ShapeSpec(
                    "label_" + edge.fromId() + "_" + edge.toId(),
                    "rect",
                    edge.label(),
                    midX, midY,
                    LABEL_WIDTH, LABEL_HEIGHT,
                    null, null
                ));
            }
        }

        return new DiagramOutput(shapes, connectors, labelShapes);
    }

    /**
     * Compute bounding box for a subgraph based on its child node positions.
     * Returns null if the subgraph has no positioned children.
     */
    private static ShapeSpec computeSubgraphShape(Subgraph sub, PositionedDiagram diagram) {
        long minX = Long.MAX_VALUE, minY = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE, maxY = Long.MIN_VALUE;
        int found = 0;

        for (String childId : sub.childNodeIds()) {
            PositionedNode pn = findNodeOrNull(diagram, childId);
            if (pn == null) continue;
            found++;
            minX = Math.min(minX, pn.x());
            minY = Math.min(minY, pn.y());
            maxX = Math.max(maxX, pn.x() + pn.width());
            maxY = Math.max(maxY, pn.y() + pn.height());
        }

        if (found == 0) return null;

        // Add padding and space for label at top
        long labelSpace = 228600; // ~0.25 inch for subgraph title
        return new ShapeSpec(
            "subgraph_" + sub.id(),
            "roundRect",
            sub.label(),
            minX - SUBGRAPH_PADDING,
            minY - SUBGRAPH_PADDING - labelSpace,
            (maxX - minX) + 2 * SUBGRAPH_PADDING,
            (maxY - minY) + 2 * SUBGRAPH_PADDING + labelSpace,
            null,
            "BFBFBF"
        );
    }

    private static PositionedNode findNode(PositionedDiagram diagram, String nodeId) {
        for (PositionedNode pn : diagram.nodes()) {
            if (pn.node().id().equals(nodeId)) return pn;
        }
        throw new IllegalStateException("Node not found in positioned diagram: " + nodeId);
    }

    private static PositionedNode findNodeOrNull(PositionedDiagram diagram, String nodeId) {
        for (PositionedNode pn : diagram.nodes()) {
            if (pn.node().id().equals(nodeId)) return pn;
        }
        return null;
    }

    /**
     * Connection point indices per OOXML standard:
     *   idx=0: top, idx=1: left, idx=2: bottom, idx=3: right
     */
    private static int[] getConnectionIndices(Direction direction) {
        return switch (direction) {
            case TB -> new int[]{2, 0};  // source bottom -> target top
            case BT -> new int[]{0, 2};  // source top -> target bottom
            case LR -> new int[]{3, 1};  // source right -> target left
            case RL -> new int[]{1, 3};  // source left -> target right
        };
    }

    private record ConnectorGeometry(long x, long y, long width, long height, boolean flipH, boolean flipV) {}

    /**
     * Compute connector bounding box and flip flags from connection points.
     *
     * OOXML connectors use (x, y) as the top-left of a bounding box with positive (cx, cy).
     * The flip attributes control which corner the line starts from:
     *   - No flip: start at top-left, end at bottom-right
     *   - flipH: start at top-right, end at bottom-left
     *   - flipV: start at bottom-left, end at top-right
     *   - Both: start at bottom-right, end at top-left
     *
     * For a TD flowchart, the source exit is bottom-center and target entry is top-center.
     * If the target is to the LEFT of the source, the connector goes right-to-left: flipH.
     */
    private static ConnectorGeometry computeConnectorGeometry(
            PositionedNode src, PositionedNode tgt, int srcIdx, int tgtIdx) {

        long[] srcPt = connectionPoint(src, srcIdx);
        long[] tgtPt = connectionPoint(tgt, tgtIdx);

        // Bounding box: always top-left origin with positive extents
        long minX = Math.min(srcPt[0], tgtPt[0]);
        long minY = Math.min(srcPt[1], tgtPt[1]);
        long w = Math.abs(srcPt[0] - tgtPt[0]);
        long h = Math.abs(srcPt[1] - tgtPt[1]);

        // Flip detection: the default connector direction is top-left -> bottom-right.
        // If the source point is to the RIGHT of the target, the line goes right-to-left: flipH.
        // If the source point is BELOW the target, the line goes bottom-to-top: flipV.
        boolean flipH = srcPt[0] > tgtPt[0];
        boolean flipV = srcPt[1] > tgtPt[1];

        return new ConnectorGeometry(minX, minY, w, h, flipH, flipV);
    }

    /**
     * Get the absolute position of a connection point on a shape.
     */
    private static long[] connectionPoint(PositionedNode node, int idx) {
        return switch (idx) {
            case 0 -> new long[]{node.x() + node.width() / 2, node.y()};          // top center
            case 1 -> new long[]{node.x(), node.y() + node.height() / 2};         // left center
            case 2 -> new long[]{node.x() + node.width() / 2, node.y() + node.height()};  // bottom center
            case 3 -> new long[]{node.x() + node.width(), node.y() + node.height() / 2};  // right center
            default -> new long[]{node.x() + node.width() / 2, node.y() + node.height() / 2}; // center
        };
    }
}
