package com.excudo.core.commands;

import com.excudo.core.commands.readonly.RenderSlideCommand;

/**
 * View-supplied render-function registry. After the command-self-description
 * sweep, this class is no longer a dispatch factory -- it just holds the
 * slide / contact-sheet render lambdas that the view layer registers at boot
 * so the (core-side) {@code render-slide} command can invoke them without
 * importing view types directly.
 */
public final class UtilityCommandFactory {

    private static RenderSlideCommand.SlideRenderFunction slideRenderFunction;
    private static ContactSheetRenderFunction contactSheetRenderFunction;

    private UtilityCommandFactory() {}

    /**
     * Register the slide render function from the view layer.
     * Must be called before any render commands are executed.
     */
    public static void setSlideRenderFunction(RenderSlideCommand.SlideRenderFunction fn) {
        slideRenderFunction = fn;
    }

    public static RenderSlideCommand.SlideRenderFunction getSlideRenderFunction() {
        return slideRenderFunction;
    }

    /**
     * View-supplied function that renders multiple slides into a single
     * contact-sheet PNG file. Registered from the view layer at boot so
     * {@code core/*} never imports view/rendering types.
     */
    @FunctionalInterface
    public interface ContactSheetRenderFunction {
        /**
         * @param doc          source document
         * @param slideNumbers 1-indexed slide numbers in grid order
         * @param outputFile   destination PNG file
         * @param thumbWidth   pixel width per thumbnail
         * @param thumbHeight  pixel height per thumbnail
         * @param columns      grid columns; rows derived from slideNumbers.length/columns
         * @param gutter       transparent padding between thumbnails
         * @param theme        resolved theme definition, or null to use the first available
         * @param clrMap       master color map
         * @param bgHexForSlide per-slide background hex resolver (may return null)
         * @param masterStyles master text-level styles by placeholder type
         * @return {@code int[]}{sheet width, sheet height} on success
         */
        int[] render(com.excudo.core.model.PPTXDocument doc, int[] slideNumbers,
                     java.io.File outputFile,
                     int thumbWidth, int thumbHeight, int columns, int gutter,
                     com.excudo.core.themes.ThemeDefinition theme,
                     java.util.Map<String, String> clrMap,
                     java.util.function.IntFunction<String> bgHexForSlide,
                     java.util.Map<String, com.excudo.core.themes.TextLevelStyle[]> masterStyles)
                throws Exception;
    }

    public static void setContactSheetRenderFunction(ContactSheetRenderFunction fn) {
        contactSheetRenderFunction = fn;
    }

    public static ContactSheetRenderFunction getContactSheetRenderFunction() {
        return contactSheetRenderFunction;
    }
}
