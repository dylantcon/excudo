package com.excudo.core.model;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import java.util.List;

/**
 * Tests for TimingTree and TimingNode focusing on animation hierarchy behaviors:
 * - Click trigger detection logic ("indefinite" delay detection)
 * - Tree traversal algorithms (counting, collection)
 * - Hierarchy management and recursive operations
 * - PowerPoint animation timeline representation accuracy
 */
public class TimingTreeTest {
    
    private TimingTree tree;
    private TimingNode rootNode;
    
    @Before
    public void setUp() {
        tree = new TimingTree();
        rootNode = new TimingNode("1", "mainSeq", "indefinite");
    }
    
    // ===== Click Trigger Detection Tests =====
    
    @Test
    public void testClickTriggerDetection() {
        // Test the critical "indefinite" delay detection for click triggers
        TimingNode clickTrigger = new TimingNode("2", "seq", "0");
        clickTrigger.setDelay("indefinite");
        assertTrue("Node with 'indefinite' delay should be click trigger", clickTrigger.isClickTrigger());
        
        TimingNode normalNode = new TimingNode("3", "par", "330");
        normalNode.setDelay("0");
        assertFalse("Node with '0' delay should not be click trigger", normalNode.isClickTrigger());
        
        TimingNode timedNode = new TimingNode("4", "anim", "500");
        timedNode.setDelay("330");
        assertFalse("Node with numeric delay should not be click trigger", timedNode.isClickTrigger());
    }
    
    @Test
    public void testClickTriggerDetectionEdgeCases() {
        // Test edge cases for click trigger detection
        TimingNode nullDelayNode = new TimingNode("5", "seq", "0");
        nullDelayNode.setDelay(null);
        assertFalse("Node with null delay should not be click trigger", nullDelayNode.isClickTrigger());
        
        TimingNode emptyDelayNode = new TimingNode("6", "seq", "0");
        emptyDelayNode.setDelay("");
        assertFalse("Node with empty delay should not be click trigger", emptyDelayNode.isClickTrigger());
        
        TimingNode caseVariantNode = new TimingNode("7", "seq", "0");
        caseVariantNode.setDelay("INDEFINITE");
        assertFalse("Node with wrong case should not be click trigger (case sensitive)", caseVariantNode.isClickTrigger());
        
        TimingNode whitespaceNode = new TimingNode("8", "seq", "0");
        whitespaceNode.setDelay(" indefinite ");
        assertFalse("Node with whitespace around 'indefinite' should not be click trigger", whitespaceNode.isClickTrigger());
    }
    
    @Test
    public void testDefaultDelayBehavior() {
        // Test that new nodes have default delay and are not click triggers
        TimingNode defaultNode = new TimingNode("9", "seq", "0");
        assertNotNull("New node should have non-null delay", defaultNode.getDelay());
        assertEquals("New node should have '0' as default delay", "0", defaultNode.getDelay());
        assertFalse("New node with default delay should not be click trigger", defaultNode.isClickTrigger());
    }
    
    // ===== Tree Structure and Hierarchy Tests =====
    
    @Test
    public void testEmptyTreeBehavior() {
        TimingTree emptyTree = new TimingTree();
        
        assertNull("Empty tree should have null root", emptyTree.getRootNode());
        assertEquals("Empty tree should have zero nodes", 0, emptyTree.getNodeCount());
        
        List<TimingNode> nodes = emptyTree.getAllNodes();
        assertNotNull("getAllNodes should not return null", nodes);
        assertTrue("Empty tree should return empty node list", nodes.isEmpty());
        
        String treeString = emptyTree.toString();
        assertTrue("Empty tree toString should show 0 nodes", treeString.contains("0 nodes"));
    }
    
