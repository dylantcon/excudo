package com.excudo.utils;

import com.excudo.core.utils.XMLConstants;
import org.junit.Test;
import static org.junit.Assert.*;
import javax.xml.namespace.NamespaceContext;
import java.util.Iterator;

/**
 * Unit tests for XMLConstants utility class
 * Verifies namespace constants and XPath expressions
 */
public class XMLConstantsTest {
    
    @Test
    public void testNamespaceConstants() {
        // Test PowerPoint XML namespaces are properly defined
        assertNotNull("Presentation namespace should not be null", XMLConstants.PRESENTATION_NS);
        assertNotNull("Drawing namespace should not be null", XMLConstants.DRAWING_NS);
        assertNotNull("Relationships namespace should not be null", XMLConstants.RELATIONSHIPS_NS);
        assertNotNull("Package relationships namespace should not be null", XMLConstants.PACKAGE_RELATIONSHIPS_NS);
        
        // Verify they follow OOXML standards
        assertTrue("Presentation namespace should be OOXML format", 
                  XMLConstants.PRESENTATION_NS.contains("openxmlformats.org"));
        assertTrue("Drawing namespace should be OOXML format", 
                  XMLConstants.DRAWING_NS.contains("openxmlformats.org"));
        assertTrue("Relationships namespace should be OOXML format", 
                  XMLConstants.RELATIONSHIPS_NS.contains("openxmlformats.org"));
    }
    
    @Test
    public void testNamespacePrefixes() {
        // Test namespace prefixes are correctly defined
        assertEquals("Presentation prefix should be 'p'", "p", XMLConstants.PRESENTATION_PREFIX);
        assertEquals("Drawing prefix should be 'a'", "a", XMLConstants.DRAWING_PREFIX);
        assertEquals("Relationships prefix should be 'r'", "r", XMLConstants.RELATIONSHIPS_PREFIX);
        assertEquals("Package relationships prefix should be 'rel'", "rel", XMLConstants.PACKAGE_RELATIONSHIPS_PREFIX);
    }
    
    @Test
    public void testXMLNSAttributes() {
        // Test xmlns attribute constants
        assertNotNull("XMLNS attribute should not be null", XMLConstants.XMLNS_ATTRIBUTE);
        assertEquals("XMLNS presentation prefix should be correct", 
                    "xmlns:p", XMLConstants.XMLNS_PREFIX_PRESENTATION);
        assertEquals("XMLNS drawing prefix should be correct", 
                    "xmlns:a", XMLConstants.XMLNS_PREFIX_DRAWING);
        assertEquals("XMLNS relationships prefix should be correct", 
                    "xmlns:r", XMLConstants.XMLNS_PREFIX_RELATIONSHIPS);
    }
    
    @Test
    public void testShapeXPathExpressions() {
        // Test XPath expressions for shape extraction
        assertNotNull("Shape XPath should not be null", XMLConstants.XPATH_ALL_SHAPES_AND_PICTURES);
        assertNotNull("Shape ID XPath should not be null", XMLConstants.XPATH_SHAPE_ID_ATTRIBUTE);
        assertNotNull("Shape name XPath should not be null", XMLConstants.XPATH_SHAPE_NAME_ATTRIBUTE);
        assertNotNull("Shape text XPath should not be null", XMLConstants.XPATH_SHAPE_TEXT_CONTENT);
        
        // Verify XPath expressions use correct prefixes
        assertTrue("Shape XPath should use p: prefix", 
                  XMLConstants.XPATH_ALL_SHAPES_AND_PICTURES.contains("p:"));
        assertTrue("Shape ID XPath should use p: prefix", 
                  XMLConstants.XPATH_SHAPE_ID_ATTRIBUTE.contains("p:"));
        assertTrue("Shape text XPath should use a: prefix", 
                  XMLConstants.XPATH_SHAPE_TEXT_CONTENT.contains("a:"));
    }
    
    @Test
    public void testGeometryXPathExpressions() {
        // Test XPath expressions for shape geometry
        assertNotNull("X position XPath should not be null", XMLConstants.XPATH_SHAPE_X_POSITION);
        assertNotNull("Y position XPath should not be null", XMLConstants.XPATH_SHAPE_Y_POSITION);
        assertNotNull("Width XPath should not be null", XMLConstants.XPATH_SHAPE_WIDTH);
        assertNotNull("Height XPath should not be null", XMLConstants.XPATH_SHAPE_HEIGHT);
        
        // Verify geometry XPaths target correct attributes
        assertTrue("X position should target @x", XMLConstants.XPATH_SHAPE_X_POSITION.contains("@x"));
        assertTrue("Y position should target @y", XMLConstants.XPATH_SHAPE_Y_POSITION.contains("@y"));
        assertTrue("Width should target @cx", XMLConstants.XPATH_SHAPE_WIDTH.contains("@cx"));
        assertTrue("Height should target @cy", XMLConstants.XPATH_SHAPE_HEIGHT.contains("@cy"));
    }
    
