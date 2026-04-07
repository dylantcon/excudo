package com.excudo.core.validation;

import com.excudo.core.utils.XMLFactoryProvider;
import org.w3c.dom.*;
import javax.xml.xpath.*;
import java.util.*;

/**
 * Validates OOXML animation structures against PowerPoint requirements
 * and MS-OE376 Section 4.6.16 constraints.
 */
public class OOXMLAnimationValidator {

    private final XPath xpath;

    public OOXMLAnimationValidator() {
        this.xpath = XMLFactoryProvider.createXPath();
    }

    /**
     * Validate animation structure completeness
     */
    public ValidationResult validateAnimationStructure(Document slideDoc) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try {
            // Check for timing element
            Element timing = (Element) xpath.evaluate("//p:timing", slideDoc, XPathConstants.NODE);
            if (timing == null) {
                return ValidationResult.success(); // No animations, valid
            }

            // Validate timing node IDs are unique
            validateUniqueTimingNodeIds(slideDoc, errors);

            // Validate animation effects have proper structure
            NodeList animEffects = (NodeList) xpath.evaluate("//p:animEffect", slideDoc, XPathConstants.NODESET);
            for (int i = 0; i < animEffects.getLength(); i++) {
                Element animEffect = (Element) animEffects.item(i);
                validateAnimationEffect(animEffect, errors, warnings);
            }

            // Validate build list entries match animations
            validateBuildList(slideDoc, errors, warnings);

            // Validate visibility controls for entrance/exit animations
            validateVisibilityControls(slideDoc, errors, warnings);

            // MS-OE376 4.6.16(b): build list entry uniqueness
            validateBuildListUniqueness(slideDoc, errors);

            // MS-OE376 4.6.16(a): animation targets must reference existing shapes
            validateShapeReferences(slideDoc, errors);

            // MS-OE376 cTn constraints: fill attribute values
            validateFillAttributeValues(slideDoc, errors);

            // MS-OE376 4.6.56(a): pRg st and end should be equal
            validateParagraphRangeEquality(slideDoc, warnings);

            // MS-OE376 4.6.4: p:animMotion structural requirements
            validateMotionPathAnimations(slideDoc, errors, warnings);

        } catch (XPathExpressionException e) {
            errors.add("Failed to validate animation structure: " + e.getMessage());
        }

