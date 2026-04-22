package com.excudo.view.components;

import com.excudo.view.MainController;
// import com.excudo.core.model.Shape;  // TODO: Implement Shape class
import com.excudo.core.orchestration.SlideMetadata;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for the properties panel that displays and allows editing
 * of shape and slide properties.
 */
public class PropertiesController implements Initializable {
    
    // ========== FXML COMPONENTS ==========
    
    @FXML private TableView<PropertyItem> propertiesTable;
    @FXML private TableColumn<PropertyItem, String> nameColumn;
    @FXML private TableColumn<PropertyItem, String> valueColumn;
    @FXML private Label propertiesTitle;
    @FXML private Button refreshPropertiesButton;
    
    // ========== STATE ==========
    
    private MainController mainController;
    private ObservableList<PropertyItem> properties = FXCollections.observableArrayList();
    private Object currentSelection;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        setupEventHandlers();
    }
    
    // ========== INITIALIZATION ==========
    
    /**
     * Set reference to main controller
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }
    
    /**
     * Set the properties table from FXML
     */
    public void setPropertiesTable(TableView<PropertyItem> propertiesTable) {
        this.propertiesTable = propertiesTable;
        setupTable();
    }
    
    private void setupTable() {
        if (propertiesTable != null) {
            // Configure columns
            if (nameColumn == null) {
                nameColumn = new TableColumn<>("Property");
                nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
                nameColumn.setPrefWidth(150);
            }
            
            if (valueColumn == null) {
                valueColumn = new TableColumn<>("Value");
                valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
                valueColumn.setCellFactory(TextFieldTableCell.forTableColumn());
                valueColumn.setPrefWidth(200);
                
                // Make value column editable
                valueColumn.setOnEditCommit(event -> {
                    PropertyItem item = event.getRowValue();
                    item.setValue(event.getNewValue());
                    handlePropertyChanged(item);
                });
            }
            
            // Set columns if not already set
            if (propertiesTable.getColumns().isEmpty()) {
                propertiesTable.getColumns().addAll(nameColumn, valueColumn);
            }
            
            // Set items
            propertiesTable.setItems(properties);
            propertiesTable.setEditable(true);
            
            // Placeholder text
            propertiesTable.setPlaceholder(new Label("No selection"));
        }
    }
    
    private void setupEventHandlers() {
        if (refreshPropertiesButton != null) {
            refreshPropertiesButton.setOnAction(e -> refreshProperties());
        }
    }
    
    // ========== PROPERTY DISPLAY ==========
    
    /**
     * Display properties for a slide. Pulls typed data via the
     * introspection surface so the panel shows the same information
     * the agent sees -- no summarization gap between what the model
     * sees and what the user sees.
     */
    public void displaySlideProperties(SlideMetadata slide) {
        currentSelection = slide;
        properties.clear();

        if (slide == null) {
            updateTitle("Properties");
            return;
        }
        updateTitle("Slide " + slide.getSlideNumber() + " Properties");

        properties.add(row("Slide Number", String.valueOf(slide.getSlideNumber())));
        properties.add(row("Title", slide.getTitle() != null ? slide.getTitle() : "Untitled"));
        properties.add(row("Type", String.valueOf(slide.getType())));
        properties.add(row("Layout", slide.getLayoutName() != null ? slide.getLayoutName() : "Custom"));
        properties.add(row("Shape Count", String.valueOf(slide.getShapeCount())));
        properties.add(row("Animation Count", String.valueOf(slide.getAnimationCount())));
        if (slide.getSpids() != null && !slide.getSpids().isEmpty()) {
            properties.add(row("SPIDs", slide.getSpids().toString()));
        }

        // Augment with introspection-surface data the metadata doesn't
        // carry: transition (and whether it's an explicit slide
        // override vs. inherited), plus user-added vs. placeholder
        // shape breakdown.
        var orch = mainController != null ? mainController.getCurrentOrchestrator() : null;
        if (orch != null) {
            try {
                var introspector = new com.excudo.core.introspection.SlideIntrospector(orch);
                var trans = introspector.getTransition(slide.getSlideNumber());
                if (trans != null) {
                    properties.add(row("Transition",
                        trans.type().getUserFriendlyName() + " (" + trans.source().name().toLowerCase() + ")"));
                    properties.add(row("Transition Speed", trans.speed()));
                    if (trans.autoAdvanceMs() != null) {
                        properties.add(row("Auto Advance", trans.autoAdvanceMs() + "ms"));
                    }
                } else {
                    properties.add(row("Transition", "none"));
                }
                // User-added shape count (total minus placeholder-flagged).
                var doc = orch.getContext().isPresent() ? orch.getContext().get().getDocument() : null;
                if (doc != null) {
                    var parsed = doc.getParsedSlideData(slide.getSlideNumber(),
                        (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
                    if (parsed != null) {
                        long placeholders = parsed.getShapeRegistry().getAllShapes().stream()
                            .filter(s -> s.getType()
                                == com.excudo.core.model.SlideShape.ShapeType.PLACEHOLDER).count();
                        long userAdded = parsed.getShapeRegistry().getAllShapes().size() - placeholders;
                        properties.add(row("User-added Shapes", String.valueOf(userAdded)));
                        properties.add(row("Placeholders", String.valueOf(placeholders)));
                    }
                }
            } catch (Exception ignored) {
                // Tolerate partial failures: the core metadata already
                // populated above gives the user something useful.
            }
        }
    }

    /**
     * Display properties for a shape. Uses the shape registry + typed
     * {@link com.excudo.core.introspection.ShapeStyleReader} so the
     * panel matches {@code get_shape_detail}'s agent-visible view.
     */
    public void displayShapeProperties(int slideNumber, int spid) {
        properties.clear();
        var orch = mainController != null ? mainController.getCurrentOrchestrator() : null;
        if (orch == null) {
            updateTitle("Properties");
            return;
        }
        try {
            var doc = orch.getContext().isPresent() ? orch.getContext().get().getDocument() : null;
            if (doc == null) {
                updateTitle("Properties");
                return;
            }
            var parsed = doc.getParsedSlideData(slideNumber,
                (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
            if (parsed == null) {
                updateTitle("Properties");
                return;
            }
            var shape = parsed.getShapeRegistry().getShape(spid);
            if (shape == null) {
                updateTitle("Properties");
                return;
            }
            currentSelection = shape;
            updateTitle("SPID " + spid + " (" + shape.getType() + ")");
            properties.add(row("SPID", String.valueOf(shape.getSpid())));
            properties.add(row("Name", shape.getName() != null ? shape.getName() : "(unnamed)"));
            properties.add(row("Type", shape.getType().name()));
            if (shape.getGeometry() != null) {
                var g = shape.getGeometry();
                properties.add(row("Position", String.format("x=%d  y=%d EMU (%.2fin, %.2fin)",
                    g.getX(), g.getY(),
                    g.getX() / 914400.0, g.getY() / 914400.0)));
                properties.add(row("Size", String.format("w=%d  h=%d EMU (%.2fin, %.2fin)",
                    g.getWidth(), g.getHeight(),
                    g.getWidth() / 914400.0, g.getHeight() / 914400.0)));
                if (g.getRotation() != 0) {
                    properties.add(row("Rotation", String.format("%.2f°", g.getRotationDegrees())));
                }
            }
            int parent = parsed.getShapeRegistry().getParentSpid(spid);
            if (parent > 0) properties.add(row("Parent Group", String.valueOf(parent)));
            if (shape.isTextBox()) properties.add(row("TextBox Flag", "true"));
            if (shape.hasText()) {
                String text = shape.getTextContent();
                if (text.length() > 120) text = text.substring(0, 120) + "…";
                properties.add(row("Text", text));
            }
            // Style via introspection surface.
            var introspector = new com.excudo.core.introspection.SlideIntrospector(orch);
            var style = introspector.getShapeStyle(slideNumber, spid);
            if (style != null) {
                if (style.getFill() != null) {
                    properties.add(row("Fill", style.getFill().getType()
                        + (style.getFill().getColor() != null ? " " + colorSummary(style.getFill().getColor()) : "")));
                }
                if (style.getLine() != null) {
                    properties.add(row("Line", (style.getLine().getWidthEMU() != null
                        ? style.getLine().getWidthEMU() + " EMU " : "")
                        + (style.getLine().getDashStyle() != null ? style.getLine().getDashStyle() : "solid")
                        + (style.getLine().getColor() != null ? " " + colorSummary(style.getLine().getColor()) : "")));
                }
            }
            // Animations targeting this SPID.
            var anims = introspector.listAnimations(slideNumber);
            long animsOnShape = anims.stream().filter(a -> a.getTargetSpid() == spid).count();
            if (animsOnShape > 0) {
                properties.add(row("Animations", String.valueOf(animsOnShape)));
            }
        } catch (Exception ignored) {
            // Best-effort: show whatever got populated before the error.
        }
    }

    private static String colorSummary(com.excudo.core.model.TextColor c) {
        return c.isScheme() ? "scheme:" + c.getSchemeVal() : "#" + c.getHexVal();
    }

    private static PropertyItem row(String name, String value) {
        return new PropertyItem(name, value != null ? value : "(null)", false);
    }
    
    /**
     * Clear properties display
     */
    public void clearProperties() {
        currentSelection = null;
        properties.clear();
        updateTitle("Properties");
    }
    
    /**
     * Refresh current properties
     */
    @FXML
    private void refreshProperties() {
        if (currentSelection instanceof SlideMetadata meta) {
            displaySlideProperties(meta);
        } else if (currentSelection instanceof com.excudo.core.model.SlideShape shape) {
            // Re-resolve via orchestrator so the snapshot is fresh
            // after mutations; the stored instance may be stale.
            var orch = mainController != null ? mainController.getCurrentOrchestrator() : null;
            if (orch != null) {
                // Slide number isn't carried on SlideShape; rely on the
                // active slide selection indirectly by walking every
                // slide for the SPID. Cheap because the parse cache is
                // warm.
                int slideNum = findSlideForSpid(shape.getSpid());
                if (slideNum > 0) displayShapeProperties(slideNum, shape.getSpid());
            }
        }
    }

    private int findSlideForSpid(int spid) {
        var orch = mainController != null ? mainController.getCurrentOrchestrator() : null;
        if (orch == null || orch.getContext().isEmpty()) return -1;
        var doc = orch.getContext().get().getDocument();
        if (doc == null) return -1;
        for (int i : doc.getSlideNumbers()) {
            var parsed = doc.getParsedSlideData(i,
                (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
            if (parsed != null && parsed.getShapeRegistry().getShape(spid) != null) {
                return i;
            }
        }
        return -1;
    }
    
    // ========== PROPERTY EDITING ==========
    
    /**
     * Handle property value change
     */
    private void handlePropertyChanged(PropertyItem item) {
        if (!item.isEditable()) {
            return;
        }
        
        // TODO: Implement actual property updates through orchestrator
        System.out.println("Property changed: " + item.getName() + " = " + item.getValue());
        
        if (mainController != null) {
            mainController.onSlideContentChanged();
        }
    }
    
    // ========== UTILITY METHODS ==========
    
    /**
     * Update properties panel title
     */
    private void updateTitle(String title) {
        if (propertiesTitle != null) {
            propertiesTitle.setText(title);
        }
    }
    
    // ========== PROPERTY ITEM CLASS ==========
    
    /**
     * Property item for table display
     */
    public static class PropertyItem {
        private final SimpleStringProperty name;
        private final SimpleStringProperty value;
        private final boolean editable;
        
        public PropertyItem(String name, String value, boolean editable) {
            this.name = new SimpleStringProperty(name);
            this.value = new SimpleStringProperty(value);
            this.editable = editable;
        }
        
        public String getName() { return name.get(); }
        public void setName(String name) { this.name.set(name); }
        public SimpleStringProperty nameProperty() { return name; }
        
        public String getValue() { return value.get(); }
        public void setValue(String value) { this.value.set(value); }
        public SimpleStringProperty valueProperty() { return value; }
        
        public boolean isEditable() { return editable; }
    }
}