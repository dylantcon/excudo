package com.excudo.core.model;

/**
 * Enum for PowerPoint slide transition types.
 * Maps user-friendly names to OOXML transition element names and attributes.
 *
 * Transitions are child elements of p:transition inside each slide XML,
 * placed after p:clrMapOvr and before p:timing.
 */
public enum TransitionType {

    NONE("none", null, null, null,
         "No transition effect between slides."),

    CUT("cut", "p:cut", null, null,
        "Instant cut to next slide with no animation."),

    CUT_THROUGH_BLACK("cut-through-black", "p:cut", "thruBlk", "1",
                      "Instant cut through a black screen."),

    FADE("fade", "p:fade", null, null,
         "Smooth fade transition between slides. Professional and subtle."),

    FADE_THROUGH_BLACK("fade-through-black", "p:fade", "thruBlk", "1",
                       "Fade out to black, then fade in next slide."),

    PUSH_LEFT("push-left", "p:push", "dir", "l",
              "New slide pushes current slide off to the left."),

    PUSH_RIGHT("push-right", "p:push", "dir", "r",
               "New slide pushes current slide off to the right."),

    PUSH_UP("push-up", "p:push", "dir", "u",
            "New slide pushes current slide up."),

    PUSH_DOWN("push-down", "p:push", "dir", "d",
              "New slide pushes current slide down."),

    WIPE_LEFT("wipe-left", "p:wipe", "dir", "l",
              "Wipe reveal from right to left."),

    WIPE_RIGHT("wipe-right", "p:wipe", "dir", "r",
               "Wipe reveal from left to right."),

    WIPE_UP("wipe-up", "p:wipe", "dir", "u",
            "Wipe reveal from bottom to top."),

    WIPE_DOWN("wipe-down", "p:wipe", "dir", "d",
              "Wipe reveal from top to bottom."),

    COVER_LEFT("cover-left", "p:cover", "dir", "l",
               "New slide covers from the right."),

    COVER_RIGHT("cover-right", "p:cover", "dir", "r",
                "New slide covers from the left."),

    COVER_UP("cover-up", "p:cover", "dir", "u",
             "New slide covers from the bottom."),

    COVER_DOWN("cover-down", "p:cover", "dir", "d",
               "New slide covers from the top."),

    UNCOVER_LEFT("uncover-left", "p:uncover", "dir", "l",
                 "Current slide uncovers to the left revealing next."),

    UNCOVER_RIGHT("uncover-right", "p:uncover", "dir", "r",
                  "Current slide uncovers to the right revealing next."),

    SPLIT_HORIZONTAL("split-horizontal", "p:split", "orient", "horz",
                     "Split open horizontally from center."),

    SPLIT_VERTICAL("split-vertical", "p:split", "orient", "vert",
                   "Split open vertically from center."),

    BLINDS_HORIZONTAL("blinds-horizontal", "p:blinds", "dir", "horz",
                      "Horizontal blinds reveal effect."),

    BLINDS_VERTICAL("blinds-vertical", "p:blinds", "dir", "vert",
                    "Vertical blinds reveal effect."),

    CHECKER_ACROSS("checker-across", "p:checker", "dir", "horz",
                   "Checkerboard pattern across."),

    CHECKER_DOWN("checker-down", "p:checker", "dir", "vert",
                 "Checkerboard pattern down."),

    COMB_HORIZONTAL("comb-horizontal", "p:comb", "dir", "horz",
                    "Horizontal comb effect."),

    COMB_VERTICAL("comb-vertical", "p:comb", "dir", "vert",
                  "Vertical comb effect."),

    DISSOLVE("dissolve", "p:dissolve", null, null,
             "Random pixel dissolve between slides."),

    DIAMOND("diamond", "p:diamond", null, null,
            "Diamond iris transition."),

    WEDGE("wedge", "p:wedge", null, null,
          "Wedge/clock wipe transition."),

    RANDOM("random", "p:random", null, null,
           "Random transition effect chosen by PowerPoint."),

    WHEEL_1("wheel-1", "p:wheel", "spokes", "1",
            "1-spoke wheel transition."),

    WHEEL_2("wheel-2", "p:wheel", "spokes", "2",
            "2-spoke wheel transition."),

    WHEEL_3("wheel-3", "p:wheel", "spokes", "3",
            "3-spoke wheel transition."),

    WHEEL_4("wheel-4", "p:wheel", "spokes", "4",
            "4-spoke wheel transition."),

    WHEEL_8("wheel-8", "p:wheel", "spokes", "8",
            "8-spoke wheel transition.");

    private final String userFriendlyName;
    private final String xmlElementName;
    private final String attributeName;
    private final String attributeValue;
    private final String description;

    TransitionType(String userFriendlyName, String xmlElementName,
                   String attributeName, String attributeValue, String description) {
        this.userFriendlyName = userFriendlyName;
        this.xmlElementName = xmlElementName;
        this.attributeName = attributeName;
        this.attributeValue = attributeValue;
        this.description = description;
    }

    public String getUserFriendlyName() { return userFriendlyName; }
    public String getXmlElementName() { return xmlElementName; }
    public String getAttributeName() { return attributeName; }
    public String getAttributeValue() { return attributeValue; }
    public String getDescription() { return description; }

    /**
     * Parse a user-friendly transition name to the enum value.
     * Case-insensitive, supports dashes and underscores.
     */
    public static TransitionType parseType(String name) {
        if (name == null || name.trim().isEmpty()) {
            return FADE;
        }
        String normalized = name.toLowerCase().trim().replace('_', '-');

        for (TransitionType type : values()) {
            if (type.getUserFriendlyName().equals(normalized)) {
                return type;
            }
        }

        // Common aliases
        switch (normalized) {
            case "fade": return FADE;
            case "cut": return CUT;
            case "push": return PUSH_LEFT;
            case "wipe": return WIPE_LEFT;
            case "cover": return COVER_LEFT;
            case "uncover": return UNCOVER_LEFT;
            case "split": return SPLIT_HORIZONTAL;
            case "blinds": return BLINDS_HORIZONTAL;
            case "checker": case "checkerboard": return CHECKER_ACROSS;
            case "comb": return COMB_HORIZONTAL;
            case "wheel": return WHEEL_4;
            case "dissolve": return DISSOLVE;
            case "diamond": return DIAMOND;
            case "wedge": return WEDGE;
            case "random": return RANDOM;
            case "none": return NONE;
            default: return FADE;
        }
    }

    public static String[] getAllTypeNames() {
        TransitionType[] types = values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            names[i] = types[i].getUserFriendlyName();
        }
        return names;
    }
}
