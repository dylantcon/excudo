package com.excudo.core.metrics.math;

import com.excudo.core.model.math.MathBody;
import com.excudo.core.model.math.MathElement;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Pins {@link OmmlExtractor}: every supported OMML element shape ought
 * to round-trip through the extractor into a typed
 * {@link MathBody} / {@link MathElement} AST that preserves structure
 * (numerator vs denominator, base vs sub vs sup, n-ary operator vs
 * limits, etc.).
 *
 * <p>Tests use synthetic OMML fragments rather than real PowerPoint
 * files so the assertions stay focused -- one element per test.
 */
public class OmmlExtractorTest {

    private static final String OMML_NS = "http://schemas.openxmlformats.org/officeDocument/2006/math";

    @Test
    public void extractsPlainOMathAsInlineMathBody() throws Exception {
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:r><m:t>x</m:t></m:r>
              <m:r><m:t>+</m:t></m:r>
              <m:r><m:t>y</m:t></m:r>
            </m:oMath>
            """);

        assertFalse("inline math, not display", body.isDisplayMode());
        assertEquals(3, body.getElements().size());
        assertEquals("x", ((MathElement.Run) body.getElements().get(0)).text());
        assertEquals("y", ((MathElement.Run) body.getElements().get(2)).text());
    }

    @Test
    public void recognisesOMathParaAsDisplayMode() throws Exception {
        MathBody body = parseAndExtract("""
            <m:oMathPara xmlns:m='%s'>
              <m:oMath><m:r><m:t>z</m:t></m:r></m:oMath>
            </m:oMathPara>
            """);
        assertTrue(body.isDisplayMode());
        assertEquals(1, body.getElements().size());
    }

    @Test
    public void fractionPreservesNumeratorAndDenominator() throws Exception {
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:f>
                <m:num><m:r><m:t>a</m:t></m:r></m:num>
                <m:den><m:r><m:t>b</m:t></m:r></m:den>
              </m:f>
            </m:oMath>
            """);