    @Test
    public void testSingleNodeTree() {
        tree.setRootNode(rootNode);
        
        assertEquals("Single node tree should have root node", rootNode, tree.getRootNode());
        assertEquals("Single node tree should have 1 node", 1, tree.getNodeCount());
        
        List<TimingNode> nodes = tree.getAllNodes();
        assertEquals("Single node tree should return list with 1 node", 1, nodes.size());
        assertEquals("Single node in list should be the root node", rootNode, nodes.get(0));
    }
    
    @Test
    public void testMultiLevelHierarchy() {
        // Build a realistic PowerPoint timing hierarchy:
        // Root (mainSeq)
        //   ├── Click 1 (indefinite delay)
        //   │   ├── Animation 1 (0ms delay)
        //   │   └── Animation 2 (330ms delay)
        //   └── Click 2 (indefinite delay)
        //       └── Animation 3 (0ms delay)
        
        tree.setRootNode(rootNode);
        
        // First click trigger
        TimingNode click1 = new TimingNode("2", "seq", "0");
        click1.setDelay("indefinite");
        rootNode.addChild(click1);
        
        // Animations under first click
        TimingNode anim1 = new TimingNode("3", "anim", "330");
        anim1.setDelay("0");
        click1.addChild(anim1);
        
        TimingNode anim2 = new TimingNode("4", "anim", "330");
        anim2.setDelay("330");
        click1.addChild(anim2);
        
        // Second click trigger
        TimingNode click2 = new TimingNode("5", "seq", "0");
        click2.setDelay("indefinite");
        rootNode.addChild(click2);
        
        // Animation under second click
        TimingNode anim3 = new TimingNode("6", "anim", "500");
        anim3.setDelay("0");
        click2.addChild(anim3);
        
        // Test tree structure
        assertEquals("Complex tree should have 6 total nodes", 6, tree.getNodeCount());
        
        List<TimingNode> allNodes = tree.getAllNodes();
        assertEquals("getAllNodes should return all 6 nodes", 6, allNodes.size());
        
        // Test click trigger identification
        assertTrue("First click node should be click trigger", click1.isClickTrigger());
        assertTrue("Second click node should be click trigger", click2.isClickTrigger());
        assertFalse("Animation nodes should not be click triggers", anim1.isClickTrigger());
        assertFalse("Animation nodes should not be click triggers", anim2.isClickTrigger());
        assertFalse("Animation nodes should not be click triggers", anim3.isClickTrigger());
    }
    
    // ===== Recursive Tree Operation Tests =====
    
    @Test
    public void testRecursiveNodeCounting() {
        // Test accuracy of recursive counting across deep hierarchies
        tree.setRootNode(rootNode);
        
        // Add multiple levels of nesting
        TimingNode level1 = new TimingNode("2", "seq", "0");
        rootNode.addChild(level1);
        
        TimingNode level2a = new TimingNode("3", "par", "0");
        TimingNode level2b = new TimingNode("4", "par", "0");
        level1.addChild(level2a);
        level1.addChild(level2b);
        
        TimingNode level3 = new TimingNode("5", "anim", "330");
        level2a.addChild(level3);
        
        // Should count: root(1) + level1(1) + level2a(1) + level2b(1) + level3(1) = 5
        assertEquals("Recursive counting should be accurate", 5, tree.getNodeCount());
        assertEquals("Root node counting should match tree counting", 5, rootNode.getTotalNodeCount());
    }
    
    @Test
    public void testRecursiveNodeCollection() {
        // Test that all nodes are collected in tree traversal order
        tree.setRootNode(rootNode);
        
        TimingNode child1 = new TimingNode("2", "seq", "0");
        TimingNode child2 = new TimingNode("3", "seq", "0");
        rootNode.addChild(child1);
        rootNode.addChild(child2);
        
        TimingNode grandchild = new TimingNode("4", "anim", "330");
        child1.addChild(grandchild);
        
        List<TimingNode> collected = tree.getAllNodes();
        
        assertEquals("Should collect all 4 nodes", 4, collected.size());
        
        // Test that root comes first (pre-order traversal)
        assertEquals("Root should be first in collection", rootNode, collected.get(0));
        
        // Test that all nodes are present
        assertTrue("Collection should contain child1", collected.contains(child1));
        assertTrue("Collection should contain child2", collected.contains(child2));
        assertTrue("Collection should contain grandchild", collected.contains(grandchild));
    }
    
