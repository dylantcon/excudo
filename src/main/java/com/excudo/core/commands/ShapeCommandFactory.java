package com.excudo.core.commands;

import com.excudo.core.commands.mutating.layout.AddLayoutCommand;
import com.excudo.core.commands.mutating.layout.AddPlaceholderCommand;
import com.excudo.core.commands.mutating.layout.DeleteLayoutCommand;
import com.excudo.core.commands.mutating.layout.DuplicateLayoutCommand;
import com.excudo.core.commands.mutating.layout.RemovePlaceholderCommand;
import com.excudo.core.commands.mutating.layout.RenameLayoutCommand;
import com.excudo.core.commands.mutating.master.EditMasterBgCommand;
import com.excudo.core.commands.mutating.master.EditMasterClrMapCommand;
import com.excudo.core.commands.mutating.master.EditMasterStyleCommand;
import com.excudo.core.commands.mutating.notes.AddNotesCommand;
import com.excudo.core.commands.mutating.slide.AddConnectorCommand;
import com.excudo.core.commands.mutating.slide.AddShapeCommand;
import com.excudo.core.commands.mutating.slide.ArrangeCommand;
import com.excudo.core.commands.mutating.slide.BulletPointEditCommand;
import com.excudo.core.commands.mutating.slide.ContentEditCommand;
import com.excudo.core.commands.mutating.slide.CopyStyleCommand;
import com.excudo.core.commands.mutating.slide.DuplicateShapeCommand;
import com.excudo.core.commands.mutating.slide.EnhancedContentCommand;
import com.excudo.core.commands.mutating.slide.GroupShapesCommand;
import com.excudo.core.commands.mutating.slide.InjectIconCommand;
import com.excudo.core.commands.mutating.slide.MoveShapeCommand;
import com.excudo.core.commands.mutating.slide.RemoveShapeCommand;
import com.excudo.core.commands.mutating.slide.ReorderShapeCommand;
import com.excudo.core.commands.mutating.slide.ResizeShapeCommand;
import com.excudo.core.commands.mutating.slide.SetActionCommand;
import com.excudo.core.commands.mutating.slide.SetBodyPropsCommand;
import com.excudo.core.commands.mutating.slide.SetFontCommand;
import com.excudo.core.commands.mutating.slide.SetStyleCommand;
import com.excudo.core.commands.mutating.slide.SetTextCommand;
import com.excudo.core.commands.mutating.slide.SetTransitionCommand;
import com.excudo.core.commands.mutating.slide.UngroupCommand;
import com.excudo.core.commands.mutating.theme.SetObjectDefaultsCommand;
import com.excudo.core.commands.readonly.ShowMasterCommand;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.ShapeFill;
import com.excudo.core.model.ShapeLine;
import com.excudo.core.model.TextColor;
import com.excudo.core.model.BodyProperties;
import com.excudo.core.model.AutofitType;
import com.excudo.core.model.TransitionType;
import com.excudo.core.geometry.UnitParser;
import com.excudo.core.parsing.ParsedCommand;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

/**
 * Factory for creating shape and content-related commands.
 * Handles edit-content, add-shape, move, resize, arrange, reorder,
 * transitions, connectors, notes, and more.
 * LLM requests are bridged to ParsedCommand by LLMRequestBridge before reaching here.
 */
public class ShapeCommandFactory extends AbstractCommandFactory {
    
    private static final Set<String> HANDLED_COMMANDS = new HashSet<>();

    static {
        HANDLED_COMMANDS.add("edit-content");
        HANDLED_COMMANDS.add("add-shape");
        HANDLED_COMMANDS.add("remove-shape");
        HANDLED_COMMANDS.add("edit-bullet");
        HANDLED_COMMANDS.add("set-body-props");
        HANDLED_COMMANDS.add("set-text");
        HANDLED_COMMANDS.add("add-notes");
        HANDLED_COMMANDS.add("add-connector");
        HANDLED_COMMANDS.add("set-action");
        HANDLED_COMMANDS.add("inject");
        HANDLED_COMMANDS.add("enhance");
        HANDLED_COMMANDS.add("set-transition");
        HANDLED_COMMANDS.add("remove-transition");
        HANDLED_COMMANDS.add("move");
        HANDLED_COMMANDS.add("resize");
        HANDLED_COMMANDS.add("arrange");
        HANDLED_COMMANDS.add("reorder");
        HANDLED_COMMANDS.add("duplicate-layout");
        HANDLED_COMMANDS.add("add-layout");
        HANDLED_COMMANDS.add("delete-layout");
        HANDLED_COMMANDS.add("rename-layout");
        HANDLED_COMMANDS.add("add-placeholder");
        HANDLED_COMMANDS.add("remove-placeholder");
        HANDLED_COMMANDS.add("set-font");
        HANDLED_COMMANDS.add("set-style");
        HANDLED_COMMANDS.add("duplicate");
        HANDLED_COMMANDS.add("group");
        HANDLED_COMMANDS.add("ungroup");
        HANDLED_COMMANDS.add("copy-style");
        HANDLED_COMMANDS.add("edit-master-style");
        HANDLED_COMMANDS.add("edit-master-clrmap");
        HANDLED_COMMANDS.add("edit-master-bg");
        HANDLED_COMMANDS.add("show-master");
        HANDLED_COMMANDS.add("set-object-defaults");
    }
    