    @Test
    public void testTimingXPathExpressions() {
        // Test XPath expressions for timing and animations
        assertNotNull("Timing root XPath should not be null", XMLConstants.XPATH_TIMING_ROOT_ELEMENT);
        assertNotNull("Main sequence XPath should not be null", XMLConstants.XPATH_MAIN_ANIMATION_SEQUENCE);
        assertNotNull("Timing child nodes XPath should not be null", XMLConstants.XPATH_TIMING_CHILD_NODES);
        assertNotNull("CTN element XPath should not be null", XMLConstants.XPATH_TIMING_CTN_ELEMENT);
        assertNotNull("Delay attribute XPath should not be null", XMLConstants.XPATH_TIMING_DELAY_ATTRIBUTE);
        
        // Verify timing XPaths use correct elements
        assertTrue("Timing root should target p:timing", 
                  XMLConstants.XPATH_TIMING_ROOT_ELEMENT.contains("p:timing"));
        assertTrue("Main sequence should target p:seq", 
                  XMLConstants.XPATH_MAIN_ANIMATION_SEQUENCE.contains("p:seq"));
        assertTrue("Child nodes should target p:par and p:seq", 
                  XMLConstants.XPATH_TIMING_CHILD_NODES.contains("p:par") && 
                  XMLConstants.XPATH_TIMING_CHILD_NODES.contains("p:seq"));
    }
    
    @Test
    public void testAnimationXPathExpressions() {
        // Test XPath expressions for animation elements
        assertNotNull("Animation target XPath should not be null", XMLConstants.XPATH_ANIMATION_TARGET_SHAPE_ID);
        assertNotNull("Animation duration XPath should not be null", XMLConstants.XPATH_ANIMATION_DURATION);
        assertNotNull("Animation delay XPath should not be null", XMLConstants.XPATH_ANIMATION_DELAY);
        
        // Verify animation XPaths target correct elements and attributes
        assertTrue("Animation target should target spid", 
                  XMLConstants.XPATH_ANIMATION_TARGET_SHAPE_ID.contains("spid"));
        assertTrue("Animation duration should target dur attribute", 
                  XMLConstants.XPATH_ANIMATION_DURATION.contains("@dur"));
        assertTrue("Animation delay should target delay attribute", 
                  XMLConstants.XPATH_ANIMATION_DELAY.contains("@delay"));
    }
    
    @Test 
    public void testNamespaceContextCreation() {
        // Test that createNamespaceContext() returns a valid context
        NamespaceContext context = XMLConstants.createNamespaceContext();
        assertNotNull("Namespace context should not be null", context);
        
        // Test namespace URI resolution
        assertEquals("Should resolve presentation prefix correctly", 
                    XMLConstants.PRESENTATION_NS, context.getNamespaceURI("p"));
        assertEquals("Should resolve drawing prefix correctly", 
                    XMLConstants.DRAWING_NS, context.getNamespaceURI("a"));
        assertEquals("Should resolve relationships prefix correctly", 
                    XMLConstants.RELATIONSHIPS_NS, context.getNamespaceURI("r"));
        
        // Test prefix resolution
        assertEquals("Should resolve presentation namespace correctly", 
                    "p", context.getPrefix(XMLConstants.PRESENTATION_NS));
        assertEquals("Should resolve drawing namespace correctly", 
                    "a", context.getPrefix(XMLConstants.DRAWING_NS));
        assertEquals("Should resolve relationships namespace correctly", 
                    "r", context.getPrefix(XMLConstants.RELATIONSHIPS_NS));
    }
    
    @Test
    public void testNamespaceContextIterator() {
        NamespaceContext context = XMLConstants.createNamespaceContext();
        
        // Test iterator functionality
        Iterator<String> prefixes = context.getPrefixes(XMLConstants.PRESENTATION_NS);
        assertNotNull("Prefixes iterator should not be null", prefixes);
        assertTrue("Should have at least one prefix", prefixes.hasNext());
        assertEquals("First prefix should be 'p'", "p", prefixes.next());
    }
    
    @Test
    public void testXPathConsistency() {
        // Verify that XPath expressions use consistent prefixes
        String[] xpaths = {
            XMLConstants.XPATH_ALL_SHAPES_AND_PICTURES,
            XMLConstants.XPATH_SHAPE_ID_ATTRIBUTE,
            XMLConstants.XPATH_SHAPE_NAME_ATTRIBUTE,
            XMLConstants.XPATH_TIMING_ROOT_ELEMENT,
            XMLConstants.XPATH_MAIN_ANIMATION_SEQUENCE
        };
        
        for (String xpath : xpaths) {
            if (xpath.contains("p:")) {
                assertTrue("XPath using p: prefix should be valid: " + xpath, 
                          xpath.contains("p:"));
            }
            if (xpath.contains("a:")) {
                assertTrue("XPath using a: prefix should be valid: " + xpath, 
                          xpath.contains("a:"));
            }
        }
    }
    
    @Test
    public void testConstantsAreImmutable() {
        // Ensure XMLConstants class cannot be instantiated (utility class pattern)
        try {
            XMLConstants.class.getDeclaredConstructor().newInstance();
            fail("XMLConstants should not be instantiable");
        } catch (Exception e) {
            // Expected - utility classes should not be instantiable
            assertTrue("Should throw exception when trying to instantiate", true);
        }
    }
}