    @Test
    public void testDeepHierarchyPerformance() {
        // Test with deep nesting to ensure recursive operations don't fail
        tree.setRootNode(rootNode);
        
        TimingNode current = rootNode;
        int depth = 100; // Create 100-level deep hierarchy
        
        for (int i = 0; i < depth; i++) {
            TimingNode child = new TimingNode(String.valueOf(i + 2), "seq", "0");
            current.addChild(child);
            current = child;
        }
        
        assertEquals("Deep hierarchy should count correctly", depth + 1, tree.getNodeCount());
        assertEquals("Deep hierarchy collection should have all nodes", 
                    depth + 1, tree.getAllNodes().size());
    }
    
    // ===== Node Children and Immutability Tests =====
    
    @Test
    public void testChildrenListImmutability() {
        // Test that getChildren() returns a copy, not the internal list
        TimingNode parent = new TimingNode("1", "seq", "0");
        TimingNode child = new TimingNode("2", "anim", "330");
        parent.addChild(child);
        
        List<TimingNode> children = parent.getChildren();
        assertEquals("Should have one child initially", 1, children.size());
        
        // Try to modify the returned list
        children.clear();
        
        // Original list should be unchanged
        assertEquals("Original children list should be unchanged", 1, parent.getChildren().size());
        assertEquals("Child should still be present", child, parent.getChildren().get(0));
    }
    
    @Test
    public void testMultipleChildrenHandling() {
        TimingNode parent = new TimingNode("1", "par", "0");
        
        // Add multiple children
        TimingNode child1 = new TimingNode("2", "anim", "330");
        TimingNode child2 = new TimingNode("3", "anim", "330");
        TimingNode child3 = new TimingNode("4", "anim", "330");
        
        parent.addChild(child1);
        parent.addChild(child2);
        parent.addChild(child3);
        
        List<TimingNode> children = parent.getChildren();
        assertEquals("Should have 3 children", 3, children.size());
        assertTrue("Should contain first child", children.contains(child1));
        assertTrue("Should contain second child", children.contains(child2));
        assertTrue("Should contain third child", children.contains(child3));
        
        // Test that order is preserved
        assertEquals("First child should be in first position", child1, children.get(0));
        assertEquals("Second child should be in second position", child2, children.get(1));
        assertEquals("Third child should be in third position", child3, children.get(2));
    }
    
    // ===== toString() and String Representation Tests =====
    
    @Test
    public void testTimingNodeToStringFormat() {
        TimingNode node = new TimingNode("42", "animEffect", "500");
        node.setDelay("330");
        
        String result = node.toString();
        assertTrue("toString should include node id", result.contains("id='42'"));
        assertTrue("toString should include node type", result.contains("type='animEffect'"));
        assertTrue("toString should include delay", result.contains("delay='330'"));
        assertTrue("toString should include children count", result.contains("children=0"));
    }
    
    @Test
    public void testTimingNodeToStringWithChildren() {
        TimingNode parent = new TimingNode("1", "seq", "0");
        parent.setDelay("indefinite");
        parent.addChild(new TimingNode("2", "anim", "330"));
        parent.addChild(new TimingNode("3", "anim", "500"));
        
        String result = parent.toString();
        assertTrue("toString should show correct children count", result.contains("children=2"));
        assertTrue("toString should show delay", result.contains("delay='indefinite'"));
    }
    
    @Test
    public void testTimingTreeToStringFormat() {
        tree.setRootNode(rootNode);
        rootNode.addChild(new TimingNode("2", "anim", "330"));
        
        String result = tree.toString();
        assertTrue("Tree toString should include node count", result.contains("2 nodes"));
        assertTrue("Tree toString should start with 'TimingTree{'", result.startsWith("TimingTree{"));
    }
    