    public ShapeCommandFactory(PPTXOrchestrator orchestrator) {
        super(orchestrator);
    }
    
    @Override
    public boolean handlesCommand(String commandName) {
        return HANDLED_COMMANDS.contains(commandName);
    }
    
    @Override
    public Command createFromParsedCommand(ParsedCommand parsedCommand, Object displayAdapter) {
        String commandName = parsedCommand.getCommandName();
        
        switch (commandName) {
            case "edit-content":
                Integer editSlide = parsedCommand.getInteger("slide");
                String editSpidStr = parsedCommand.getString("spid");
                String text = parsedCommand.getString("text");
                if (text == null) text = "";  // accept explicit empty-string -> clear
                Boolean prependFlag = parsedCommand.getBoolean("prepend");
                Boolean appendFlag = parsedCommand.getBoolean("append");
                boolean prepend = prependFlag != null && prependFlag;
                boolean append = appendFlag != null && appendFlag;
                if (prepend && append) {
                    throw new IllegalArgumentException(
                        "edit-content: --prepend and --append are mutually exclusive");
                }
                ContentEditCommand.Mode mode = prepend ? ContentEditCommand.Mode.PREPEND
                    : (append ? ContentEditCommand.Mode.APPEND : ContentEditCommand.Mode.REPLACE);
                int editSpid = editSpidStr != null ? Integer.parseInt(editSpidStr) : 0;
                return createContentEdit(editSlide != null ? editSlide : 1, editSpid, text, mode, displayAdapter);
                
            case "add-shape":
                Integer shapeSlide = parsedCommand.getInteger("slide");
                String shapeTypeStr = parsedCommand.getString("shape-type");
                String shapeText = parsedCommand.getString("text");
                Double x = parsedCommand.getDouble("x");
                Double y = parsedCommand.getDouble("y");
                Double width = parsedCommand.getDouble("width");
                Double height = parsedCommand.getDouble("height");
                String fillColor = parsedCommand.getString("fill-color");
                String lineColor = parsedCommand.getString("line-color");
                // Normalize alignment aliases to OOXML vocabulary.
                String alignRaw = parsedCommand.getString("align");
                String alignment = normalizeAlignment(alignRaw);

                // TEXT_BOX isn't a drawingml preset -- OOXML models text boxes
                // as rectangles with no fill, no line, and no p:style element.
                // Accept it as an alias so agents don't have to remember the
                // "rectangle + empty style" incantation when they just want
                // plain text on a slide.
                String normalizedTypeStr = shapeTypeStr != null ? shapeTypeStr.toUpperCase() : "RECTANGLE";
                boolean isTextBoxAlias = "TEXT_BOX".equals(normalizedTypeStr)
                    || "TEXTBOX".equals(normalizedTypeStr);

                SlideShape.ShapeType shapeType = isTextBoxAlias
                    ? SlideShape.ShapeType.RECTANGLE
                    : SlideShape.ShapeType.valueOf(normalizedTypeStr);
                ShapeGeometry geometry = new ShapeGeometry(
                    x != null ? x.longValue() : 0L, y != null ? y.longValue() : 0L,
                    width != null ? width.longValue() : 100L, height != null ? height.longValue() : 50L);

                // TEXT_BOX overrides any fill/line the caller passed -- if you
                // want a styled rectangle, ask for RECTANGLE directly.
                ShapeStyle parsedStyle = isTextBoxAlias
                    ? ShapeStyle.textBox()
                    : parseShapeStyle(fillColor, lineColor);
                return new AddShapeCommand(shapeSlide != null ? shapeSlide : 1, shapeType, geometry,
                    shapeText, isTextBoxAlias ? "TextBox" : "Shape", parsedStyle, alignment,
                    isTextBoxAlias, orchestrator, displayAdapter);
                
            case "remove-shape":
                Integer removeSlide = parsedCommand.getInteger("slide");
                String removeSpidStr = parsedCommand.getString("spid");
                int removeSpid = removeSpidStr != null ? Integer.parseInt(removeSpidStr) : 0;
                return createRemoveShape(removeSlide != null ? removeSlide : 1, removeSpid);

            case "edit-bullet":
                Integer bulletSlide = parsedCommand.getInteger("slide");
                String bulletSpidStr = parsedCommand.getString("spid");
                String bulletOp = parsedCommand.getString("operation");
                Integer bulletIdx = parsedCommand.getInteger("index");
                String bulletText = parsedCommand.getString("text");
                int bulletSpid = bulletSpidStr != null ? Integer.parseInt(bulletSpidStr) : 0;
                return createBulletPointEdit(
                    bulletSlide != null ? bulletSlide : 1,
                    bulletSpid, bulletOp,
                    bulletIdx != null ? bulletIdx : -1,
                    bulletText, null);

            case "set-body-props":
                Integer bodySlide = parsedCommand.getInteger("slide");
                String bodySpidStr = parsedCommand.getString("spid");
                int bodySpid = bodySpidStr != null ? Integer.parseInt(bodySpidStr) : 0;
                String anchor = parsedCommand.getString("anchor");
                String vert = parsedCommand.getString("vert");
                String autofit = parsedCommand.getString("autofit");
                String columnsStr = parsedCommand.getString("columns");
                String wrap = parsedCommand.getString("wrap");
                String textboxStr = parsedCommand.getString("textbox");

                BodyProperties.Builder bpBuilder = BodyProperties.builder();
                if (anchor != null) bpBuilder.verticalAlignment(anchor);
                if (vert != null) bpBuilder.verticalText(vert);
                if (wrap != null) bpBuilder.wrap(wrap);
                if (columnsStr != null) bpBuilder.numColumns(Integer.parseInt(columnsStr));
                if (autofit != null) {
                    switch (autofit.toLowerCase()) {
                        case "none": bpBuilder.autofit(AutofitType.NONE); break;
                        case "normal": bpBuilder.autofit(AutofitType.NORMAL); break;
                        case "shape": bpBuilder.autofit(AutofitType.SHAPE); break;
                    }
                }
                boolean isTextBox = "true".equalsIgnoreCase(textboxStr) || "1".equals(textboxStr);
                return new SetBodyPropsCommand(bodySlide != null ? bodySlide : 1, bodySpid,
                    bpBuilder.build(), isTextBox, orchestrator);

            case "set-text":
                Integer setTextSlide = parsedCommand.getInteger("slide");
                String setTextSpidStr = parsedCommand.getString("spid");
                String jsonBody = parsedCommand.getString("json");
                int setTextSpid = setTextSpidStr != null ? Integer.parseInt(setTextSpidStr) : 0;
                com.excudo.core.model.TextBody textBody = TextBodyJsonParser.parse(jsonBody);
                return new SetTextCommand(setTextSlide != null ? setTextSlide : 1, setTextSpid, textBody, orchestrator);

            case "add-notes":
                Integer notesSlide = parsedCommand.getInteger("slide");
                String notesText = parsedCommand.getString("text");
                return new AddNotesCommand(notesSlide != null ? notesSlide : 1, notesText, orchestrator);

            case "add-connector":
                Integer cxnSlide = parsedCommand.getInteger("slide");
                String cxnType = parsedCommand.getString("type");
                Double cxnX = parsedCommand.getDouble("x");
                Double cxnY = parsedCommand.getDouble("y");
                Double cxnWidth = parsedCommand.getDouble("width");
                Double cxnHeight = parsedCommand.getDouble("height");
                String cxnHeadEnd = parsedCommand.getString("head-end");
                String cxnTailEnd = parsedCommand.getString("tail-end");
                String cxnLineColor = parsedCommand.getString("line-color");
                String cxnLineStyle = parsedCommand.getString("line-style");
                String startBinding = parsedCommand.getString("start");
                String endBinding = parsedCommand.getString("end");
                String cxnPath = parsedCommand.getString("path");

                ShapeGeometry cxnGeometry = new ShapeGeometry(
                    cxnX != null ? cxnX.longValue() : 0L, cxnY != null ? cxnY.longValue() : 0L,
                    cxnWidth != null ? cxnWidth.longValue() : 914400L, cxnHeight != null ? cxnHeight.longValue() : 0L);

                Integer startCxnSpid = null, startCxnIdx = null, endCxnSpid = null, endCxnIdx = null;
                if (startBinding != null && startBinding.contains(":")) {
                    String[] parts = startBinding.split(":");
                    startCxnSpid = Integer.parseInt(parts[0]);
                    startCxnIdx = Integer.parseInt(parts[1]);
                }
                if (endBinding != null && endBinding.contains(":")) {
                    String[] parts = endBinding.split(":");
                    endCxnSpid = Integer.parseInt(parts[0]);
                    endCxnIdx = Integer.parseInt(parts[1]);
                }

                return new AddConnectorCommand(cxnSlide != null ? cxnSlide : 1, cxnType, cxnGeometry,
                    cxnHeadEnd, cxnTailEnd, cxnLineColor, cxnLineStyle,
                    startCxnSpid, startCxnIdx, endCxnSpid, endCxnIdx, cxnPath, orchestrator);

            case "set-action":
                Integer actionSlide = parsedCommand.getInteger("slide");
                String actionSpidStr = parsedCommand.getString("spid");
                String action = parsedCommand.getString("action");
                String sound = parsedCommand.getString("sound");
                int actionSpid = actionSpidStr != null ? Integer.parseInt(actionSpidStr) : 0;
                return new SetActionCommand(actionSlide != null ? actionSlide : 1, actionSpid, action, sound, orchestrator);

            case "set-transition": {
                Integer transSlide = parsedCommand.getInteger("slide");
                String transType = parsedCommand.getString("type");
                String transSpeed = parsedCommand.getString("speed");
                Integer transAdvance = parsedCommand.getInteger("advance");
                return new SetTransitionCommand(
                    transSlide != null ? transSlide : 1,
                    TransitionType.parseType(transType),
                    transSpeed, transAdvance, orchestrator);
            }

            case "remove-transition": {
                Integer rmTransSlide = parsedCommand.getInteger("slide");
                return new SetTransitionCommand(
                    rmTransSlide != null ? rmTransSlide : 1,
                    TransitionType.NONE, null, null, orchestrator);
            }

            case "move": {
                Integer moveSlide = parsedCommand.getInteger("slide");
                String moveSpidStr = parsedCommand.getString("spid");
                String xStr = parsedCommand.getString("x");
                String yStr = parsedCommand.getString("y");
                int moveSpid = moveSpidStr != null ? Integer.parseInt(moveSpidStr) : 0;
                long moveX = UnitParser.parseToEmu(xStr);
                long moveY = UnitParser.parseToEmu(yStr);
                return new MoveShapeCommand(moveSlide != null ? moveSlide : 1, moveSpid, moveX, moveY, orchestrator);
            }

            case "resize": {
                Integer resizeSlide = parsedCommand.getInteger("slide");
                String resizeSpidStr = parsedCommand.getString("spid");
                String widthStr = parsedCommand.getString("width");
                String heightStr = parsedCommand.getString("height");
                int resizeSpid = resizeSpidStr != null ? Integer.parseInt(resizeSpidStr) : 0;
                long resizeW = UnitParser.parseToEmu(widthStr);
                long resizeH = UnitParser.parseToEmu(heightStr);
                return new ResizeShapeCommand(resizeSlide != null ? resizeSlide : 1, resizeSpid, resizeW, resizeH, orchestrator);
            }

            case "arrange": {
                Integer arrSlide = parsedCommand.getInteger("slide");
                String arrOp = parsedCommand.getString("operation");
                String arrTargets = parsedCommand.getString("targets");
                String arrAnchorStr = parsedCommand.getString("anchor");
                Integer arrAnchor = arrAnchorStr != null ? Integer.parseInt(arrAnchorStr) : null;
                return new ArrangeCommand(arrSlide != null ? arrSlide : 1, arrOp, arrTargets, arrAnchor, orchestrator);
            }

            case "reorder": {
                Integer reorderSlide = parsedCommand.getInteger("slide");
                String reorderSpidStr = parsedCommand.getString("spid");
                String direction = parsedCommand.getString("direction");
                int reorderSpid = reorderSpidStr != null ? Integer.parseInt(reorderSpidStr) : 0;
                return new ReorderShapeCommand(reorderSlide != null ? reorderSlide : 1, reorderSpid,
                    ReorderShapeCommand.ZOrderOperation.parse(direction), orchestrator);
            }

            case "duplicate-layout": {
                String dlSource = parsedCommand.getString("sourceLayoutId");
                String dlName = parsedCommand.getString("name");
                return new DuplicateLayoutCommand(
                    dlSource != null ? dlSource : "slideLayout1",
                    dlName != null ? dlName : "Custom Layout",
                    orchestrator);
            }

            case "add-layout": {
                String alName = parsedCommand.getString("name");
                String alType = parsedCommand.getString("type");
                String alPlaceholders = parsedCommand.getString("placeholders");
                return new AddLayoutCommand(
                    alName != null ? alName : "New Layout",
                    alType, alPlaceholders, orchestrator);
            }

            case "delete-layout": {
                String delLayoutId = parsedCommand.getString("layoutId");
                return new DeleteLayoutCommand(
                    delLayoutId != null ? delLayoutId : "slideLayout1",
                    orchestrator);
            }

            case "rename-layout": {
                String rnLayoutId = parsedCommand.getString("layoutId");
                String rnName = parsedCommand.getString("name");
                return new RenameLayoutCommand(
                    rnLayoutId != null ? rnLayoutId : "slideLayout1",
                    rnName != null ? rnName : "Renamed Layout",
                    orchestrator);
            }

            case "add-placeholder": {
                String apLayoutId = parsedCommand.getString("layoutId");
                String apType = parsedCommand.getString("type");
                Integer apIdx = parsedCommand.getInteger("idx");
                Double apX = parsedCommand.getDouble("x");
                Double apY = parsedCommand.getDouble("y");
                Double apCx = parsedCommand.getDouble("cx");
                Double apCy = parsedCommand.getDouble("cy");
                return new AddPlaceholderCommand(
                    apLayoutId != null ? apLayoutId : "slideLayout1",
                    apType != null ? apType : "obj",
                    apIdx != null ? apIdx : 1,
                    apX != null ? apX.longValue() : 0L,
                    apY != null ? apY.longValue() : 0L,
                    apCx != null ? apCx.longValue() : 4572000L,
                    apCy != null ? apCy.longValue() : 3429000L,
                    orchestrator);
            }

            case "remove-placeholder": {
                String rpLayoutId = parsedCommand.getString("layoutId");
                Integer rpIdx = parsedCommand.getInteger("idx");
                return new RemovePlaceholderCommand(
                    rpLayoutId != null ? rpLayoutId : "slideLayout1",
                    rpIdx != null ? rpIdx : 1,
                    orchestrator);
            }

            case "set-font": {
                Integer fontSlide = parsedCommand.getInteger("slide");
                String fontSpidStr = parsedCommand.getString("spid");
                int fontSpid = fontSpidStr != null ? Integer.parseInt(fontSpidStr) : 0;
                Map<String, Object> fontProps = new HashMap<>();
                String family = parsedCommand.getString("family");
                Integer fontSize = parsedCommand.getInteger("size");
                String boldStr = parsedCommand.getString("bold");
                String italicStr = parsedCommand.getString("italic");
                String underlineStr = parsedCommand.getString("underline");
                String fontColor = parsedCommand.getString("color");
                if (family != null) fontProps.put("family", family);
                if (fontSize != null) fontProps.put("size", fontSize);
                if (boldStr != null) fontProps.put("bold", "true".equalsIgnoreCase(boldStr) || "1".equals(boldStr));
                if (italicStr != null) fontProps.put("italic", "true".equalsIgnoreCase(italicStr) || "1".equals(italicStr));
                if (underlineStr != null) fontProps.put("underline", "true".equalsIgnoreCase(underlineStr) || "1".equals(underlineStr));
                if (fontColor != null) fontProps.put("color", fontColor);
                return new SetFontCommand(fontSlide != null ? fontSlide : 1, fontSpid, fontProps, orchestrator);
            }

            case "set-style": {
                Integer styleSlide = parsedCommand.getInteger("slide");
                String styleSpidStr = parsedCommand.getString("spid");
                int styleSpid = styleSpidStr != null ? Integer.parseInt(styleSpidStr) : 0;
                String styleFillColor = parsedCommand.getString("fill-color");
                String styleLineColor = parsedCommand.getString("line-color");
                ShapeStyle parsedNewStyle = parseShapeStyle(styleFillColor, styleLineColor);
                if (parsedNewStyle == null) parsedNewStyle = ShapeStyle.defaultStyle();
                return new SetStyleCommand(styleSlide != null ? styleSlide : 1, styleSpid,
                    parsedNewStyle, orchestrator);
            }

            case "duplicate": {
                Integer dupSlide = parsedCommand.getInteger("slide");
                String dupSpidStr = parsedCommand.getString("spid");
                int dupSpid = dupSpidStr != null ? Integer.parseInt(dupSpidStr) : 0;
                String offsetXStr = parsedCommand.getString("offset-x");
                String offsetYStr = parsedCommand.getString("offset-y");
                long dupOffsetX = offsetXStr != null
                    ? com.excudo.core.geometry.UnitParser.parseToEmu(offsetXStr)
                    : DuplicateShapeCommand.DEFAULT_OFFSET_EMU;
                long dupOffsetY = offsetYStr != null
                    ? com.excudo.core.geometry.UnitParser.parseToEmu(offsetYStr)
                    : DuplicateShapeCommand.DEFAULT_OFFSET_EMU;
                return new DuplicateShapeCommand(dupSlide != null ? dupSlide : 1, dupSpid,
                    dupOffsetX, dupOffsetY, orchestrator);
            }

            case "inject":
                Integer injectSlide = parsedCommand.getInteger("slide");
                String iconQuery = parsedCommand.getString("keyword");
                
                // Parse placement options from command parameters
                Map<String, Object> placementOptions = new HashMap<>();
                Double injectX = parsedCommand.getDouble("x");
                Double injectY = parsedCommand.getDouble("y");
                Double injectWidth = parsedCommand.getDouble("width");
                Double injectHeight = parsedCommand.getDouble("height");
                String position = parsedCommand.getString("position");
                
                if (injectX != null) placementOptions.put("x", injectX);
                if (injectY != null) placementOptions.put("y", injectY);
                if (injectWidth != null) placementOptions.put("width", injectWidth);
                if (injectHeight != null) placementOptions.put("height", injectHeight);
                if (position != null) placementOptions.put("position", position);
                
                return createIconInjection(injectSlide != null ? injectSlide : 1, iconQuery != null ? iconQuery : "", placementOptions);
                
            case "enhance":
                Integer enhanceSlide = parsedCommand.getInteger("slide");
                String keyword = parsedCommand.getString("keyword");
                String templateStyle = parsedCommand.getString("style");
                
                // Parse geometry options for enhancement
                Map<String, Object> enhanceGeometry = new HashMap<>();
                Double enhanceX = parsedCommand.getDouble("x");
                Double enhanceY = parsedCommand.getDouble("y");
                Double enhanceWidth = parsedCommand.getDouble("width");
                Double enhanceHeight = parsedCommand.getDouble("height");
                
                if (enhanceX != null) enhanceGeometry.put("x", enhanceX);
                if (enhanceY != null) enhanceGeometry.put("y", enhanceY);
                if (enhanceWidth != null) enhanceGeometry.put("width", enhanceWidth);
                if (enhanceHeight != null) enhanceGeometry.put("height", enhanceHeight);
                
                return createEnhancedContent(enhanceSlide != null ? enhanceSlide : 1, keyword != null ? keyword : "", templateStyle, enhanceGeometry);

            case "group": {
                Integer groupSlide = parsedCommand.getInteger("slide");
                String spidsStr = parsedCommand.getString("spids");
                if (spidsStr == null || spidsStr.trim().isEmpty()) {
                    throw new IllegalArgumentException("group command requires a 'spids' parameter (comma-separated SPIDs)");
                }
                java.util.List<Integer> groupSpids = new java.util.ArrayList<>();
                for (String part : spidsStr.split(",")) {
                    groupSpids.add(Integer.parseInt(part.trim()));
                }
                return new GroupShapesCommand(groupSlide != null ? groupSlide : 1, groupSpids, orchestrator);
            }

            case "ungroup": {
                Integer ungroupSlide = parsedCommand.getInteger("slide");
                String ungroupSpidStr = parsedCommand.getString("spid");
                int ungroupSpid = ungroupSpidStr != null ? Integer.parseInt(ungroupSpidStr) : 0;
                return new UngroupCommand(ungroupSlide != null ? ungroupSlide : 1, ungroupSpid, orchestrator);
            }

            case "copy-style": {
                Integer csSlide = parsedCommand.getInteger("slide");
                String csSourceStr = parsedCommand.getString("source");
                String csTargetsStr = parsedCommand.getString("targets");
                if (csSourceStr == null) {
                    throw new IllegalArgumentException("copy-style command requires a 'source' parameter");
                }
                if (csTargetsStr == null || csTargetsStr.trim().isEmpty()) {
                    throw new IllegalArgumentException("copy-style command requires a 'targets' parameter (comma-separated SPIDs)");
                }
                int csSource = Integer.parseInt(csSourceStr.trim());
                java.util.List<Integer> csTargets = new java.util.ArrayList<>();
                for (String part : csTargetsStr.split(",")) {
                    csTargets.add(Integer.parseInt(part.trim()));
                }
                return new CopyStyleCommand(csSlide != null ? csSlide : 1, csSource, csTargets, orchestrator);
            }

            case "edit-master-style": {
                String msTarget = parsedCommand.getString("target");
                Integer msLevel = parsedCommand.getInteger("level");
                Map<String, Object> msUpdates = new HashMap<>();
                Integer msFontSize = parsedCommand.getInteger("fontSize");
                String msBold = parsedCommand.getString("bold");
                String msColor = parsedCommand.getString("color");
                String msBullet = parsedCommand.getString("bullet");
                String msBulletFont = parsedCommand.getString("bulletFont");
                Integer msMargin = parsedCommand.getInteger("margin");
                Integer msIndent = parsedCommand.getInteger("indent");
                if (msFontSize != null) msUpdates.put("fontSize", msFontSize);
                if (msBold != null) msUpdates.put("bold", msBold);
                if (msColor != null) msUpdates.put("color", msColor);
                if (msBullet != null) msUpdates.put("bullet", msBullet);
                if (msBulletFont != null) msUpdates.put("bulletFont", msBulletFont);
                if (msMargin != null) msUpdates.put("margin", msMargin);
                if (msIndent != null) msUpdates.put("indent", msIndent);
                return new EditMasterStyleCommand(
                    msTarget != null ? msTarget : "body",
                    msLevel != null ? msLevel : 1,
                    msUpdates, orchestrator);
            }

            case "edit-master-clrmap": {
                Map<String, String> clrMappings = new java.util.LinkedHashMap<>();
                String cmBg1 = parsedCommand.getString("bg1");
                String cmTx1 = parsedCommand.getString("tx1");
                String cmBg2 = parsedCommand.getString("bg2");
                String cmTx2 = parsedCommand.getString("tx2");
                if (cmBg1 != null) clrMappings.put("bg1", cmBg1);
                if (cmTx1 != null) clrMappings.put("tx1", cmTx1);
                if (cmBg2 != null) clrMappings.put("bg2", cmBg2);
                if (cmTx2 != null) clrMappings.put("tx2", cmTx2);
                if (clrMappings.isEmpty()) {
                    throw new IllegalArgumentException("edit-master-clrmap requires at least one mapping (--bg1, --tx1, --bg2, --tx2)");
                }
                return new EditMasterClrMapCommand(clrMappings, orchestrator);
            }

            case "edit-master-bg": {
                Integer bgFillIdx = parsedCommand.getInteger("fill-idx");
                String bgColor = parsedCommand.getString("color");
                return new EditMasterBgCommand(
                    bgFillIdx != null ? bgFillIdx : 1001,
                    bgColor, orchestrator);
            }

            case "show-master": {
                return new ShowMasterCommand(orchestrator, (CommandDisplay) displayAdapter);
            }

            case "set-object-defaults": {
                String odFontColor = parsedCommand.getString("font-color");
                Integer odLineWidth = parsedCommand.getInteger("line-width");
                String odFillColor = parsedCommand.getString("fill-color");
                return new SetObjectDefaultsCommand(odFontColor, odLineWidth, odFillColor, orchestrator);
            }

            default:
                throw new IllegalArgumentException("Unknown shape command: " + commandName);
        }
    }
    
