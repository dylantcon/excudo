package com.excudo.view.rendering.tables;

import com.excudo.core.metrics.MeasuredText;
import com.excudo.core.metrics.TextBodyExtractor;
import com.excudo.core.metrics.TextMeasurer;
import com.excudo.core.metrics.TextStyleSource;
import com.excudo.core.model.BodyProperties;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TableModel;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.TextColor;
import com.excudo.core.model.TextParagraph;
import com.excudo.core.rendering.surface.RenderSurface;
import com.excudo.core.rendering.surface.StrokeCap;
import com.excudo.core.rendering.surface.SurfacePaint;
import com.excudo.view.rendering.CoordinateMapper;
import com.excudo.view.rendering.RenderingContext;
import com.excudo.view.rendering.ShapeStyleExtractor;
import com.excudo.view.rendering.SlideRenderContext;
import com.excudo.view.rendering.shapes.ModelShapeRenderer;
import com.excudo.view.rendering.text.LstStyleResolver;
import com.excudo.view.rendering.text.TextPainter;
import javafx.geometry.Rectangle2D;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders TABLE-typed graphicFrames: grid layout from a:tblGrid /
 * a:tr with merge spans, per-cell fill, border, and text passes in
 * PowerPoint's paint order (all fills, then all borders, then text —
 * the order its PDF export writes).
 *
 * <p>Styling resolves through layered parts, weakest to strongest:
 * wholeTbl &lt; column banding &lt; row banding &lt; firstCol/lastCol
 * &lt; firstRow/lastRow &lt; per-cell tcPr. Banding skips the header /
 * footer rows when the firstRow / lastRow flags are on, and band
 * indexing starts after them (tables-basic truth: data row 0 = band1H).
 * Merged cells resolve everything from their anchor slot and paint
 * once over the full span.
 *
 * <p>Borders: each grid boundary segment takes the higher-ranked of
 * the two adjacent cells' resolved edges (truth: the firstRow 3pt
 * bottom beats wholeTbl's 1pt insideH), skips merge interiors
 * entirely (truth: the x=568.8pt boundary strokes only above/below
 * the 2x2 block), and extends by half the crossing border's width at
 * each end so joints are solid, matching the truth PDF's segment
 * endpoints (435.3 = 433.8 + half of 3pt).
 *
 * <p>Cell text rides the shared pipeline (TextBodyExtractor -&gt;
 * TextMeasurer -&gt; TextPainter) at the merged span's width, with
 * tcPr margins/anchor overriding bodyPr and the style part's
 * tcTxStyle (bold / color / fontRef) injected as a LevelStyle overlay
 * under the run properties.
 */
public class TableRenderer implements ModelShapeRenderer {

    // Layer ranks, weakest -> strongest; tcPr outranks every style part.
    private static final int RANK_WHOLE = 1;
    private static final int RANK_BAND_COL = 2;
    private static final int RANK_BAND_ROW = 3;
    private static final int RANK_EDGE_COL = 4;
    private static final int RANK_EDGE_ROW = 5;
    private static final int RANK_TCPR = 10;

    private enum Side { LEFT, RIGHT, TOP, BOTTOM }

    /** Resolved border: color null = explicitly none (a:ln noFill). */
    private record Border(double widthPx, SurfacePaint.Solid color, int rank) {
        boolean paints() { return color != null && widthPx > 0; }
        boolean sameStroke(Border o) {
            return o != null && widthPx == o.widthPx
                && color != null && o.color != null
                && color.argb() == o.color.argb();
        }
    }

    /** One style part applying to a cell, with its region bounds
     *  (inclusive grid indices) for region-edge border selection. */
    private record Layer(TableStyle.Part part, int rank,
                         int rowStart, int rowEnd, int colStart, int colEnd) {}

    /** Fully resolved anchor cell. */
    private record Resolved(int r, int c, int rs, int cs, TableModel.Cell cell,
                            SurfacePaint fill, Border left, Border right,
                            Border top, Border bottom,
                            TextStyleSource.LevelStyle textOverlay) {}

    @Override
    public boolean canRender(SlideShape.ShapeType type) {
        return type == SlideShape.ShapeType.TABLE;
    }