    // ===== Real-world PowerPoint Scenario Tests =====
    
    @Test
    public void testTypicalPowerPointAnimationSequence() {
        // Simulate a typical PowerPoint slide with multiple click sequences:
        // Click 1: Title fades in
        // Click 2: Bullet points appear one by one with delays
        // Click 3: Chart animates in
        
        tree.setRootNode(rootNode);
        
        // Click 1: Title animation
        TimingNode titleClick = new TimingNode("2", "seq", "0");
        titleClick.setDelay("indefinite");
        rootNode.addChild(titleClick);
        
        TimingNode titleFade = new TimingNode("3", "animEffect", "330");
        titleFade.setDelay("0");
        titleClick.addChild(titleFade);
        
        // Click 2: Bullet points with staggered timing
        TimingNode bulletClick = new TimingNode("4", "seq", "0");
        bulletClick.setDelay("indefinite");
        rootNode.addChild(bulletClick);
        
        TimingNode bullet1 = new TimingNode("5", "animEffect", "330");
        bullet1.setDelay("0");
        bulletClick.addChild(bullet1);
        
        TimingNode bullet2 = new TimingNode("6", "animEffect", "330");
        bullet2.setDelay("330");
        bulletClick.addChild(bullet2);
        
        TimingNode bullet3 = new TimingNode("7", "animEffect", "330");
        bullet3.setDelay("660");
        bulletClick.addChild(bullet3);
        
        // Click 3: Chart animation
        TimingNode chartClick = new TimingNode("8", "seq", "0");
        chartClick.setDelay("indefinite");
        rootNode.addChild(chartClick);
        
        TimingNode chartAnim = new TimingNode("9", "animEffect", "1000");
        chartAnim.setDelay("0");
        chartClick.addChild(chartAnim);
        
        // Verify the structure
        assertEquals("Complete animation sequence should have 9 nodes", 9, tree.getNodeCount());
        
        // Count click triggers
        List<TimingNode> allNodes = tree.getAllNodes();
        long clickTriggers = allNodes.stream().filter(TimingNode::isClickTrigger).count();
        assertEquals("Should have 3 click triggers (title, bullets, chart)", 3, clickTriggers);
        
        // Verify click trigger nodes
        assertTrue("Title click should be click trigger", titleClick.isClickTrigger());
        assertTrue("Bullet click should be click trigger", bulletClick.isClickTrigger());
        assertTrue("Chart click should be click trigger", chartClick.isClickTrigger());
        
        // Verify animation nodes are not click triggers
        assertFalse("Title fade should not be click trigger", titleFade.isClickTrigger());
        assertFalse("Bullet animations should not be click triggers", bullet1.isClickTrigger());
        assertFalse("Chart animation should not be click trigger", chartAnim.isClickTrigger());
    }
    
    @Test
    public void testComplexNestedAnimationGroups() {
        // Test complex nesting with parallel and sequential groups
        tree.setRootNode(rootNode);
        
        TimingNode clickSeq = new TimingNode("2", "seq", "0");
        clickSeq.setDelay("indefinite");
        rootNode.addChild(clickSeq);
        
        // Parallel group for simultaneous animations
        TimingNode parallelGroup = new TimingNode("3", "par", "0");
        parallelGroup.setDelay("0");
        clickSeq.addChild(parallelGroup);
        
        // Multiple animations in parallel
        TimingNode anim1 = new TimingNode("4", "animEffect", "500");
        TimingNode anim2 = new TimingNode("5", "animEffect", "750");
        TimingNode anim3 = new TimingNode("6", "animEffect", "1000");
        
        parallelGroup.addChild(anim1);
        parallelGroup.addChild(anim2);
        parallelGroup.addChild(anim3);
        
        assertEquals("Complex nested structure should count all nodes correctly", 6, tree.getNodeCount());
        
        // Verify only the click sequence is a trigger
        List<TimingNode> allNodes = tree.getAllNodes();
        long clickTriggers = allNodes.stream().filter(TimingNode::isClickTrigger).count();
        assertEquals("Should have exactly 1 click trigger", 1, clickTriggers);
        assertTrue("Click sequence should be the trigger", clickSeq.isClickTrigger());
    }
    
