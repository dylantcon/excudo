package com.excudo.core.geometry;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.SlideShape;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class ShapeSelectorTest {

    private ShapeRegistry registry;

    @Before
    public void setUp() {
        registry = new ShapeRegistry();
        // spid 1 = group shape container (excluded by "all")
        registry.addShape(new SlideShape(1, "Group", SlideShape.ShapeType.GROUP, null,
            new ShapeGeometry(0, 0, 0, 0), null));
        registry.addShape(new SlideShape(2, "Title 1", SlideShape.ShapeType.RECTANGLE, "Hello",
            new ShapeGeometry(100, 100, 500, 200), null));
        registry.addShape(new SlideShape(3, "Subtitle", SlideShape.ShapeType.RECTANGLE, "World",
            new ShapeGeometry(200, 300, 500, 200), null));
        registry.addShape(new SlideShape(4, "Circle", SlideShape.ShapeType.ELLIPSE, null,
            new ShapeGeometry(300, 100, 300, 300), null));
        registry.addShape(new SlideShape(5, "Star", SlideShape.ShapeType.STAR_5_POINTS, "Wow",
            new ShapeGeometry(600, 200, 200, 200), null));
    }

    @Test
    public void testExplicitSpids() {
        List<SlideShape> result = ShapeSelector.resolve("2,3", registry);
        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getSpid());
        assertEquals(3, result.get(1).getSpid());
    }

    @Test
    public void testAllSelector() {
        List<SlideShape> result = ShapeSelector.resolve("all", registry);
        assertEquals(4, result.size()); // excludes spid 1 (group)
    }

    @Test
    public void testTypeSelector() {
        List<SlideShape> result = ShapeSelector.resolve("type:rectangle", registry);
        assertEquals(2, result.size());
    }

    @Test
    public void testTypeUnion() {
        List<SlideShape> result = ShapeSelector.resolve("type:rectangle,type:ellipse", registry);
        assertEquals(3, result.size());
    }

    @Test
    public void testTextSelector() {
        List<SlideShape> result = ShapeSelector.resolve("text:*", registry);
        assertEquals(3, result.size()); // spids 2, 3, 5
    }

    @Test
    public void testNameGlob() {
        List<SlideShape> result = ShapeSelector.resolve("name:Title*", registry);
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getSpid());
    }

    @Test
    public void testMixedSelector() {
        List<SlideShape> result = ShapeSelector.resolve("4,type:rectangle", registry);
        assertEquals(3, result.size()); // spids 2, 3, 4
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoMatchThrows() {
        ShapeSelector.resolve("type:picture", registry);
    }

    @Test
    public void testDeduplication() {
        List<SlideShape> result = ShapeSelector.resolve("2,2,3", registry);
        assertEquals(2, result.size());
    }

    @Test
    public void testSingleSpid() {
        List<SlideShape> result = ShapeSelector.resolve("5", registry);
        assertEquals(1, result.size());
        assertEquals(5, result.get(0).getSpid());
    }
}
