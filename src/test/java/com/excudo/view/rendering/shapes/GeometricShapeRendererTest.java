package com.excudo.view.rendering.shapes;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for connector diagonal selection in {@link GeometricShapeRenderer}.
 *
 * A straight connector is stored as a bounding box plus flipH/flipV that pick
 * which diagonal the line runs along. The renderer previously hardcoded the
 * main diagonal (minX,minY)->(maxX,maxY) and ignored the flips, so every
 * flipped connector -- common in mermaid "graph LR" fan-outs and in
 * PowerPoint-authored flowcharts -- rendered mirrored. These tests pin the
 * flip-aware endpoint math.
 */
public class GeometricShapeRendererTest {

    private static final double X1 = 100, Y1 = 200, X2 = 400, Y2 = 600;

    private static void assertEndpoints(double[] e, double x1, double y1, double x2, double y2) {
        assertArrayEquals(new double[]{x1, y1, x2, y2}, e, 0.0001);
    }

    @Test
    public void noFlip_drawsMainDiagonal() {
        // TL -> BR
        assertEndpoints(GeometricShapeRenderer.connectorEndpoints(X1, Y1, X2, Y2, false, false),
            X1, Y1, X2, Y2);
    }

    @Test
    public void flipV_drawsAntiDiagonal() {
        // Source below target: line should run BL -> TR, not mirrored down.
        assertEndpoints(GeometricShapeRenderer.connectorEndpoints(X1, Y1, X2, Y2, false, true),
            X1, Y2, X2, Y1);
    }

    @Test
    public void flipH_drawsAntiDiagonal() {
        // Source right of target: same anti-diagonal segment as flipV (no
        // arrowhead is drawn, so only the diagonal matters).
        assertEndpoints(GeometricShapeRenderer.connectorEndpoints(X1, Y1, X2, Y2, true, false),
            X1, Y2, X2, Y1);
    }

    @Test
    public void flipBoth_returnsToMainDiagonal() {
        // flipH AND flipV cancel back to the main diagonal.
        assertEndpoints(GeometricShapeRenderer.connectorEndpoints(X1, Y1, X2, Y2, true, true),
            X1, Y1, X2, Y2);
    }
}