    // ===== Advanced PowerPoint Timing Scenarios =====
    
    @Test
    public void testProgressiveDisclosurePattern() {
        // Test progressive disclosure - common PowerPoint pattern
        // Title appears, then content reveals incrementally
        tree.setRootNode(rootNode);
        
        // Title appears on click 1
        TimingNode titleClick = new TimingNode("2", "par", "0");
        titleClick.setDelay("indefinite");
        rootNode.addChild(titleClick);
        
        TimingNode titleAnim = new TimingNode("3", "animEffect", "500");
        titleAnim.setDelay("0");
        titleClick.addChild(titleAnim);
        
        // Content reveals on subsequent clicks with staggered timing
        TimingNode contentClick1 = new TimingNode("4", "par", "0");
        contentClick1.setDelay("indefinite");
        rootNode.addChild(contentClick1);
        
        TimingNode bullet1 = new TimingNode("5", "animEffect", "300");
        bullet1.setDelay("0");
        contentClick1.addChild(bullet1);
        
        TimingNode contentClick2 = new TimingNode("6", "par", "0");
        contentClick2.setDelay("indefinite");
        rootNode.addChild(contentClick2);
        
        TimingNode bullet2 = new TimingNode("7", "animEffect", "300");
        bullet2.setDelay("100"); // Slight delay for visual impact
        contentClick2.addChild(bullet2);
        
        assertEquals("Progressive disclosure should have 7 total nodes", 7, tree.getNodeCount());
        
        // Count click triggers (should be 3: title + 2 content clicks)
        List<TimingNode> allNodes = tree.getAllNodes();
        long clickTriggers = allNodes.stream().filter(TimingNode::isClickTrigger).count();
        assertEquals("Should have 3 click triggers for progressive disclosure", 3, clickTriggers);
    }
    
    // Helper method to count click triggers in a timing tree
    private int getClickTriggerCount(TimingTree tree) {
        if (tree.getRootNode() == null) {
            return 0;
        }
        int count = 0;
        List<TimingNode> allNodes = tree.getAllNodes();
        for (TimingNode node : allNodes) {
            if (node.isClickTrigger()) {
                count++;
            }
        }
        return count;
    }
    
    @Test
    public void testSimultaneousAnimationsWithTiming() {
        // Test simultaneous animations with precise timing coordination
        tree.setRootNode(rootNode);
        
        TimingNode clickTrigger = new TimingNode("2", "par", "0");
        clickTrigger.setDelay("indefinite");
        rootNode.addChild(clickTrigger);
        
        // Parallel container for simultaneous animations
        TimingNode parallelGroup = new TimingNode("3", "par", "0");
        parallelGroup.setDelay("0");
        clickTrigger.addChild(parallelGroup);
        
        // Multiple animations starting simultaneously but with different durations
        TimingNode quickFade = new TimingNode("4", "animEffect", "250");
        quickFade.setDelay("0");
        parallelGroup.addChild(quickFade);
        
        TimingNode mediumSlide = new TimingNode("5", "animEffect", "500");
        mediumSlide.setDelay("0");
        parallelGroup.addChild(mediumSlide);
        
        TimingNode slowZoom = new TimingNode("6", "animEffect", "1000");
        slowZoom.setDelay("0");
        parallelGroup.addChild(slowZoom);
        
        assertEquals("Simultaneous animation structure should have 6 nodes", 6, tree.getNodeCount());
        assertEquals("Should have exactly 1 click trigger", 1, getClickTriggerCount(tree));
        
        // Verify parallel structure
        List<TimingNode> parallelChildren = parallelGroup.getChildren();
        assertEquals("Parallel group should have 3 animation children", 3, parallelChildren.size());
        
        // All animations should start at the same time (delay="0")
        for (TimingNode animNode : parallelChildren) {
            assertEquals("All parallel animations should start immediately", "0", animNode.getDelay());
        }
    }
    
