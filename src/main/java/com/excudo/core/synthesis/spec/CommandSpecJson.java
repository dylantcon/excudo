package com.excudo.core.synthesis.spec;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.BulletType;
import com.excudo.core.model.FillType;
import com.excudo.core.model.ShapeFill;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeLine;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.TextColor;
import com.excudo.core.model.TextParagraph;
import com.excudo.core.model.TextRun;
import com.excudo.core.model.ThemeStyleRef;
import com.excudo.core.model.TransitionType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON round-trip for {@link CommandSpec} records and the nested model
 * value types they carry ({@link AnimationBinding}, {@link TextBody},
 * {@link ShapeStyle}, and friends). Enables scripts to be written to
 * disk, shipped to another agent, stored alongside PPTX artifacts for
 * audit trails, or diffed as JSON documents.
 *
 * <p>The polymorphic envelope for {@code CommandSpec} uses a
 * {@code "_type"} discriminator matching the record's simple-name
 * (e.g. {@code "AddShapeSpec"}). Unknown discriminators fail fast with
 * a clear error rather than deserializing into a different type.
 *
 * <p>Nested models with Builder-only construction (everything under
 * {@code core/model/*} that isn't a record) get hand-written
 * serializer + deserializer pairs wired via
 * {@code GsonBuilder.registerTypeAdapter}.
 */
public final class CommandSpecJson {

    private CommandSpecJson() {}

    /** Obtain a pre-configured Gson that round-trips every v1 spec. */
    public static Gson gson() {
        return GSON;
    }

    /** Serialize to a pretty-printed string. */
    public static String toJson(CommandSpec spec) {
        return GSON.toJson(spec, CommandSpec.class);
    }

    public static CommandSpec fromJson(String json) {
        return GSON.fromJson(json, CommandSpec.class);
    }

    /**
     * Serialize a flat list of specs to a JSON array. The synthesizer
     * produces scripts topologically sorted, so preserving array order
     * is equivalent to preserving execution order. DAG edges aren't
     * persisted -- consumers that re-build a {@link com.excudo.core.synthesis.CommandScript}
     * from the array get a flat script with no declared dependencies,
     * which {@code ScriptRunner} still executes correctly because the
     * order already honors every originally-declared edge.
     */
    public static String toJsonArray(java.util.List<CommandSpec> specs) {
        JsonArray arr = new JsonArray();
        for (CommandSpec s : specs) {
            arr.add(GSON.toJsonTree(s, CommandSpec.class));
        }
        return GSON.toJson(arr);
    }

    /**
     * Inverse of {@link #toJsonArray}. Returns specs in array order.
     */
    public static java.util.List<CommandSpec> fromJsonArray(String json) {
        JsonElement el = com.google.gson.JsonParser.parseString(json);
        if (!el.isJsonArray()) {
            throw new com.google.gson.JsonParseException(
                "Expected JSON array for CommandSpec script, got: " + el.getClass().getSimpleName());
        }
        JsonArray arr = el.getAsJsonArray();
        java.util.List<CommandSpec> out = new java.util.ArrayList<>(arr.size());
        for (JsonElement item : arr) {
            out.add(GSON.fromJson(item, CommandSpec.class));
        }
        return out;
    }

    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(CommandSpec.class, new CommandSpecAdapter())
        .registerTypeAdapter(AnimationBinding.class, new AnimationBindingAdapter())
        .registerTypeAdapter(TextBody.class, new TextBodyAdapter())
        .registerTypeAdapter(com.excudo.core.model.BodyProperties.class, new BodyPropertiesAdapter())
        .registerTypeAdapter(TextParagraph.class, new TextParagraphAdapter())
        .registerTypeAdapter(TextRun.class, new TextRunAdapter())
        .registerTypeAdapter(TextColor.class, new TextColorAdapter())
        .registerTypeAdapter(ShapeStyle.class, new ShapeStyleAdapter())
        .registerTypeAdapter(ShapeFill.class, new ShapeFillAdapter())
        .registerTypeAdapter(ShapeLine.class, new ShapeLineAdapter())
        .registerTypeAdapter(ThemeStyleRef.class, new ThemeStyleRefAdapter())
        .disableHtmlEscaping()
        .create();

    // ============================================================
    // Polymorphic CommandSpec envelope
    // ============================================================

    private static final class CommandSpecAdapter
            implements JsonSerializer<CommandSpec>, JsonDeserializer<CommandSpec> {
        @Override
        public JsonElement serialize(CommandSpec src, Type typeOfSrc, JsonSerializationContext ctx) {
            // Default serialization using the concrete runtime type, then
            // attach the _type discriminator.
            JsonObject obj = (JsonObject) ctx.serialize(src, src.getClass());
            obj.addProperty("_type", src.getClass().getSimpleName());
            return obj;
        }
        @Override
        public CommandSpec deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx) {
            JsonObject obj = json.getAsJsonObject();
            JsonElement typeEl = obj.get("_type");
            if (typeEl == null) {
                throw new com.google.gson.JsonParseException("CommandSpec JSON missing _type discriminator");
            }
            String type = typeEl.getAsString();
            Class<? extends CommandSpec> cls = SPEC_TYPES.get(type);
            if (cls == null) {
                throw new com.google.gson.JsonParseException("Unknown CommandSpec _type: " + type);
            }
            return ctx.deserialize(obj, cls);
        }
    }

    private static final Map<String, Class<? extends CommandSpec>> SPEC_TYPES;
    static {
        SPEC_TYPES = new HashMap<>();
        SPEC_TYPES.put("AddShapeSpec",        CommandSpec.AddShapeSpec.class);
        SPEC_TYPES.put("RemoveShapeSpec",     CommandSpec.RemoveShapeSpec.class);
        SPEC_TYPES.put("MoveSpec",            CommandSpec.MoveSpec.class);
        SPEC_TYPES.put("ResizeSpec",          CommandSpec.ResizeSpec.class);
        SPEC_TYPES.put("RotateSpec",          CommandSpec.RotateSpec.class);
        SPEC_TYPES.put("RenameShapeSpec",     CommandSpec.RenameShapeSpec.class);
        SPEC_TYPES.put("SetTextSpec",         CommandSpec.SetTextSpec.class);
        SPEC_TYPES.put("SetShapeStyleSpec",   CommandSpec.SetShapeStyleSpec.class);
        SPEC_TYPES.put("SetTextBoxFlagSpec",  CommandSpec.SetTextBoxFlagSpec.class);
        SPEC_TYPES.put("SetRunFormatSpec",    CommandSpec.SetRunFormatSpec.class);
        SPEC_TYPES.put("ReorderSpec",         CommandSpec.ReorderSpec.class);
        SPEC_TYPES.put("AddAnimationSpec",    CommandSpec.AddAnimationSpec.class);
        SPEC_TYPES.put("RemoveAnimationSpec", CommandSpec.RemoveAnimationSpec.class);
        SPEC_TYPES.put("SetAnimationTimingSpec", CommandSpec.SetAnimationTimingSpec.class);
        SPEC_TYPES.put("SetTransitionSpec",   CommandSpec.SetTransitionSpec.class);
        SPEC_TYPES.put("ClearTransitionSpec", CommandSpec.ClearTransitionSpec.class);
        SPEC_TYPES.put("CreateGroupSpec",     CommandSpec.CreateGroupSpec.class);
        SPEC_TYPES.put("UngroupSpec",         CommandSpec.UngroupSpec.class);
        SPEC_TYPES.put("AddToGroupSpec",      CommandSpec.AddToGroupSpec.class);
        SPEC_TYPES.put("DetachFromGroupSpec", CommandSpec.DetachFromGroupSpec.class);
        SPEC_TYPES.put("CreateCodeBoxSpec",   CommandSpec.CreateCodeBoxSpec.class);
        SPEC_TYPES.put("CreateDiagramSpec",   CommandSpec.CreateDiagramSpec.class);
    }

    // ============================================================
    // Nested value-type adapters
    // ============================================================

    private static final class AnimationBindingAdapter
            implements JsonSerializer<AnimationBinding>, JsonDeserializer<AnimationBinding> {
        @Override
        public JsonElement serialize(AnimationBinding b, Type t, JsonSerializationContext ctx) {
            JsonObject o = new JsonObject();
            o.addProperty("targetSpid", b.getTargetSpid());
            o.addProperty("type", b.getAnimationType().name());
            o.addProperty("clickTrigger", b.getClickTrigger());
            o.addProperty("duration", b.getDuration());
            o.addProperty("delay", b.getDelay());
            o.addProperty("timingNodeId", b.getTimingNodeId());
            o.addProperty("animationGroup", b.getAnimationGroup());
            o.addProperty("motionPath", b.getMotionPath());
            o.addProperty("acceleration", b.getAcceleration());
            o.addProperty("deceleration", b.getDeceleration());
            if (b.getParagraphStart() != null) o.addProperty("paragraphStart", b.getParagraphStart());
            if (b.getParagraphEnd()   != null) o.addProperty("paragraphEnd",   b.getParagraphEnd());
            // Transition disposition captured by the three boolean flags.
            if (b.isEntranceAnimation())  o.addProperty("disposition", "entrance");
            else if (b.isExitAnimation()) o.addProperty("disposition", "exit");
            else if (b.isEmphasisAnimation()) o.addProperty("disposition", "emphasis");
            if (b.getEffectParams() != null && !b.getEffectParams().isEmpty()) {
                JsonObject params = new JsonObject();
                for (var e : b.getEffectParams().entrySet()) {
                    params.addProperty(e.getKey(), e.getValue());
                }
                o.add("effectParams", params);
            }
            return o;
        }
        @Override
        public AnimationBinding deserialize(JsonElement el, Type t, JsonDeserializationContext ctx) {
            JsonObject o = el.getAsJsonObject();
            AnimationBinding.Builder bld = AnimationBinding.builder()
                .target(o.get("targetSpid").getAsInt())
                .type(AnimationType.valueOf(o.get("type").getAsString()))
                .clickTrigger(o.get("clickTrigger").getAsInt());
            if (o.has("duration") && !o.get("duration").isJsonNull())
                bld.duration(o.get("duration").getAsString());
            if (o.has("delay") && !o.get("delay").isJsonNull())
                bld.delay(o.get("delay").getAsString());
            if (o.has("timingNodeId")) bld.timingNodeId(o.get("timingNodeId").getAsInt());
            if (o.has("animationGroup") && !o.get("animationGroup").isJsonNull())
                bld.animationGroup(o.get("animationGroup").getAsString());
            if (o.has("motionPath") && !o.get("motionPath").isJsonNull())
                bld.motionPath(o.get("motionPath").getAsString());
            if (o.has("acceleration") || o.has("deceleration")) {
                int acc = o.has("acceleration") ? o.get("acceleration").getAsInt() : 0;
                int dec = o.has("deceleration") ? o.get("deceleration").getAsInt() : 0;
                if (acc != 0 || dec != 0) bld.easing(acc, dec);
            }
            if (o.has("paragraphStart") && o.has("paragraphEnd")) {
                bld.paragraphRange(o.get("paragraphStart").getAsInt(), o.get("paragraphEnd").getAsInt());
            }
            String disp = o.has("disposition") ? o.get("disposition").getAsString() : null;
            if ("entrance".equals(disp)) bld.entrance();
            else if ("exit".equals(disp)) bld.exit();
            else if ("emphasis".equals(disp)) bld.emphasis();
            if (o.has("effectParams") && o.get("effectParams").isJsonObject()) {
                for (var e : o.getAsJsonObject("effectParams").entrySet()) {
                    bld.effectParam(e.getKey(), e.getValue().getAsString());
                }
            }
            return bld.build();
        }
    }

    private static final class TextColorAdapter
            implements JsonSerializer<TextColor>, JsonDeserializer<TextColor> {
        @Override
        public JsonElement serialize(TextColor c, Type t, JsonSerializationContext ctx) {
            JsonObject o = new JsonObject();
            if (c.isScheme()) { o.addProperty("scheme", c.getSchemeVal()); }
            else              { o.addProperty("hex", c.getHexVal()); }
            return o;
        }
        @Override
        public TextColor deserialize(JsonElement el, Type t, JsonDeserializationContext ctx) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("scheme")) return TextColor.scheme(o.get("scheme").getAsString());
            return TextColor.hex(o.get("hex").getAsString());
        }
    }

    private static final class ShapeFillAdapter
            implements JsonSerializer<ShapeFill>, JsonDeserializer<ShapeFill> {
        @Override
        public JsonElement serialize(ShapeFill f, Type t, JsonSerializationContext ctx) {
            JsonObject o = new JsonObject();
            o.addProperty("type", f.getType().name());
            if (f.getColor() != null) o.add("color", ctx.serialize(f.getColor(), TextColor.class));
            return o;
        }
        @Override
        public ShapeFill deserialize(JsonElement el, Type t, JsonDeserializationContext ctx) {
            JsonObject o = el.getAsJsonObject();
            FillType ft = FillType.valueOf(o.get("type").getAsString());
            return switch (ft) {
                case SOLID    -> ShapeFill.solid((TextColor) ctx.deserialize(o.get("color"), TextColor.class));
                case NO_FILL  -> ShapeFill.noFill();
                case GRADIENT -> ShapeFill.noFill(); // TODO: proper gradient JSON when ShapeFill grows it
            };
        }
    }

    private static final class ShapeLineAdapter
            implements JsonSerializer<ShapeLine>, JsonDeserializer<ShapeLine> {
        @Override
        public JsonElement serialize(ShapeLine l, Type t, JsonSerializationContext ctx) {
            JsonObject o = new JsonObject();
            if (l.getWidthEMU() != null) o.addProperty("widthEMU", l.getWidthEMU());
            if (l.getColor()    != null) o.add("color", ctx.serialize(l.getColor(), TextColor.class));
            if (l.getDashStyle()!= null) o.addProperty("dashStyle", l.getDashStyle());
            return o;
        }
        @Override
        public ShapeLine deserialize(JsonElement el, Type t, JsonDeserializationContext ctx) {
            JsonObject o = el.getAsJsonObject();
            Integer w = o.has("widthEMU") ? o.get("widthEMU").getAsInt() : null;
            TextColor c = o.has("color") ? ctx.deserialize(o.get("color"), TextColor.class) : null;
            String d = o.has("dashStyle") ? o.get("dashStyle").getAsString() : null;
            return ShapeLine.of(w != null ? w : 0, c, d);
        }
    }

    private static final class ThemeStyleRefAdapter
            implements JsonSerializer<ThemeStyleRef>, JsonDeserializer<ThemeStyleRef> {
        @Override
        public JsonElement serialize(ThemeStyleRef r, Type t, JsonSerializationContext ctx) {
            JsonObject o = new JsonObject();
            if (r == ThemeStyleRef.NONE) { o.addProperty("none", true); return o; }
            o.addProperty("lineRefIdx",   r.getLineRefIdx());
            o.addProperty("lineColor",    r.getLineColor());
            if (r.getLineShadeVal() != null) o.addProperty("lineShadeVal", r.getLineShadeVal());
            o.addProperty("fillRefIdx",   r.getFillRefIdx());
            o.addProperty("fillColor",    r.getFillColor());
            o.addProperty("effectRefIdx", r.getEffectRefIdx());
            o.addProperty("effectColor",  r.getEffectColor());
            o.addProperty("fontRefIdx",   r.getFontRefIdx());
            o.addProperty("fontColor",    r.getFontColor());
            return o;
        }
        @Override
        public ThemeStyleRef deserialize(JsonElement el, Type t, JsonDeserializationContext ctx) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("none") && o.get("none").getAsBoolean()) return ThemeStyleRef.NONE;
            String shade = o.has("lineShadeVal") ? o.get("lineShadeVal").getAsString() : null;
            return ThemeStyleRef.ofFull(
                o.get("lineRefIdx").getAsInt(),   o.get("lineColor").getAsString(), shade,
                o.get("fillRefIdx").getAsInt(),   o.get("fillColor").getAsString(),
                o.get("effectRefIdx").getAsInt(), o.get("effectColor").getAsString(),
                o.get("fontRefIdx").getAsString(),o.get("fontColor").getAsString());
        }
    }

    private static final class ShapeStyleAdapter
            implements JsonSerializer<ShapeStyle>, JsonDeserializer<ShapeStyle> {
        @Override
        public JsonElement serialize(ShapeStyle s, Type t, JsonSerializationContext ctx) {
            JsonObject o = new JsonObject();
            if (s.getFill() != null)       o.add("fill",       ctx.serialize(s.getFill(), ShapeFill.class));
            if (s.getLine() != null)       o.add("line",       ctx.serialize(s.getLine(), ShapeLine.class));
            if (s.getThemeStyle() != null) o.add("themeStyle", ctx.serialize(s.getThemeStyle(), ThemeStyleRef.class));
            return o;
        }
        @Override
        public ShapeStyle deserialize(JsonElement el, Type t, JsonDeserializationContext ctx) {
            JsonObject o = el.getAsJsonObject();
            ShapeFill fill = o.has("fill") ? ctx.deserialize(o.get("fill"), ShapeFill.class) : null;
            ShapeLine line = o.has("line") ? ctx.deserialize(o.get("line"), ShapeLine.class) : null;
            ThemeStyleRef ref = o.has("themeStyle")
                ? ctx.deserialize(o.get("themeStyle"), ThemeStyleRef.class) : null;
            return ShapeStyle.of(fill, line, ref);
        }
    }

    private static final class TextRunAdapter
            implements JsonSerializer<TextRun>, JsonDeserializer<TextRun> {
        @Override
        public JsonElement serialize(TextRun r, Type t, JsonSerializationContext ctx) {
            JsonObject o = new JsonObject();
            o.addProperty("text", r.getText());
            if (r.getFontSize()      != null) o.addProperty("fontSize", r.getFontSize());
            if (r.getBold()          != null) o.addProperty("bold", r.getBold());
            if (r.getItalic()        != null) o.addProperty("italic", r.getItalic());
            if (r.getUnderline()     != null) o.addProperty("underline", r.getUnderline());
            if (r.getFontFamily()    != null) o.addProperty("fontFamily", r.getFontFamily());
            if (r.getColor()         != null) o.add("color",     ctx.serialize(r.getColor(),     TextColor.class));
            if (r.getHighlight()     != null) o.add("highlight", ctx.serialize(r.getHighlight(), TextColor.class));
            return o;
        }
        @Override
        public TextRun deserialize(JsonElement el, Type t, JsonDeserializationContext ctx) {
            JsonObject o = el.getAsJsonObject();
            TextRun.Builder b = TextRun.builder(o.get("text").getAsString());
            if (o.has("fontSize"))   b.fontSize(o.get("fontSize").getAsInt());
            if (o.has("bold"))       b.bold(o.get("bold").getAsBoolean());
            if (o.has("italic"))     b.italic(o.get("italic").getAsBoolean());
            if (o.has("underline"))  b.underline(o.get("underline").getAsString());
            if (o.has("fontFamily")) b.fontFamily(o.get("fontFamily").getAsString());
            if (o.has("color"))      b.color(ctx.deserialize(o.get("color"), TextColor.class));
            if (o.has("highlight"))  b.highlight(ctx.deserialize(o.get("highlight"), TextColor.class));
            return b.build();
        }
    }

    private static final class TextParagraphAdapter
            implements JsonSerializer<TextParagraph>, JsonDeserializer<TextParagraph> {
        @Override
        public JsonElement serialize(TextParagraph p, Type t, JsonSerializationContext ctx) {
            JsonObject o = new JsonObject();
            if (p.getAlignment()  != null) o.addProperty("alignment",  p.getAlignment());
            o.addProperty("level", p.getLevel());
            if (p.getBulletType() != null) o.addProperty("bulletType", p.getBulletType().name());
            if (p.getBulletChar() != null) o.addProperty("bulletChar", p.getBulletChar());
            JsonArray runs = new JsonArray();
            for (TextRun r : p.getRuns()) runs.add(ctx.serialize(r, TextRun.class));
            o.add("runs", runs);
            return o;
        }
        @Override
        public TextParagraph deserialize(JsonElement el, Type t, JsonDeserializationContext ctx) {
            JsonObject o = el.getAsJsonObject();
            TextParagraph.Builder b = TextParagraph.builder();
            if (o.has("alignment"))  b.alignment(o.get("alignment").getAsString());
            if (o.has("level"))      b.level(o.get("level").getAsInt());
            if (o.has("bulletType")) b.bulletType(BulletType.valueOf(o.get("bulletType").getAsString()));
            if (o.has("bulletChar")) b.bulletChar(o.get("bulletChar").getAsString());
            if (o.has("runs")) {
                for (JsonElement rel : o.getAsJsonArray("runs")) {
                    b.addRun(ctx.deserialize(rel, TextRun.class));
                }
            }
            return b.build();
        }
    }

    private static final class TextBodyAdapter
            implements JsonSerializer<TextBody>, JsonDeserializer<TextBody> {
        @Override
        public JsonElement serialize(TextBody tb, Type t, JsonSerializationContext ctx) {
            JsonObject o = new JsonObject();
            o.addProperty("isPlaceholder", tb.isPlaceholder());
            JsonArray paras = new JsonArray();
            for (TextParagraph p : tb.getParagraphs()) {
                paras.add(ctx.serialize(p, TextParagraph.class));
            }
            o.add("paragraphs", paras);
            // bodyProperties carries insets / anchor / autofit -- TextBody's
            // equality includes it, so round-trip must preserve it or
            // value-equal specs diverge post-JSON.
            if (tb.getBodyProperties() != null) {
                o.add("bodyProperties", ctx.serialize(tb.getBodyProperties(),
                    com.excudo.core.model.BodyProperties.class));
            }
            return o;
        }
        @Override
        public TextBody deserialize(JsonElement el, Type t, JsonDeserializationContext ctx) {
            JsonObject o = el.getAsJsonObject();
            TextBody.Builder b = TextBody.builder();
            if (o.has("isPlaceholder")) b.placeholder(o.get("isPlaceholder").getAsBoolean());
            if (o.has("paragraphs")) {
                for (JsonElement pe : o.getAsJsonArray("paragraphs")) {
                    b.addParagraph(ctx.deserialize(pe, TextParagraph.class));
                }
            }
            if (o.has("bodyProperties")) {
                b.bodyProperties(ctx.deserialize(o.get("bodyProperties"),
                    com.excudo.core.model.BodyProperties.class));
            }
            return b.build();
        }
    }

    private static final class BodyPropertiesAdapter
            implements JsonSerializer<com.excudo.core.model.BodyProperties>,
                       JsonDeserializer<com.excudo.core.model.BodyProperties> {
        @Override
        public JsonElement serialize(com.excudo.core.model.BodyProperties bp,
                Type t, JsonSerializationContext ctx) {
            JsonObject o = new JsonObject();
            if (bp.getVerticalAlignment() != null) o.addProperty("verticalAlignment", bp.getVerticalAlignment());
            if (bp.getWrap()              != null) o.addProperty("wrap",              bp.getWrap());
            if (bp.getVerticalText()      != null) o.addProperty("verticalText",      bp.getVerticalText());
            if (bp.getAutofit()           != null) o.addProperty("autofit",           bp.getAutofit().name());
            if (bp.getFontScale()         != null) o.addProperty("fontScale",         bp.getFontScale());
            if (bp.getLeftInset()         != null) o.addProperty("leftInset",         bp.getLeftInset());
            if (bp.getTopInset()          != null) o.addProperty("topInset",          bp.getTopInset());
            if (bp.getRightInset()        != null) o.addProperty("rightInset",        bp.getRightInset());
            if (bp.getBottomInset()       != null) o.addProperty("bottomInset",       bp.getBottomInset());
            if (bp.getNumColumns()        != null) o.addProperty("numColumns",        bp.getNumColumns());
            if (bp.isRtlCol())                     o.addProperty("rtlCol",            true);
            return o;
        }
        @Override
        public com.excudo.core.model.BodyProperties deserialize(JsonElement el, Type t,
                JsonDeserializationContext ctx) {
            JsonObject o = el.getAsJsonObject();
            com.excudo.core.model.BodyProperties.Builder b = com.excudo.core.model.BodyProperties.builder();
            if (o.has("verticalAlignment")) b.verticalAlignment(o.get("verticalAlignment").getAsString());
            if (o.has("wrap"))              b.wrap(o.get("wrap").getAsString());
            if (o.has("verticalText"))      b.verticalText(o.get("verticalText").getAsString());
            if (o.has("autofit"))           b.autofit(com.excudo.core.model.AutofitType.valueOf(o.get("autofit").getAsString()));
            if (o.has("fontScale"))         b.fontScale(o.get("fontScale").getAsInt());
            if (o.has("leftInset"))         b.leftInset(o.get("leftInset").getAsInt());
            if (o.has("topInset"))          b.topInset(o.get("topInset").getAsInt());
            if (o.has("rightInset"))        b.rightInset(o.get("rightInset").getAsInt());
            if (o.has("bottomInset"))       b.bottomInset(o.get("bottomInset").getAsInt());
            if (o.has("numColumns"))        b.numColumns(o.get("numColumns").getAsInt());
            if (o.has("rtlCol"))            b.rtlCol(o.get("rtlCol").getAsBoolean());
            return b.build();
        }
    }

    // Keep unused imports honest.
    @SuppressWarnings("unused")
    private static final Class<?>[] REFLECTION_SANITY = {
        ShapeGeometry.class, SlideShape.ShapeType.class, TransitionType.class, JsonPrimitive.class,
        JsonNull.class, List.class
    };
}