        if (errors.isEmpty() && warnings.isEmpty()) {
            return ValidationResult.success();
        } else if (errors.isEmpty()) {
            return ValidationResult.successWithWarnings(warnings);
        } else {
            ValidationResult.Builder builder = ValidationResult.builder();
            builder.addErrors(errors);
            for (String warning : warnings) {
                builder.addWarning(warning);
            }
            return builder.build();
        }
    }

    private void validateUniqueTimingNodeIds(Document doc, List<String> errors) throws XPathExpressionException {
        NodeList timingIds = (NodeList) xpath.evaluate("//p:cTn/@id", doc, XPathConstants.NODESET);
        Set<String> seenIds = new HashSet<>();

        for (int i = 0; i < timingIds.getLength(); i++) {
            String id = timingIds.item(i).getTextContent();
            if (seenIds.contains(id)) {
                errors.add("Duplicate timing node ID found: " + id);
            }
            seenIds.add(id);
        }
    }

    private void validateAnimationEffect(Element animEffect, List<String> errors, List<String> warnings)
            throws XPathExpressionException {
        // Check transition attribute
        String transition = animEffect.getAttribute("transition");
        if (transition.isEmpty()) {
            errors.add("Animation effect missing transition attribute");
        } else if (!transition.equals("in") && !transition.equals("out")) {
            warnings.add("Animation transition '" + transition + "' may not be standard");
        }

        // Check for cBhvr child
        Element cBhvr = (Element) xpath.evaluate("./p:cBhvr", animEffect, XPathConstants.NODE);
        if (cBhvr == null) {
            errors.add("Animation effect missing cBhvr element");
            return;
        }

        // Check for target element
        Element tgtEl = (Element) xpath.evaluate("./p:tgtEl", cBhvr, XPathConstants.NODE);
        if (tgtEl == null) {
            errors.add("Animation behavior missing target element");
            return;
        }

        // Check for spTgt
        Element spTgt = (Element) xpath.evaluate("./p:spTgt", tgtEl, XPathConstants.NODE);
        if (spTgt == null) {
            errors.add("Animation target missing spTgt element");
        } else {
            // Validate spid attribute
            String spid = spTgt.getAttribute("spid");
            if (spid.isEmpty()) {
                errors.add("Animation spTgt missing spid attribute");
            }

            // Check for txEl (text element targeting)
            Element txEl = (Element) xpath.evaluate("./p:txEl", spTgt, XPathConstants.NODE);
            if (txEl == null) {
                warnings.add("Animation target missing txEl for text targeting");
            }
        }
    }

    private void validateBuildList(Document doc, List<String> errors, List<String> warnings)
            throws XPathExpressionException {
        Element bldLst = (Element) xpath.evaluate("//p:timing/p:bldLst", doc, XPathConstants.NODE);

        // Only count entrance/exit animations -- emphasis-only slides legitimately omit bldLst
        NodeList entrExitAnims = (NodeList) xpath.evaluate(
            "//p:cTn[@presetClass='entr' or @presetClass='exit']//p:spTgt/@spid",
            doc, XPathConstants.NODESET);
        Set<String> animatedSpids = new HashSet<>();
        for (int i = 0; i < entrExitAnims.getLength(); i++) {
            animatedSpids.add(entrExitAnims.item(i).getTextContent());
        }

        if (!animatedSpids.isEmpty() && bldLst == null) {
            errors.add("Animations present but build list is missing");
            return;
        }

        if (bldLst != null) {
            // Check that all animated shapes have build entries
            NodeList buildEntries = (NodeList) xpath.evaluate("./p:bldP", bldLst, XPathConstants.NODESET);
            Set<String> buildSpids = new HashSet<>();

            for (int i = 0; i < buildEntries.getLength(); i++) {
                Element bldP = (Element) buildEntries.item(i);
                String spid = bldP.getAttribute("spid");
                String grpId = bldP.getAttribute("grpId");

                if (spid.isEmpty()) {
                    errors.add("Build list entry missing spid attribute");
                }
                if (grpId.isEmpty()) {
                    errors.add("Build list entry missing grpId attribute");
                }

                buildSpids.add(spid);
            }

            // Check for missing build entries
            for (String spid : animatedSpids) {
                if (!buildSpids.contains(spid)) {
                    errors.add("Shape " + spid + " is animated but missing from build list");
                }
            }
        }
    }

    /**
     * MS-OE376 4.6.16(b): No duplicate (spid, grpId) pairs in bldLst.
     */
    private void validateBuildListUniqueness(Document doc, List<String> errors) throws XPathExpressionException {
        NodeList bldEntries = (NodeList) xpath.evaluate("//p:timing/p:bldLst/p:bldP", doc, XPathConstants.NODESET);
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < bldEntries.getLength(); i++) {
            Element bldP = (Element) bldEntries.item(i);
            String spid = bldP.getAttribute("spid");
            String grpId = bldP.getAttribute("grpId");
            String key = spid + ":" + grpId;

            if (!seen.add(key)) {
                errors.add("Duplicate build list entry: spid=" + spid + ", grpId=" + grpId + " (MS-OE376 4.6.16b)");
            }
        }
    }

    /**
     * MS-OE376 4.6.16(a): Animation targets must reference shapes that exist in the slide's spTree.
     */
    private void validateShapeReferences(Document doc, List<String> errors) throws XPathExpressionException {
        // Collect all shape IDs from spTree
        NodeList shapeIds = (NodeList) xpath.evaluate("//p:spTree//p:cNvPr/@id", doc, XPathConstants.NODESET);
        Set<String> shapeIdSet = new HashSet<>();
        for (int i = 0; i < shapeIds.getLength(); i++) {
            shapeIdSet.add(shapeIds.item(i).getTextContent());
        }

        // Collect all animation target spids
        NodeList targetSpids = (NodeList) xpath.evaluate("//p:spTgt/@spid", doc, XPathConstants.NODESET);
        for (int i = 0; i < targetSpids.getLength(); i++) {
            String spid = targetSpids.item(i).getTextContent();
            if (!shapeIdSet.contains(spid)) {
                errors.add("Animation references non-existent shape spid=" + spid + " (MS-OE376 4.6.16a)");
            }
        }
    }

    /**
     * MS-OE376 cTn constraints: fill attribute must be "hold" or "remove".
     * PowerPoint silently strips or repairs animations with other fill values.
     */
    private void validateFillAttributeValues(Document doc, List<String> errors) throws XPathExpressionException {
        NodeList fillAttrs = (NodeList) xpath.evaluate("//p:cTn/@fill", doc, XPathConstants.NODESET);
        Set<String> validFills = Set.of("hold", "remove");

        for (int i = 0; i < fillAttrs.getLength(); i++) {
            String fillValue = fillAttrs.item(i).getTextContent();
            if (!validFills.contains(fillValue)) {
                errors.add("Unsupported fill value '" + fillValue + "' -- PowerPoint only accepts 'hold' or 'remove'");
            }
        }
    }

    /**
     * MS-OE376 4.6.56(a): PowerPoint requires pRg st and end to be equal.
     * Each animation entry targets exactly one paragraph; ranges are not supported.
     */
    private void validateParagraphRangeEquality(Document doc, List<String> warnings) throws XPathExpressionException {
        NodeList pRgNodes = (NodeList) xpath.evaluate("//p:pRg", doc, XPathConstants.NODESET);

        for (int i = 0; i < pRgNodes.getLength(); i++) {
            Element pRg = (Element) pRgNodes.item(i);
            String st = pRg.getAttribute("st");
            String end = pRg.getAttribute("end");

            if (!st.equals(end)) {
                warnings.add("pRg st=" + st + " and end=" + end + " differ -- PowerPoint requires st == end (MS-OE376 4.6.56a)");
            }
        }
    }

    /**
     * MS-OE376 4.6.4: Validate p:animMotion structural requirements.
     * - origin must be "layout"
     * - pathEditMode must be "relative"
     * - path attribute must be non-empty
     * - ptsTypes attribute must be present
     * - cBhvr must have attrNameLst with ppt_x and ppt_y
     * - Containing cTn must have presetClass="path" and accel/decel attributes
     */
    private void validateMotionPathAnimations(Document doc, List<String> errors, List<String> warnings)
            throws XPathExpressionException {
        NodeList animMotions = (NodeList) xpath.evaluate("//p:animMotion", doc, XPathConstants.NODESET);

        for (int i = 0; i < animMotions.getLength(); i++) {
            Element animMotion = (Element) animMotions.item(i);

            // Check origin
            String origin = animMotion.getAttribute("origin");
            if (!"layout".equals(origin)) {
                errors.add("p:animMotion missing or incorrect origin attribute (expected 'layout', got '" + origin + "')");
            }

            // Check pathEditMode
            String pathEditMode = animMotion.getAttribute("pathEditMode");
            if (!"relative".equals(pathEditMode)) {
                errors.add("p:animMotion missing or incorrect pathEditMode (expected 'relative', got '" + pathEditMode + "')");
            }

            // Check path non-empty
            String path = animMotion.getAttribute("path");
            if (path == null || path.isEmpty()) {
                errors.add("p:animMotion missing or empty path attribute");
            }

            // Check ptsTypes present
            String ptsTypes = animMotion.getAttribute("ptsTypes");
            if (ptsTypes == null || ptsTypes.isEmpty()) {
                errors.add("p:animMotion missing ptsTypes attribute");
            }

            // Check cBhvr has attrNameLst with ppt_x and ppt_y
            Element cBhvr = (Element) xpath.evaluate("./p:cBhvr", animMotion, XPathConstants.NODE);
            if (cBhvr != null) {
                NodeList attrNames = (NodeList) xpath.evaluate(
                    "./p:attrNameLst/p:attrName", cBhvr, XPathConstants.NODESET);
                boolean hasPptX = false, hasPptY = false;
                for (int j = 0; j < attrNames.getLength(); j++) {
                    String val = attrNames.item(j).getTextContent();
                    if ("ppt_x".equals(val)) hasPptX = true;
                    if ("ppt_y".equals(val)) hasPptY = true;
                }
                if (!hasPptX || !hasPptY) {
                    errors.add("p:animMotion cBhvr attrNameLst must contain ppt_x and ppt_y");
                }
            }

            // Check containing cTn has presetClass="path"
            // Navigate up: animMotion -> childTnLst -> cTn (the animation preset cTn)
            Element containingCTn = (Element) xpath.evaluate(
                "ancestor::p:par/p:cTn[@presetClass]", animMotion, XPathConstants.NODE);
            if (containingCTn != null) {
                String presetClass = containingCTn.getAttribute("presetClass");
                if (!"path".equals(presetClass)) {
                    errors.add("p:animMotion containing cTn must have presetClass='path' (got '" + presetClass + "')");
                }

                // Check accel/decel
                String accel = containingCTn.getAttribute("accel");
                String decel = containingCTn.getAttribute("decel");
                if (accel.isEmpty() || decel.isEmpty()) {
                    warnings.add("Motion path cTn missing accel/decel attributes (oracle requires 50000/50000)");
                }
            }
        }
    }

    private void validateVisibilityControls(Document doc, List<String> errors, List<String> warnings)
            throws XPathExpressionException {
        // Check entrance animations have visibility set to visible
        NodeList entranceAnims = (NodeList) xpath.evaluate(
            "//p:animEffect[@transition='in']", doc, XPathConstants.NODESET);

        for (int i = 0; i < entranceAnims.getLength(); i++) {
            Element animEffect = (Element) entranceAnims.item(i);

            // Look for preceding p:set element
            Element parent = (Element) animEffect.getParentNode();
            Element setElement = (Element) xpath.evaluate(
                "./p:set[p:to/p:strVal/@val='visible']", parent, XPathConstants.NODE);

            if (setElement == null) {
                warnings.add("Entrance animation missing visibility set to 'visible'");
            }
        }

        // Check exit animations have visibility set to hidden
        NodeList exitAnims = (NodeList) xpath.evaluate(
            "//p:animEffect[@transition='out']", doc, XPathConstants.NODESET);

        for (int i = 0; i < exitAnims.getLength(); i++) {
            Element animEffect = (Element) exitAnims.item(i);

            // Look for following p:set element
            Element parent = (Element) animEffect.getParentNode();
            Element setElement = (Element) xpath.evaluate(
                "./p:set[p:to/p:strVal/@val='hidden']", parent, XPathConstants.NODE);

            if (setElement == null) {
                warnings.add("Exit animation missing visibility set to 'hidden'");
            }
        }
    }
}