    @Test
    public void testChainedAnimationSequence() {
        // Test chained animations (each triggers the next)
        tree.setRootNode(rootNode);
        
        TimingNode clickTrigger = new TimingNode("2", "par", "0");
        clickTrigger.setDelay("indefinite");
        rootNode.addChild(clickTrigger);
        
        // Sequential container for chained animations
        TimingNode sequentialGroup = new TimingNode("3", "seq", "0");
        sequentialGroup.setDelay("0");
        clickTrigger.addChild(sequentialGroup);
        
        // Chain of animations, each starting when previous ends
        TimingNode anim1 = new TimingNode("4", "animEffect", "300");
        anim1.setDelay("0");
        sequentialGroup.addChild(anim1);
        
        TimingNode anim2 = new TimingNode("5", "animEffect", "400");
        anim2.setDelay("0"); // Starts when anim1 ends
        sequentialGroup.addChild(anim2);
        
        TimingNode anim3 = new TimingNode("6", "animEffect", "500");
        anim3.setDelay("0"); // Starts when anim2 ends
        sequentialGroup.addChild(anim3);
        
        assertEquals("Chained animation structure should have 6 nodes", 6, tree.getNodeCount());
        assertEquals("Should have exactly 1 click trigger", 1, getClickTriggerCount(tree));
        
        // Verify sequential structure
        List<TimingNode> sequentialChildren = sequentialGroup.getChildren();
        assertEquals("Sequential group should have 3 animation children", 3, sequentialChildren.size());
    }
    
    @Test
    public void testMixedTimingWithDelays() {
        // Test complex timing with mixed delays and triggers
        tree.setRootNode(rootNode);
        
        // Auto-start animation (no click required)
        TimingNode autoStart = new TimingNode("2", "par", "0");
        autoStart.setDelay("0"); // Starts immediately
        rootNode.addChild(autoStart);
        
        TimingNode autoAnim = new TimingNode("3", "animEffect", "500");
        autoAnim.setDelay("0");
        autoStart.addChild(autoAnim);
        
        // Click-triggered animation
        TimingNode clickAnim = new TimingNode("4", "par", "0");
        clickAnim.setDelay("indefinite");
        rootNode.addChild(clickAnim);
        
        TimingNode clickEffect = new TimingNode("5", "animEffect", "750");
        clickEffect.setDelay("200"); // 200ms delay after click
        clickAnim.addChild(clickEffect);
        
        // Timed animation (starts after specific delay)
        TimingNode timedAnim = new TimingNode("6", "par", "0");
        timedAnim.setDelay("1000"); // Starts 1 second after previous
        rootNode.addChild(timedAnim);
        
        TimingNode timedEffect = new TimingNode("7", "animEffect", "300");
        timedEffect.setDelay("0");
        timedAnim.addChild(timedEffect);
        
        assertEquals("Mixed timing structure should have 7 nodes", 7, tree.getNodeCount());
        
        // Only the click-triggered animation should be a click trigger
        List<TimingNode> allNodes = tree.getAllNodes();
        List<TimingNode> clickTriggers = allNodes.stream()
            .filter(TimingNode::isClickTrigger)
            .toList();
        
        assertEquals("Should have exactly 1 click trigger", 1, clickTriggers.size());
        assertEquals("Click trigger should be the clickAnim node", clickAnim, clickTriggers.get(0));
        
        // Verify other nodes are not click triggers
        assertFalse("Auto-start should not be click trigger", autoStart.isClickTrigger());
        assertFalse("Timed animation should not be click trigger", timedAnim.isClickTrigger());
    }
    