    @Override
    public void render(SlideShape shape, RenderingContext ctx, SlideRenderContext slideCtx) {
        TableModel table = shape.getTableModel();
        ShapeGeometry geom = shape.getGeometry();
        if (table == null || geom == null || geom.getWidth() <= 0 || geom.getHeight() <= 0) {
            return;
        }

        CoordinateMapper mapper = ctx.getZoomedCoordinateMapper();
        RenderSurface surface = ctx.getSurface();

        Rectangle2D frame = mapper.mapToCanvas(geom.getX(), geom.getY(),
            geom.getWidth(), geom.getHeight());
        double rotDeg = geom.getRotationDegrees();
        if (rotDeg != 0) {
            double cx = frame.getMinX() + frame.getWidth() / 2;
            double cy = frame.getMinY() + frame.getHeight() / 2;
            surface.save();
            surface.translate(cx, cy);
            surface.rotate(rotDeg);
            surface.translate(-cx, -cy);
        }

        int rows = table.getRowCount();
        int cols = table.getColumnCount();

        // Grid boundary positions. The layout authority is the grid
        // (gridCol widths / tr heights), not the frame ext -- PowerPoint
        // lets the two disagree after column resizes (tables-basic ext
        // matches the grid sum; a stale ext must not rescale the grid).
        long[] colEmu = new long[cols + 1];
        for (int c = 0; c < cols; c++) colEmu[c + 1] = colEmu[c] + table.getColumnWidthEmu(c);
        long[] rowEmu = new long[rows + 1];
        for (int r = 0; r < rows; r++) rowEmu[r + 1] = rowEmu[r] + table.getRows().get(r).heightEmu();

        double[] colPx = new double[cols + 1];
        for (int c = 0; c <= cols; c++) {
            colPx[c] = mapper.mapToCanvas(geom.getX() + colEmu[c], geom.getY(), 0, 0).getMinX();
        }
        double[] rowPx = new double[rows + 1];
        for (int r = 0; r <= rows; r++) {
            rowPx[r] = mapper.mapToCanvas(geom.getX(), geom.getY() + rowEmu[r], 0, 0).getMinY();
        }

        TableStyle style = TableStyleResolver.resolve(table.getProperties().styleId(), slideCtx);

        // Resolve every anchor cell once.
        Resolved[][] resolved = new Resolved[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!table.isAnchor(r, c)) continue;
                resolved[r][c] = resolveCell(table, style, r, c, mapper, slideCtx);
            }
        }

        // ===== Pass 1: fills, once per merged span =====
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Resolved cell = resolved[r][c];
                if (cell == null || cell.fill() == null
                        || cell.fill() == SurfacePaint.Transparent.INSTANCE) continue;
                double x = colPx[c], y = rowPx[r];
                double w = colPx[c + cell.cs()] - x, h = rowPx[r + cell.rs()] - y;
                surface.setFill(cell.fill());
                surface.fillRect(x, y, w, h);
            }
        }

        // ===== Pass 2: borders =====
        // Boundary winners: higher rank of the two adjacent cells' edges.
        Border[][] vWin = new Border[cols + 1][rows];
        for (int b = 0; b <= cols; b++) {
            for (int r = 0; r < rows; r++) {
                if (b > 0 && b < cols) {
                    int[] la = table.anchorOf(r, b - 1);
                    int[] ra = table.anchorOf(r, b);
                    if (la[0] == ra[0] && la[1] == ra[1]) continue; // merge interior
                }
                Border cand = null;
                if (b > 0) {
                    Resolved left = resolved[table.anchorOf(r, b - 1)[0]][table.anchorOf(r, b - 1)[1]];
                    if (left != null && left.c() + left.cs() == b) cand = left.right();
                }
                if (b < cols) {
                    Resolved right = resolved[table.anchorOf(r, b)[0]][table.anchorOf(r, b)[1]];
                    if (right != null && right.c() == b) cand = maxRank(cand, right.left());
                }
                vWin[b][r] = cand;
            }
        }
        Border[][] hWin = new Border[rows + 1][cols];
        for (int rb = 0; rb <= rows; rb++) {
            for (int c = 0; c < cols; c++) {
                if (rb > 0 && rb < rows) {
                    int[] ta = table.anchorOf(rb - 1, c);
                    int[] ba = table.anchorOf(rb, c);
                    if (ta[0] == ba[0] && ta[1] == ba[1]) continue; // merge interior
                }
                Border cand = null;
                if (rb > 0) {
                    Resolved top = resolved[table.anchorOf(rb - 1, c)[0]][table.anchorOf(rb - 1, c)[1]];
                    if (top != null && top.r() + top.rs() == rb) cand = top.bottom();
                }
                if (rb < rows) {
                    Resolved bottom = resolved[table.anchorOf(rb, c)[0]][table.anchorOf(rb, c)[1]];
                    if (bottom != null && bottom.r() == rb) cand = maxRank(cand, bottom.top());
                }
                hWin[rb][c] = cand;
            }
        }

        // Truth draw order: interior verticals, interior horizontals,
        // then the outer left / right / top / bottom borders.
        for (int b = 1; b < cols; b++) strokeVertical(surface, b, vWin, hWin, colPx, rowPx, rows, cols);
        for (int rb = 1; rb < rows; rb++) strokeHorizontal(surface, rb, hWin, vWin, colPx, rowPx, rows, cols);
        strokeVertical(surface, 0, vWin, hWin, colPx, rowPx, rows, cols);
        strokeVertical(surface, cols, vWin, hWin, colPx, rowPx, rows, cols);
        strokeHorizontal(surface, 0, hWin, vWin, colPx, rowPx, rows, cols);
        strokeHorizontal(surface, rows, hWin, vWin, colPx, rowPx, rows, cols);

        // ===== Pass 3: cell text =====
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Resolved cell = resolved[r][c];
                if (cell == null || cell.cell().txBody() == null) continue;
                paintCellText(cell, table, colEmu, colPx, rowPx, ctx, slideCtx);
            }
        }

        if (rotDeg != 0) {
            surface.restore();
        }
    }

    // ====================================================================
    // Per-cell style resolution
    // ====================================================================

    private static Resolved resolveCell(TableModel table, TableStyle style, int r, int c,
                                        CoordinateMapper mapper, SlideRenderContext slideCtx) {
        TableModel.Cell cell = table.getCell(r, c);
        int rs = cell.rowSpan(), cs = cell.gridSpan();
        List<Layer> layers = layersFor(table, style, r, c, rs, cs);

        // Fill: tcPr override, else the strongest layer that authors one.
        SurfacePaint fill = null;
        if (cell.fill() != null) {
            fill = ShapeStyleExtractor.resolveFillChoice(cell.fill(), slideCtx);
        } else {
            for (int i = layers.size() - 1; i >= 0; i--) {
                Element el = layers.get(i).part().fill();
                if (el != null) {
                    fill = ShapeStyleExtractor.resolveFillChoice(el, slideCtx);
                    break;
                }
            }
        }

        Border left = resolveEdge(cell, layers, Side.LEFT, r, r + rs - 1, c, c + cs - 1,
            mapper, slideCtx);
        Border right = resolveEdge(cell, layers, Side.RIGHT, r, r + rs - 1, c, c + cs - 1,
            mapper, slideCtx);
        Border top = resolveEdge(cell, layers, Side.TOP, r, r + rs - 1, c, c + cs - 1,
            mapper, slideCtx);
        Border bottom = resolveEdge(cell, layers, Side.BOTTOM, r, r + rs - 1, c, c + cs - 1,
            mapper, slideCtx);

        // Text overlay: strongest layer wins per field.
        Boolean bold = null;
        TextColor color = null;
        String fontRefIdx = null;
        for (int i = layers.size() - 1; i >= 0; i--) {
            TableStyle.TextProps t = layers.get(i).part().text();
            if (t == null) continue;
            if (bold == null) bold = t.bold();
            if (color == null) color = t.color();
            if (fontRefIdx == null) fontRefIdx = t.fontRefIdx();
        }
        String fontFamily = null;
        if (slideCtx != null && fontRefIdx != null) {
            fontFamily = fontRefIdx.startsWith("major")
                ? slideCtx.getMajorFont() : slideCtx.getMinorFont();
        }
        TextStyleSource.LevelStyle overlay = null;
        if (bold != null || color != null || fontFamily != null) {
            overlay = new TextStyleSource.LevelStyle(null, bold, null, fontFamily, color,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        return new Resolved(r, c, rs, cs, cell, fill, left, right, top, bottom, overlay);
    }

    /**
     * The style parts applying to an anchor cell, weakest first, each
     * with its region bounds. Banding skips header/footer rows (and
     * first/last columns for column banding) and indexes from the first
     * data row/column, per the tables-basic truth.
     */
    private static List<Layer> layersFor(TableModel table, TableStyle style,
                                         int r, int c, int rs, int cs) {
        List<Layer> layers = new ArrayList<>(4);
        if (style == null) return layers;

        TableModel.Properties p = table.getProperties();
        int rows = table.getRowCount(), cols = table.getColumnCount();
        boolean inHeader = p.firstRow() && r == 0;
        boolean inFooter = p.lastRow() && r == rows - 1;
        boolean inFirstCol = p.firstCol() && c == 0;
        boolean inLastCol = p.lastCol() && c == cols - 1;

        if (style.wholeTbl() != null) {
            layers.add(new Layer(style.wholeTbl(), RANK_WHOLE, 0, rows - 1, 0, cols - 1));
        }
        if (p.bandCol() && !inFirstCol && !inLastCol) {
            int dc = c - (p.firstCol() ? 1 : 0);
            if (dc >= 0) {
                TableStyle.Part band = dc % 2 == 0 ? style.band1V() : style.band2V();
                if (band != null) {
                    layers.add(new Layer(band, RANK_BAND_COL, 0, rows - 1, c, c + cs - 1));
                }
            }
        }
        if (p.bandRow() && !inHeader && !inFooter) {
            int dr = r - (p.firstRow() ? 1 : 0);
            if (dr >= 0) {
                TableStyle.Part band = dr % 2 == 0 ? style.band1H() : style.band2H();
                if (band != null) {
                    layers.add(new Layer(band, RANK_BAND_ROW, r, r + rs - 1, 0, cols - 1));
                }
            }
        }
        if (inFirstCol && style.firstCol() != null) {
            layers.add(new Layer(style.firstCol(), RANK_EDGE_COL, 0, rows - 1, c, c + cs - 1));
        }
        if (inLastCol && style.lastCol() != null) {
            layers.add(new Layer(style.lastCol(), RANK_EDGE_COL, 0, rows - 1, c, cols - 1));
        }
        if (inHeader && style.firstRow() != null) {
            layers.add(new Layer(style.firstRow(), RANK_EDGE_ROW, 0, r + rs - 1, 0, cols - 1));
        }
        if (inFooter && style.lastRow() != null) {
            layers.add(new Layer(style.lastRow(), RANK_EDGE_ROW, r, rows - 1, 0, cols - 1));
        }
        return layers;
    }

    /**
     * Resolve one edge of a cell span: tcPr wins outright; then the
     * strongest layer that authors a border for that edge. An edge that
     * coincides with the layer's region boundary uses the part's outer
     * border for that side; an edge interior to the region uses
     * insideH/insideV. (This is what makes firstRow's BOTTOM apply at
     * the header/data boundary while its unset insideV falls through to
     * wholeTbl's 1pt line -- both pinned by the truth PDFs.)
     */
    private static Border resolveEdge(TableModel.Cell cell, List<Layer> layers, Side side,
                                      int rowStart, int rowEnd, int colStart, int colEnd,
                                      CoordinateMapper mapper, SlideRenderContext slideCtx) {
        Element tcLn = switch (side) {
            case LEFT -> cell.lnL();
            case RIGHT -> cell.lnR();
            case TOP -> cell.lnT();
            case BOTTOM -> cell.lnB();
        };
        if (tcLn != null) return borderFromLn(tcLn, RANK_TCPR, mapper, slideCtx);

        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer layer = layers.get(i);
            TableStyle.Borders b = layer.part().borders();
            if (b == null) continue;
            boolean regionEdge = switch (side) {
                case LEFT -> colStart == layer.colStart();
                case RIGHT -> colEnd == layer.colEnd();
                case TOP -> rowStart == layer.rowStart();
                case BOTTOM -> rowEnd == layer.rowEnd();
            };
            Element ln = switch (side) {
                case LEFT -> regionEdge ? b.left() : b.insideV();
                case RIGHT -> regionEdge ? b.right() : b.insideV();
                case TOP -> regionEdge ? b.top() : b.insideH();
                case BOTTOM -> regionEdge ? b.bottom() : b.insideH();
            };
            if (ln != null) return borderFromLn(ln, layer.rank(), mapper, slideCtx);
        }
        return null;
    }

    /**
     * An a:ln element (style tcBdr child or tcPr lnL..lnB) to a Border:
     * width @w (default 12700 EMU), solidFill color, noFill = explicit
     * none. A fill-less a:ln keeps the 1pt-black default the shape
     * outline path uses.
     */
    private static Border borderFromLn(Element ln, int rank, CoordinateMapper mapper,
                                       SlideRenderContext slideCtx) {
        double widthEmu = 12700;
        if (ln.hasAttribute("w")) {
            try {
                widthEmu = Double.parseDouble(ln.getAttribute("w"));
            } catch (NumberFormatException ignored) { }
        }
        double widthPx = mapper.mapDimensionToCanvas(Math.round(widthEmu));

        if (childByLocal(ln, "noFill") != null) {
            return new Border(0, null, rank);
        }
        Element solid = childByLocal(ln, "solidFill");
        SurfacePaint.Solid color = SurfacePaint.Solid.rgb(0, 0, 0);
        if (solid != null) {
            SurfacePaint paint = ShapeStyleExtractor.resolveFillChoice(solid, slideCtx);
            if (paint instanceof SurfacePaint.Solid s) color = s;
        }
        return new Border(widthPx, color, rank);
    }

    private static Border maxRank(Border a, Border b) {
        if (a == null) return b;
        if (b == null) return a;
        return b.rank() > a.rank() ? b : a;
    }

    // ====================================================================
    // Border stroking
    // ====================================================================

    private static void strokeVertical(RenderSurface surface, int b, Border[][] vWin,
                                       Border[][] hWin, double[] colPx, double[] rowPx,
                                       int rows, int cols) {
        int r = 0;
        while (r < rows) {
            Border w = vWin[b][r];
            if (w == null || !w.paints()) { r++; continue; }
            int r0 = r;
            while (r + 1 < rows && w.sameStroke(vWin[b][r + 1])) r++;
            double y0 = rowPx[r0] - halfCrossing(hWin, r0, b, cols);
            double y1 = rowPx[r + 1] + halfCrossing(hWin, r + 1, b, cols);
            strokeSegment(surface, w, colPx[b], y0, colPx[b], y1);
            r++;
        }
    }

    private static void strokeHorizontal(RenderSurface surface, int rb, Border[][] hWin,
                                         Border[][] vWin, double[] colPx, double[] rowPx,
                                         int rows, int cols) {
        int c = 0;
        while (c < cols) {
            Border w = hWin[rb][c];
            if (w == null || !w.paints()) { c++; continue; }
            int c0 = c;
            while (c + 1 < cols && w.sameStroke(hWin[rb][c + 1])) c++;
            double x0 = colPx[c0] - halfCrossing(vWin, c0, rb, rows);
            double x1 = colPx[c + 1] + halfCrossing(vWin, c + 1, rb, rows);
            strokeSegment(surface, w, x0, rowPx[rb], x1, rowPx[rb]);
            c++;
        }
    }

    /** Half the width of the crossing border at a junction (truth: the
     *  verticals under the 3pt header line start 1.5pt above it). */
    private static double halfCrossing(Border[][] crossing, int junction, int lane, int laneCount) {
        double w = 0;
        if (lane > 0) {
            Border x = crossing[junction][lane - 1];
            if (x != null && x.paints()) w = Math.max(w, x.widthPx());
        }
        if (lane < laneCount) {
            Border x = crossing[junction][Math.min(lane, laneCount - 1)];
            if (x != null && x.paints()) w = Math.max(w, x.widthPx());
        }
        return w / 2;
    }

    private static void strokeSegment(RenderSurface surface, Border border,
                                      double x0, double y0, double x1, double y1) {
        surface.setStroke(border.color());
        surface.setLineWidth(border.widthPx());
        surface.setLineCap(StrokeCap.BUTT);
        surface.setLineDashes((double[]) null);
        surface.beginPath();
        surface.moveTo(x0, y0);
        surface.lineTo(x1, y1);
        surface.strokePath();
    }

    // ====================================================================
    // Cell text
    // ====================================================================

    private static void paintCellText(Resolved cell, TableModel table, long[] colEmu,
                                      double[] colPx, double[] rowPx,
                                      RenderingContext ctx, SlideRenderContext slideCtx) {
        try {
            TextBody body = TextBodyExtractor.extract(cell.cell().txBody());
            if (body == null || body.getParagraphs().isEmpty()) return;
            body = withCellBodyOverrides(body, cell.cell());

            long widthEmu = colEmu[cell.c() + cell.cs()] - colEmu[cell.c()];

            // The a:tc element hosts the cell's own a:lstStyle (if any);
            // slideCtx supplies the presentation defaultTextStyle chain.
            Node tc = cell.cell().txBody().getParentNode();
            TextStyleSource base = LstStyleResolver.forShape(slideCtx, null, null,
                tc instanceof Element el ? el : null);
            TextStyleSource.LevelStyle overlay = cell.textOverlay();
            TextStyleSource styles = overlay == null ? base
                : level -> base.levelStyle(level).overlaidBy(overlay);

            MeasuredText measured = TextMeasurer.measure(body, widthEmu, styles);
            Rectangle2D bounds = new Rectangle2D(
                colPx[cell.c()], rowPx[cell.r()],
                colPx[cell.c() + cell.cs()] - colPx[cell.c()],
                rowPx[cell.r() + cell.rs()] - rowPx[cell.r()]);
            TextPainter.paint(body, measured, bounds, ctx, slideCtx, null, styles);
        } catch (Exception e) {
            // Non-critical -- the table renders, this cell's text doesn't.
        }
    }

    /**
     * tcPr margins map onto the body insets and tcPr anchor onto the
     * vertical alignment (the tcPr defaults equal the bodyPr defaults,
     * so absent attributes need no rebuild at all).
     */
    private static TextBody withCellBodyOverrides(TextBody body, TableModel.Cell cell) {
        if (cell.marLEmu() == null && cell.marREmu() == null && cell.marTEmu() == null
                && cell.marBEmu() == null && cell.anchor() == null) {
            return body;
        }
        BodyProperties old = body.getBodyProperties();
        BodyProperties.Builder nb = BodyProperties.builder();
        if (old != null) {
            if (old.getVerticalAlignment() != null) nb.verticalAlignment(old.getVerticalAlignment());
            if (old.getWrap() != null) nb.wrap(old.getWrap());
            if (old.getVerticalText() != null) nb.verticalText(old.getVerticalText());
            if (old.getAutofit() != null) nb.autofit(old.getAutofit());
            if (old.getFontScale() != null) nb.fontScale(old.getFontScale());
            if (old.getLnSpcReduction() != null) nb.lnSpcReduction(old.getLnSpcReduction());
            if (old.getLeftInset() != null) nb.leftInset(old.getLeftInset());
            if (old.getTopInset() != null) nb.topInset(old.getTopInset());
            if (old.getRightInset() != null) nb.rightInset(old.getRightInset());
            if (old.getBottomInset() != null) nb.bottomInset(old.getBottomInset());
            if (old.getNumColumns() != null) nb.numColumns(old.getNumColumns());
            nb.rtlCol(old.isRtlCol());
        }
        if (cell.marLEmu() != null) nb.leftInset(cell.marLEmu().intValue());
        if (cell.marREmu() != null) nb.rightInset(cell.marREmu().intValue());
        if (cell.marTEmu() != null) nb.topInset(cell.marTEmu().intValue());
        if (cell.marBEmu() != null) nb.bottomInset(cell.marBEmu().intValue());
        if (cell.anchor() != null) nb.verticalAlignment(cell.anchor());

        TextBody.Builder tb = TextBody.builder()
            .bodyProperties(nb.build())
            .placeholder(body.isPlaceholder());
        for (TextParagraph p : body.getParagraphs()) tb.addParagraph(p);
        return tb.build();
    }

    // ====================================================================
    // DOM helper
    // ====================================================================

    private static Element childByLocal(Element parent, String local) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el) {
                String name = el.getLocalName();
                if (name == null) {
                    String tag = el.getTagName();
                    int colon = tag.indexOf(':');
                    name = colon < 0 ? tag : tag.substring(colon + 1);
                }
                if (local.equals(name)) return el;
            }
        }
        return null;
    }
}
