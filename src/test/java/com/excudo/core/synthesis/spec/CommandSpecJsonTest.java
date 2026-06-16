package com.excudo.core.synthesis.spec;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.ShapeFill;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.TextColor;
import com.excudo.core.model.TextParagraph;
import com.excudo.core.model.TextRun;
import com.excudo.core.model.TransitionType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Round-trip tests for every spec type in the v1 vocabulary: serialize
 * via {@link CommandSpecJson#toJson(CommandSpec)}, deserialize, assert
 * structural equality against the original. Each spec type gets at
 * least one test; complex nested models (AnimationBinding, TextBody,
 * ShapeStyle) get their own coverage.
 */
public class CommandSpecJsonTest {

    @Test
    public void addShapeSpec_roundTripsWithGeometryAndStyle() {
        CommandSpec spec = new CommandSpec.AddShapeSpec(
            1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_000_000, 2_000_000, 3_000_000, 1_500_000, 600_000),
            "hello", "R1",
            ShapeStyle.withFillAndLine(
                ShapeFill.scheme("accent3"),
                com.excudo.core.model.ShapeLine.thin("000000")),
            "ctr", false, 7);
        assertRoundTrip(spec);
    }

    @Test
    public void removeShapeSpec_roundTrips() {
        assertRoundTrip(new CommandSpec.RemoveShapeSpec(2, 5));
    }

    @Test
    public void moveResizeRotate_roundTrip() {
        assertRoundTrip(new CommandSpec.MoveSpec(1, 5, 100, 200));
        assertRoundTrip(new CommandSpec.ResizeSpec(1, 5, 1000, 500));
        assertRoundTrip(new CommandSpec.RotateSpec(1, 5, 45.0));
    }

    @Test
    public void renameShapeSpec_roundTrips() {
        assertRoundTrip(new CommandSpec.RenameShapeSpec(1, 5, "NewName"));
    }

    @Test
    public void setTextSpec_withRichTextBody() {
        TextBody body = TextBody.builder().addParagraph(
            TextParagraph.builder()
                .alignment("ctr")
                .addRun(TextRun.builder("hello")
                    .bold(true).fontSize(2400)
                    .color(TextColor.hex("FF0000")).build())
                .addRun(TextRun.builder(" world").build())
                .build())
            .build();
        CommandSpec spec = new CommandSpec.SetTextSpec(1, 5, body);
        assertRoundTrip(spec);
    }

    @Test
    public void setShapeStyleSpec_roundTripsWithThemeRef() {
        CommandSpec spec = new CommandSpec.SetShapeStyleSpec(1, 5,
            ShapeStyle.of(
                ShapeFill.solid("FF8800"),
                com.excudo.core.model.ShapeLine.solid(19050, TextColor.hex("000000")),
                com.excudo.core.model.ThemeStyleRef.defaultStyle(false)));
        assertRoundTrip(spec);
    }

    @Test
    public void setShapeStyleSpec_roundTripsFillOpacity() {
        // alphaPercent must survive JSON. Before the fix the adapter wrote
        // only type+color, so the deserialized fill came back fully opaque
        // and a fill-opacity edit silently vanished on apply.
        CommandSpec spec = new CommandSpec.SetShapeStyleSpec(1, 5,
            ShapeStyle.of(
                ShapeFill.solid("FF8800").withAlphaPercent(40),
                com.excudo.core.model.ShapeLine.solid(19050, TextColor.hex("000000")),
                com.excudo.core.model.ThemeStyleRef.defaultStyle(false)));
        assertRoundTrip(spec);
    }

    @Test
    public void setShapeStyleSpec_withThemeRefNONE() {
        // The NONE sentinel serializes as {"none": true} and deserializes
        // back to the same instance per the adapter contract.
        CommandSpec spec = new CommandSpec.SetShapeStyleSpec(1, 5,
            ShapeStyle.textBox());
        CommandSpec round = roundTrip(spec);
        CommandSpec.SetShapeStyleSpec sss = (CommandSpec.SetShapeStyleSpec) round;
        assertSame("NONE sentinel must round-trip as identity",
            com.excudo.core.model.ThemeStyleRef.NONE, sss.style().getThemeStyle());
    }

    @Test
    public void setTextBoxFlagSpec_roundTrips() {
        assertRoundTrip(new CommandSpec.SetTextBoxFlagSpec(1, 5, true));
    }

    @Test
    public void setRunFormatSpec_roundTripsTargetAndFormatting() {
        CommandSpec spec = new CommandSpec.SetRunFormatSpec(1, 5, 0, 1,
            TextRun.builder("fragment").bold(true).color(TextColor.scheme("accent1")).build());
        assertRoundTrip(spec);
    }

    @Test
    public void reorderSpec_eachDirection_roundTrips() {
        for (var d : CommandSpec.ReorderSpec.Direction.values()) {
            assertRoundTrip(new CommandSpec.ReorderSpec(1, 5, d));
        }
    }

    @Test
    public void addAnimationSpec_roundTripsEveryBindingField() {
        AnimationBinding binding = AnimationBinding.builder()
            .target(7).type(AnimationType.FADE).entrance()
            .clickTrigger(2).durationMs(750).delay("250")
            .animationGroup("on-click")
            .easing(30, 40)
            .paragraphRange(0, 3)
            .effectParam("color", "scheme:accent1")
            .timingNodeId(42)
            .build();
        CommandSpec spec = new CommandSpec.AddAnimationSpec(1, binding);
        CommandSpec round = roundTrip(spec);
        AnimationBinding rb = ((CommandSpec.AddAnimationSpec) round).binding();
        assertEquals(7, rb.getTargetSpid());
        assertEquals(AnimationType.FADE, rb.getAnimationType());
        assertEquals("750", rb.getDuration());
        assertEquals("250", rb.getDelay());
        assertEquals(42, rb.getTimingNodeId());
        assertEquals(2, rb.getClickTrigger());
        assertEquals(30, rb.getAcceleration());
        assertEquals(40, rb.getDeceleration());
        assertEquals(Integer.valueOf(0), rb.getParagraphStart());
        assertEquals(Integer.valueOf(3), rb.getParagraphEnd());
        assertEquals("scheme:accent1", rb.getEffectParams().get("color"));
    }

    @Test
    public void removeAnimationSpec_roundTrips() {
        assertRoundTrip(new CommandSpec.RemoveAnimationSpec(1, 42));
    }

    @Test
    public void setAnimationTimingSpec_roundTrips() {
        assertRoundTrip(new CommandSpec.SetAnimationTimingSpec(1, 42, "800", "0"));
        // Null-valued fields (leave unchanged) also round-trip.
        assertRoundTrip(new CommandSpec.SetAnimationTimingSpec(1, 42, "800", null));
    }

    @Test
    public void setTransitionSpec_roundTrips() {
        assertRoundTrip(new CommandSpec.SetTransitionSpec(1, TransitionType.FADE, "fast", 5000));
    }

    @Test
    public void clearTransitionSpec_roundTrips() {
        assertRoundTrip(new CommandSpec.ClearTransitionSpec(1));
    }

    @Test
    public void createGroupSpec_roundTripsChildList() {
        assertRoundTrip(new CommandSpec.CreateGroupSpec(1, List.of(11, 12, 13), "MyGroup"));
    }

    @Test
    public void ungroupSpec_roundTrips() {
        assertRoundTrip(new CommandSpec.UngroupSpec(1, 10));
    }

    @Test
    public void addToGroupSpec_roundTrips() {
        assertRoundTrip(new CommandSpec.AddToGroupSpec(1, 10, 11));
    }

    @Test
    public void detachFromGroupSpec_roundTrips() {
        assertRoundTrip(new CommandSpec.DetachFromGroupSpec(1, 11));
    }

    // ========== Compound primitives + connector + picture ==========

    @Test
    public void createCodeBoxSpec_roundTripsAllFields() {
        assertRoundTrip(new CommandSpec.CreateCodeBoxSpec(
            1, "java", "int x = 1;\nint y = 2;", 100L, 200L, 800L, 600L, "858585", 7));
        // Nullable width/height/lineNumberColor variants.
        assertRoundTrip(new CommandSpec.CreateCodeBoxSpec(
            1, "python", "print('hi')", 0L, 0L, null, null, null, null));
    }

    @Test
    public void createDiagramSpec_roundTripsAllFields() {
        assertRoundTrip(new CommandSpec.CreateDiagramSpec(
            1, "graph TD\n  A --> B\n  B --> C", 100L, 200L, 800L, 600L, 7));
        // Nullable position/size variants.
        assertRoundTrip(new CommandSpec.CreateDiagramSpec(
            1, "sequenceDiagram\n  A->>B: hi", null, null, null, null, null));
    }

    @Test
    public void addConnectorSpec_roundTripsAllFields() {
        // With explicit endpoint bindings + arrowheads + line color.
        assertRoundTrip(new CommandSpec.AddConnectorSpec(
            1, "elbow",
            new ShapeGeometry(100, 200, 500, 300),
            "triangle", "arrow", "FF0000",
            10, 1, 11, 2,
            null, "Connector A", 5));
        // Free-floating connector (no endpoints).
        assertRoundTrip(new CommandSpec.AddConnectorSpec(
            1, "straight",
            new ShapeGeometry(0, 0, 1000, 0),
            null, null, null, null, null, null, null, null, "Free", null));
    }

    @Test
    public void addPictureSpec_roundTripsAllFields() {
        assertRoundTrip(new CommandSpec.AddPictureSpec(
            1, com.excudo.core.model.BlipRef.of("ppt/media/image1.png"),
            new ShapeGeometry(100, 200, 800, 600), "Hero", 7));
        // With explicit mime + crop rect.
        assertRoundTrip(new CommandSpec.AddPictureSpec(
            1, new com.excudo.core.model.BlipRef(
                "ppt/media/image2.jpeg", "image/jpeg", "l=10 t=10 r=10 b=10"),
            new ShapeGeometry(0, 0, 100, 100), "Cropped", null));
    }

    // ========== Error paths ==========

    @Test(expected = com.google.gson.JsonParseException.class)
    public void missingTypeDiscriminator_throws() {
        CommandSpecJson.fromJson("{\"slideNumber\":1,\"spid\":5}");
    }

    @Test(expected = com.google.gson.JsonParseException.class)
    public void unknownTypeDiscriminator_throws() {
        CommandSpecJson.fromJson("{\"_type\":\"BogusSpec\",\"slideNumber\":1}");
    }

    // ========== Helpers ==========

    private static void assertRoundTrip(CommandSpec spec) {
        CommandSpec back = roundTrip(spec);
        assertEquals("JSON round-trip must produce an equal spec", spec, back);
        // Also verify JSON contains the discriminator so downstream consumers
        // can trust the format.
        String json = CommandSpecJson.toJson(spec);
        assertTrue("JSON must include _type: " + json, json.contains("\"_type\""));
    }

    private static CommandSpec roundTrip(CommandSpec spec) {
        String json = CommandSpecJson.toJson(spec);
        return CommandSpecJson.fromJson(json);
    }
}