    @Test
    public void testNestedTimingContainers() {
        // Test deeply nested timing containers (realistic for complex slides)
        tree.setRootNode(rootNode);
        
        TimingNode mainClick = new TimingNode("2", "par", "0");
        mainClick.setDelay("indefinite");
        rootNode.addChild(mainClick);
        
        // Nested sequence within the click
        TimingNode outerSequence = new TimingNode("3", "seq", "0");
        outerSequence.setDelay("0");
        mainClick.addChild(outerSequence);
        
        // Parallel group within the sequence
        TimingNode parallelGroup = new TimingNode("4", "par", "0");
        parallelGroup.setDelay("0");
        outerSequence.addChild(parallelGroup);
        
        // Individual animations within parallel group
        TimingNode nestedAnim1 = new TimingNode("5", "animEffect", "400");
        nestedAnim1.setDelay("0");
        parallelGroup.addChild(nestedAnim1);
        
        TimingNode nestedAnim2 = new TimingNode("6", "animEffect", "600");
        nestedAnim2.setDelay("100");
        parallelGroup.addChild(nestedAnim2);
        
        // Sequential animation after parallel group
        TimingNode followUpAnim = new TimingNode("7", "animEffect", "300");
        followUpAnim.setDelay("0");
        outerSequence.addChild(followUpAnim);
        
        assertEquals("Nested structure should have 7 nodes", 7, tree.getNodeCount());
        assertEquals("Should have exactly 1 click trigger", 1, getClickTriggerCount(tree));
        
        // Test nested navigation
        List<TimingNode> mainClickChildren = mainClick.getChildren();
        assertEquals("Main click should have 1 child (outer sequence)", 1, mainClickChildren.size());
        
        List<TimingNode> sequenceChildren = outerSequence.getChildren();
        assertEquals("Outer sequence should have 2 children (parallel group + follow-up)", 2, sequenceChildren.size());
        
        List<TimingNode> parallelChildren = parallelGroup.getChildren();
        assertEquals("Parallel group should have 2 children", 2, parallelChildren.size());
    }
    
    @Test
    public void testBulletPointTimingPattern() {
        // Test typical bullet point timing pattern from real PowerPoint presentations
        tree.setRootNode(rootNode);
        
        // Each bullet point gets its own click trigger
        for (int i = 0; i < 4; i++) {
            TimingNode bulletClick = new TimingNode(String.valueOf(i + 2), "par", "0");
            bulletClick.setDelay("indefinite");
            rootNode.addChild(bulletClick);
            
            // Each bullet has a wipe animation with slight delay for visual appeal
            TimingNode bulletAnim = new TimingNode(String.valueOf(i + 6), "animEffect", "300");
            bulletAnim.setDelay(String.valueOf(i * 50)); // Staggered timing
            bulletClick.addChild(bulletAnim);
        }
        
        assertEquals("Bullet point pattern should have 9 nodes total", 9, tree.getNodeCount());
        assertEquals("Should have 4 click triggers (one per bullet)", 4, getClickTriggerCount(tree));
        
        // Verify each bullet has proper structure
        List<TimingNode> rootChildren = rootNode.getChildren();
        assertEquals("Root should have 4 bullet clicks", 4, rootChildren.size());
        
        for (int i = 0; i < 4; i++) {
            TimingNode bulletClick = rootChildren.get(i);
            assertTrue("Each bullet should be a click trigger", bulletClick.isClickTrigger());
            assertEquals("Each bullet should have 1 animation child", 1, bulletClick.getChildren().size());
        }
    }
    