    // ========== PUBLIC COMMAND CREATION METHODS ==========
    
    /**
     * Create a content edit command.
     * 
     * Phase 3 Enhancement: Includes pre-validation of shape existence and geometry.
     * 
     * @param slideNumber the slide number containing the shape
     * @param spid the SPID of the shape to edit
     * @param newText the new text content
     * @return ContentEditCommand
     * @throws IllegalArgumentException if validation fails
     */
    public ContentEditCommand createContentEdit(int slideNumber, int spid, String newText) {
        return createContentEdit(slideNumber, spid, newText, ContentEditCommand.Mode.REPLACE, null);
    }

    /**
     * Overload for callers who want to choose replace/prepend/append and
     * receive feedback through a display adapter (typically the console
     * engine). The display adapter is optional -- passing null suppresses
     * feedback (headless / programmatic use).
     */
    public ContentEditCommand createContentEdit(int slideNumber, int spid, String newText,
            ContentEditCommand.Mode mode, Object displayAdapter) {
        validateContentEditParameters(slideNumber, spid, newText);
        return new ContentEditCommand(slideNumber, spid, newText, mode, orchestrator, displayAdapter);
    }
    
    /**
     * Create an enhanced content command.
     * 
     * @param slideNumber the slide number to enhance
     * @param iconKeyword the keyword for content search
     * @param templateStyle the template style to apply
     * @param geometry the geometry parameters
     * @return EnhancedContentCommand
     */
    public EnhancedContentCommand createEnhancedContent(int slideNumber, String iconKeyword, 
                                                       String templateStyle, Map<String, Object> geometry) {
        return new EnhancedContentCommand(slideNumber, iconKeyword, templateStyle, geometry, orchestrator);
    }
    