        MathElement.Fraction f = (MathElement.Fraction) body.getElements().get(0);
        assertEquals(MathElement.FractionType.BAR, f.type());
        assertEquals("a", ((MathElement.Run) f.numerator().getElements().get(0)).text());
        assertEquals("b", ((MathElement.Run) f.denominator().getElements().get(0)).text());
    }

    @Test
    public void fractionTypeReadFromProperties() throws Exception {
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:f>
                <m:fPr><m:type m:val='skw'/></m:fPr>
                <m:num><m:r><m:t>1</m:t></m:r></m:num>
                <m:den><m:r><m:t>2</m:t></m:r></m:den>
              </m:f>
            </m:oMath>
            """);
        MathElement.Fraction f = (MathElement.Fraction) body.getElements().get(0);
        assertEquals(MathElement.FractionType.SKEWED, f.type());
    }

    @Test
    public void radicalWithoutDegreeIsPlainSqrt() throws Exception {
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:rad>
                <m:radPr><m:degHide m:val='1'/></m:radPr>
                <m:deg/>
                <m:e><m:r><m:t>x</m:t></m:r></m:e>
              </m:rad>
            </m:oMath>
            """);
        MathElement.Radical r = (MathElement.Radical) body.getElements().get(0);
        assertNull("degHide=true means plain sqrt", r.degree());
        assertEquals("x", ((MathElement.Run) r.base().getElements().get(0)).text());
    }

    @Test
    public void radicalWithDegreeIsNthRoot() throws Exception {
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:rad>
                <m:deg><m:r><m:t>3</m:t></m:r></m:deg>
                <m:e><m:r><m:t>x</m:t></m:r></m:e>
              </m:rad>
            </m:oMath>
            """);
        MathElement.Radical r = (MathElement.Radical) body.getElements().get(0);
        assertNotNull(r.degree());
        assertEquals("3", ((MathElement.Run) r.degree().getElements().get(0)).text());
    }

    @Test
    public void subSuperscriptKeepsBothScripts() throws Exception {
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:sSubSup>
                <m:e><m:r><m:t>x</m:t></m:r></m:e>
                <m:sub><m:r><m:t>i</m:t></m:r></m:sub>
                <m:sup><m:r><m:t>2</m:t></m:r></m:sup>
              </m:sSubSup>
            </m:oMath>
            """);
        MathElement.SubSuperscript ss = (MathElement.SubSuperscript) body.getElements().get(0);
        assertEquals("x", ((MathElement.Run) ss.base().getElements().get(0)).text());
        assertEquals("i", ((MathElement.Run) ss.sub().getElements().get(0)).text());
        assertEquals("2", ((MathElement.Run) ss.sup().getElements().get(0)).text());
    }

    @Test
    public void naryReadsOperatorAndLimits() throws Exception {
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:nary>
                <m:naryPr><m:chr m:val='∑'/></m:naryPr>
                <m:sub><m:r><m:t>i=0</m:t></m:r></m:sub>
                <m:sup><m:r><m:t>n</m:t></m:r></m:sup>
                <m:e><m:r><m:t>i</m:t></m:r></m:e>
              </m:nary>
            </m:oMath>
            """);
        MathElement.Nary n = (MathElement.Nary) body.getElements().get(0);
        assertEquals("∑", n.op());
        assertEquals("i=0", ((MathElement.Run) n.sub().getElements().get(0)).text());
        assertEquals("n", ((MathElement.Run) n.sup().getElements().get(0)).text());
        assertEquals("i", ((MathElement.Run) n.base().getElements().get(0)).text());
    }

    @Test
    public void naryHidesSubAndSupWhenFlagsSet() throws Exception {
        // The integral uses subHide / supHide to suppress the limit
        // slots when only the base is rendered.
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:nary>
                <m:naryPr>
                  <m:chr m:val='∫'/>
                  <m:subHide m:val='1'/>
                  <m:supHide m:val='1'/>
                </m:naryPr>
                <m:sub><m:r><m:t>0</m:t></m:r></m:sub>
                <m:sup><m:r><m:t>1</m:t></m:r></m:sup>
                <m:e><m:r><m:t>x</m:t></m:r></m:e>
              </m:nary>
            </m:oMath>
            """);
        MathElement.Nary n = (MathElement.Nary) body.getElements().get(0);
        assertEquals("∫", n.op());
        assertTrue(n.properties().hideSub());
        assertTrue(n.properties().hideSup());
        assertTrue("hidden sub yields empty body", n.sub().isEmpty());
        assertTrue("hidden sup yields empty body", n.sup().isEmpty());
    }

    @Test
    public void delimiterReadsBeginEndCharacters() throws Exception {
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:d>
                <m:dPr>
                  <m:begChr m:val='['/>
                  <m:endChr m:val=']'/>
                </m:dPr>
                <m:e><m:r><m:t>z</m:t></m:r></m:e>
              </m:d>
            </m:oMath>
            """);
        MathElement.Delimiter d = (MathElement.Delimiter) body.getElements().get(0);
        assertEquals("[", d.beginChar());
        assertEquals("]", d.endChar());
        assertEquals(1, d.elements().size());
    }

    @Test
    public void delimiterDefaultsToParensWhenNoProps() throws Exception {
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:d>
                <m:e><m:r><m:t>q</m:t></m:r></m:e>
              </m:d>
            </m:oMath>
            """);
        MathElement.Delimiter d = (MathElement.Delimiter) body.getElements().get(0);
        assertEquals("(", d.beginChar());
        assertEquals(")", d.endChar());
    }

    @Test
    public void mathRunStyleNorMakesUprightText() throws Exception {
        // <m:nor/> opts a run out of the default math italic.
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:r>
                <m:rPr><m:nor/></m:rPr>
                <m:t>sin</m:t>
              </m:r>
            </m:oMath>
            """);
        MathElement.Run r = (MathElement.Run) body.getElements().get(0);
        assertEquals("sin", r.text());
        assertFalse("nor flag forces upright", r.style().italic());
    }

    @Test
    public void mathRunStyleMVal() throws Exception {
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:r>
                <m:rPr><m:sty m:val='b'/></m:rPr>
                <m:t>F</m:t>
              </m:r>
            </m:oMath>
            """);
        MathElement.Run r = (MathElement.Run) body.getElements().get(0);
        // sty=b means upright bold (vector / matrix convention).
        assertFalse(r.style().italic());
        assertTrue(r.style().bold());
    }

    @Test
    public void mathRunDoubleStruckScript() throws Exception {
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:r>
                <m:rPr><m:scr m:val='double-struck'/></m:rPr>
                <m:t>R</m:t>
              </m:r>
            </m:oMath>
            """);
        MathElement.Run r = (MathElement.Run) body.getElements().get(0);
        assertEquals(MathElement.ScriptVariant.DOUBLE_STRUCK, r.style().variant());
    }

    @Test
    public void unsupportedElementsFlattenAsRuns() throws Exception {
        // Matrices aren't natively modelled yet -- they fall back to a
        // flat-text Run that concatenates every <m:t> in the subtree.
        // The formula stays readable; native layout will replace this
        // path when the matrix Element is added to the sealed
        // hierarchy.
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:m>
                <m:mr>
                  <m:e><m:r><m:t>1</m:t></m:r></m:e>
                  <m:e><m:r><m:t>2</m:t></m:r></m:e>
                </m:mr>
              </m:m>
            </m:oMath>
            """);
        MathElement.Run r = (MathElement.Run) body.getElements().get(0);
        assertEquals("12", r.text());
    }

    @Test
    public void mixedRunsAndStructuredElements() throws Exception {
        // The formula 'a + b/c' as a single oMath: text run + plus
        // operator + fraction.
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:r><m:t>a</m:t></m:r>
              <m:r><m:t>+</m:t></m:r>
              <m:f>
                <m:num><m:r><m:t>b</m:t></m:r></m:num>
                <m:den><m:r><m:t>c</m:t></m:r></m:den>
              </m:f>
            </m:oMath>
            """);
        List<MathElement> elements = body.getElements();
        assertEquals(3, elements.size());
        assertTrue(elements.get(0) instanceof MathElement.Run);
        assertTrue(elements.get(1) instanceof MathElement.Run);
        assertTrue(elements.get(2) instanceof MathElement.Fraction);
    }

    @Test
    public void barOnTopProducesOverbar() throws Exception {
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:bar>
                <m:barPr><m:pos m:val='top'/></m:barPr>
                <m:e><m:r><m:t>X</m:t></m:r></m:e>
              </m:bar>
            </m:oMath>
            """);
        MathElement.Bar b = (MathElement.Bar) body.getElements().get(0);
        assertEquals(MathElement.BarPosition.TOP, b.position());
    }

    @Test
    public void accentReadsCharacter() throws Exception {
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:acc>
                <m:accPr><m:chr m:val='̂'/></m:accPr>
                <m:e><m:r><m:t>v</m:t></m:r></m:e>
              </m:acc>
            </m:oMath>
            """);
        MathElement.Accent a = (MathElement.Accent) body.getElements().get(0);
        assertEquals("̂", a.accentChar());
    }

    @Test
    public void functionApplicationKeepsNameAndArgument() throws Exception {
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:func>
                <m:fName><m:r><m:rPr><m:nor/></m:rPr><m:t>sin</m:t></m:r></m:fName>
                <m:e><m:r><m:t>x</m:t></m:r></m:e>
              </m:func>
            </m:oMath>
            """);
        MathElement.Function f = (MathElement.Function) body.getElements().get(0);
        assertEquals("sin", ((MathElement.Run) f.name().getElements().get(0)).text());
        assertEquals("x", ((MathElement.Run) f.argument().getElements().get(0)).text());
    }

    @Test
    public void limitLowerKeepsBaseAndLimit() throws Exception {
        MathBody body = parseAndExtract("""
            <m:oMath xmlns:m='%s'>
              <m:limLow>
                <m:e><m:r><m:rPr><m:nor/></m:rPr><m:t>lim</m:t></m:r></m:e>
                <m:lim><m:r><m:t>x→0</m:t></m:r></m:lim>
              </m:limLow>
            </m:oMath>
            """);
        MathElement.LimitLower l = (MathElement.LimitLower) body.getElements().get(0);
        assertEquals("lim", ((MathElement.Run) l.base().getElements().get(0)).text());
        assertEquals("x→0", ((MathElement.Run) l.limit().getElements().get(0)).text());
    }

    @Test
    public void nullInputReturnsNull() {
        assertNull(OmmlExtractor.extract(null));
    }

    @Test
    public void nonMathRootReturnsNull() throws Exception {
        Document d = parse("<x/>");
        assertNull(OmmlExtractor.extract(d.getDocumentElement()));
    }

    private MathBody parseAndExtract(String xmlTemplate) throws Exception {
        String xml = String.format(xmlTemplate, OMML_NS);
        Document doc = parse(xml);
        return OmmlExtractor.extract(doc.getDocumentElement());
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