    @Test
    public void testPresentationFlowPattern() {
        // Test complete presentation flow: intro -> content -> conclusion
        tree.setRootNode(rootNode);
        
        // Section 1: Introduction (auto-start)
        TimingNode introSection = new TimingNode("2", "seq", "0");
        introSection.setDelay("0"); // Auto-start
        rootNode.addChild(introSection);
        
        TimingNode titleReveal = new TimingNode("3", "animEffect", "800");
        titleReveal.setDelay("0");
        introSection.addChild(titleReveal);
        
        TimingNode subtitleReveal = new TimingNode("4", "animEffect", "600");
        subtitleReveal.setDelay("500"); // Appears after title
        introSection.addChild(subtitleReveal);
        
        // Section 2: Content (click-triggered)
        TimingNode contentSection = new TimingNode("5", "par", "0");
        contentSection.setDelay("indefinite");
        rootNode.addChild(contentSection);
        
        TimingNode contentReveal = new TimingNode("6", "seq", "0");
        contentReveal.setDelay("0");
        contentSection.addChild(contentReveal);
        
        // Multiple content pieces in sequence
        for (int i = 0; i < 3; i++) {
            TimingNode contentPart = new TimingNode(String.valueOf(i + 7), "animEffect", "400");
            contentPart.setDelay(String.valueOf(i * 200));
            contentReveal.addChild(contentPart);
        }
        
        // Section 3: Conclusion (click-triggered)
        TimingNode conclusionSection = new TimingNode("10", "par", "0");
        conclusionSection.setDelay("indefinite");
        rootNode.addChild(conclusionSection);
        
        TimingNode conclusionAnim = new TimingNode("11", "animEffect", "1000");
        conclusionAnim.setDelay("0");
        conclusionSection.addChild(conclusionAnim);
        
        assertEquals("Presentation flow should have 11 nodes", 11, tree.getNodeCount());
        assertEquals("Should have 2 click triggers (content + conclusion)", 2, getClickTriggerCount(tree));
        
        // Verify flow structure
        assertFalse("Introduction should be auto-start", introSection.isClickTrigger());
        assertTrue("Content should be click-triggered", contentSection.isClickTrigger());
        assertTrue("Conclusion should be click-triggered", conclusionSection.isClickTrigger());
    }
    
    // ===== Performance and Stress Tests =====
    
    @Test
    public void testLargeTimingStructurePerformance() {
        // Test with large timing structure to ensure performance
        tree.setRootNode(rootNode);
        
        TimingNode currentParent = rootNode;
        int totalNodes = 1; // Count root
        
        // Create 50 levels of nesting with multiple children each
        for (int level = 0; level < 20; level++) {
            TimingNode levelContainer = new TimingNode(String.valueOf(level + 2), "seq", "0");
            levelContainer.setDelay(level % 3 == 0 ? "indefinite" : "0");
            currentParent.addChild(levelContainer);
            totalNodes++;
            
            // Add 3 children at each level
            for (int child = 0; child < 3; child++) {
                TimingNode childNode = new TimingNode(
                    String.valueOf((level * 3) + child + 22), "animEffect", "300"
                );
                childNode.setDelay(String.valueOf(child * 100));
                levelContainer.addChild(childNode);
                totalNodes++;
            }
            
            currentParent = levelContainer;
        }
        
        assertEquals("Large structure should count correctly", totalNodes, tree.getNodeCount());
        
        // Performance test: getAllNodes should complete quickly
        long startTime = System.currentTimeMillis();
        List<TimingNode> allNodes = tree.getAllNodes();
        long endTime = System.currentTimeMillis();
        
        assertEquals("getAllNodes should return correct count", totalNodes, allNodes.size());
        assertTrue("getAllNodes should complete within 100ms", (endTime - startTime) < 100);
        
        // Count click triggers efficiently
        startTime = System.currentTimeMillis();
        long clickTriggers = allNodes.stream().filter(TimingNode::isClickTrigger).count();
        endTime = System.currentTimeMillis();
        
        assertTrue("Click trigger counting should complete quickly", (endTime - startTime) < 50);
        assertTrue("Should have some click triggers", clickTriggers > 0);
    }
}