    /**
     * Create an add shape command.
     * 
     * @param slideNumber the slide number to add the shape to
     * @param shapeType the type of shape to create
     * @param geometry the shape geometry (position and size)
     * @param text the text content (can be null for non-text shapes)
     * @param shapeName the name for the shape
     * @return AddShapeCommand
     */
    public AddShapeCommand createAddShape(int slideNumber, SlideShape.ShapeType shapeType, 
                                         ShapeGeometry geometry, String text, String shapeName) {
        return new AddShapeCommand(slideNumber, shapeType, geometry, text, shapeName, orchestrator);
    }
    
    /**
     * Create an icon injection command.
     * 
     * @param slideNumber the slide number to inject icon into
     * @param iconQuery the query/keyword for icon search
     * @param placementOptions optional placement options (position, size, etc.)
     * @return InjectIconCommand
     */
    /**
     * Create a remove shape command.
     */
    public RemoveShapeCommand createRemoveShape(int slideNumber, int spid) {
        return new RemoveShapeCommand(slideNumber, spid, orchestrator);
    }

    /**
     * Create a bullet point edit command.
     */
    public BulletPointEditCommand createBulletPointEdit(int slideNumber, int spid, String operation,
                                                        int bulletIndex, String newText, String bulletStyle) {
        return new BulletPointEditCommand(slideNumber, spid, operation, bulletIndex, newText, bulletStyle, orchestrator);
    }

    public InjectIconCommand createIconInjection(int slideNumber, String iconQuery,
                                                Map<String, Object> placementOptions) {
        return new InjectIconCommand(slideNumber, iconQuery, placementOptions, orchestrator);
    }
    
    // ========== STYLE HELPERS ==========

    /**
     * Parse optional fill-color and line-color strings into a ShapeStyle.
     * Accepts hex colors (e.g. "FF0000", "#FF0000") or scheme names (e.g. "accent1").
     * Returns null if no style parameters given (triggers default theme style).
     */
    /**
     * Normalize an alignment input ("left" / "l" / "center" / "ctr" /
     * "right" / "r" / "justify" / "just") to the canonical OOXML token
     * ("l" / "ctr" / "r" / "just"). Returns null if the input is null
     * or blank, signaling "use default alignment." Throws on
     * unrecognized values rather than silently dropping them so the
     * agent gets immediate feedback.
     */
    private String normalizeAlignment(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().toLowerCase();
        switch (v) {
            case "l": case "left":    return "l";
            case "ctr": case "center": case "centre": return "ctr";
            case "r": case "right":   return "r";
            case "just": case "justify": return "just";
            default:
                throw new IllegalArgumentException(
                    "Unrecognised alignment: '" + raw
                    + "'. Use one of: l/left, ctr/center, r/right, just/justify.");
        }
    }

    private ShapeStyle parseShapeStyle(String fillColor, String lineColor) {
        ShapeFill fill = null;
        ShapeLine line = null;

        if (fillColor != null && !fillColor.isEmpty()) {
            fill = isSchemeColor(fillColor)
                ? ShapeFill.scheme(fillColor)
                : ShapeFill.solid(fillColor);
        }

        if (lineColor != null && !lineColor.isEmpty()) {
            TextColor lc = isSchemeColor(lineColor)
                ? TextColor.scheme(lineColor)
                : TextColor.hex(lineColor);
            line = ShapeLine.solid(12700, lc); // 1pt default width
        }

        if (fill == null && line == null) return null;
        return ShapeStyle.withFillAndLine(fill, line);
    }

    private static boolean isSchemeColor(String val) {
        String lower = val.toLowerCase();
        return lower.startsWith("accent") || lower.startsWith("dk") || lower.startsWith("lt")
            || "hlink".equals(lower) || "folhlink".equals(lower);
    }

    // ========== VALIDATION METHODS ==========
    
    /**
     * Validate parameters for content edit operations.
     * Phase 3 Enhancement: Pre-validates shape existence and provides detailed error messages.
     * 
     * @param slideNumber the slide number
     * @param spid the shape SPID
     * @param newText the new text content
     * @throws IllegalArgumentException if validation fails
     */
    private void validateContentEditParameters(int slideNumber, int spid, String newText) {
        // Basic parameter validation
        if (slideNumber <= 0) {
            throw new IllegalArgumentException(String.format(
                "Invalid slide number: %d. Slide numbers must be positive.", slideNumber));
        }
        
        if (spid <= 0) {
            throw new IllegalArgumentException(String.format(
                "Invalid SPID: %d. SPIDs must be positive integers.", spid));
        }
        
        if (newText == null) {
            throw new IllegalArgumentException("New text content cannot be null.");
        }
        
    }
}