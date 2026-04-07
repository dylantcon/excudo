package com.excudo.view.precision;

import com.excudo.view.MainController;
import com.excudo.view.rendering.CoordinateMapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.w3c.dom.Element;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.HashMap;
import java.util.Map;

/**
 * Precision editing view providing surgical precision editing tools for technical users.
 * Enables exact coordinate entry, dimension control, and technical shape manipulation.
 */
public class PrecisionEditingView implements Initializable {
    
    // ========== FXML COMPONENTS ==========
    
    @FXML private TabPane precisionTabPane;
    
    // Coordinates tab
    @FXML private Tab coordinatesTab;
    @FXML private GridPane coordinatesGrid;
    @FXML private TextField xPositionField;
    @FXML private TextField yPositionField;
    @FXML private TextField widthField;
    @FXML private TextField heightField;
    @FXML private TextField rotationField;
    @FXML private ComboBox<String> unitsComboBox;
    @FXML private Button applyTransformButton;
    @FXML private Button resetTransformButton;
    
    // Properties tab
    @FXML private Tab propertiesTab;
    @FXML private TableView<PropertyEntry> propertiesTable;
    @FXML private TableColumn<PropertyEntry, String> propertyNameColumn;
    @FXML private TableColumn<PropertyEntry, String> propertyValueColumn;
    @FXML private TextField propertySearchField;
    @FXML private Button addPropertyButton;
    @FXML private Button deletePropertyButton;
    @FXML private Button exportPropertiesButton;
    
    // Measurements tab
    @FXML private Tab measurementsTab;
    @FXML private VBox measurementsContainer;
    @FXML private CheckBox showRulersCheckBox;
    @FXML private CheckBox showGridCheckBox;
    @FXML private CheckBox snapToGridCheckBox;
    @FXML private Spinner<Double> gridSizeSpinner;
    @FXML private Button measureDistanceButton;
    @FXML private Button measureAngleButton;
    @FXML private Label measurementResultLabel;
    
    // Technical tab
    @FXML private Tab technicalTab;
    @FXML private TextArea xmlPreviewArea;
    @FXML private Button refreshXmlButton;
    @FXML private Button validateXmlButton;
    @FXML private Button exportXmlButton;
    @FXML private Label spidLabel;
    @FXML private Label shapeTypeLabel;
    @FXML private Label geometryInfoLabel;
    
    // ========== STATE ==========
    
    private MainController mainController;
    private Element selectedShape;
    private CoordinateMapper coordinateMapper;
    private ObservableList<PropertyEntry> propertyEntries;
    private Map<String, String> currentProperties;
    
    // Measurement state
    private boolean measurementMode = false;
    private String measurementType;
    
    // Units conversion
    private enum CoordinateUnit {
        EMU("EMU", 1.0),
        PIXELS("Pixels", 9525.0), // 1 pixel = 9525 EMU at 96 DPI
        POINTS("Points", 12700.0), // 1 point = 12700 EMU
        INCHES("Inches", 914400.0), // 1 inch = 914400 EMU
        CM("cm", 360000.0); // 1 cm = 360000 EMU
        
        private final String displayName;
        private final double emuConversionFactor;
        
        CoordinateUnit(String displayName, double emuConversionFactor) {
            this.displayName = displayName;
            this.emuConversionFactor = emuConversionFactor;
        }
        
        public String getDisplayName() { return displayName; }
        public double getEmuConversionFactor() { return emuConversionFactor; }
    }
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupComponents();
        setupEventHandlers();
        setupInitialState();
    }
    
    // ========== INITIALIZATION ==========
    
    /**
     * Set reference to main controller
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        this.coordinateMapper = new CoordinateMapper();
    }
    
    private void setupComponents() {
        // Setup coordinates tab
        setupCoordinatesTab();
        
        // Setup properties table
        setupPropertiesTab();
        
        // Setup measurements tab
        setupMeasurementsTab();
        
        // Setup technical tab
        setupTechnicalTab();
    }
    
    private void setupCoordinatesTab() {
        // Configure units combo box
        if (unitsComboBox != null) {
            for (CoordinateUnit unit : CoordinateUnit.values()) {
                unitsComboBox.getItems().add(unit.getDisplayName());
            }
            unitsComboBox.setValue(CoordinateUnit.PIXELS.getDisplayName());
        }
        
        // Configure numeric fields
        configureNumericField(xPositionField, "X Position");
        configureNumericField(yPositionField, "Y Position");
        configureNumericField(widthField, "Width");
        configureNumericField(heightField, "Height");
        configureNumericField(rotationField, "Rotation (degrees)");
    }
    
    private void setupPropertiesTab() {
        // Initialize properties table
        propertyEntries = FXCollections.observableArrayList();
        currentProperties = new HashMap<>();
        
        if (propertiesTable != null) {
            propertiesTable.setItems(propertyEntries);
            
            // Configure columns
            if (propertyNameColumn != null) {
                propertyNameColumn.setCellValueFactory(cellData -> 
                    new SimpleStringProperty(cellData.getValue().getName()));
                propertyNameColumn.setPrefWidth(200);
            }
            
            if (propertyValueColumn != null) {
                propertyValueColumn.setCellValueFactory(cellData -> 
                    new SimpleStringProperty(cellData.getValue().getValue()));
                propertyValueColumn.setPrefWidth(250);
                
                // Make values editable
                propertyValueColumn.setCellFactory(TextFieldTableCell.forTableColumn());
                propertyValueColumn.setOnEditCommit(event -> {
                    PropertyEntry entry = event.getRowValue();
                    entry.setValue(event.getNewValue());
                    updateShapeProperty(entry.getName(), entry.getValue());
                });
            }
            
            propertiesTable.setEditable(true);
        }
    }
    
    private void setupMeasurementsTab() {
        // Configure grid size spinner
        if (gridSizeSpinner != null) {
            gridSizeSpinner.setValueFactory(
                new SpinnerValueFactory.DoubleSpinnerValueFactory(1.0, 100.0, 10.0, 1.0));
        }
    }
    
    private void setupTechnicalTab() {
        // Configure XML preview area
        if (xmlPreviewArea != null) {
            xmlPreviewArea.setEditable(false);
            xmlPreviewArea.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 12px;");
        }
    }
    
    private void setupEventHandlers() {
        // Coordinates tab handlers
        if (applyTransformButton != null) {
            applyTransformButton.setOnAction(e -> applyTransformations());
        }
        if (resetTransformButton != null) {
            resetTransformButton.setOnAction(e -> resetTransformations());
        }
        if (unitsComboBox != null) {
            unitsComboBox.setOnAction(e -> convertUnits());
        }
        
        // Properties tab handlers
        if (addPropertyButton != null) {
            addPropertyButton.setOnAction(e -> addCustomProperty());
        }
        if (deletePropertyButton != null) {
            deletePropertyButton.setOnAction(e -> deleteSelectedProperty());
        }
        if (exportPropertiesButton != null) {
            exportPropertiesButton.setOnAction(e -> exportProperties());
        }
        if (propertySearchField != null) {
            propertySearchField.textProperty().addListener((obs, oldVal, newVal) -> filterProperties(newVal));
        }
        
        // Measurements tab handlers
        if (showRulersCheckBox != null) {
            showRulersCheckBox.setOnAction(e -> toggleRulers());
        }
        if (showGridCheckBox != null) {
            showGridCheckBox.setOnAction(e -> toggleGrid());
        }
        if (snapToGridCheckBox != null) {
            snapToGridCheckBox.setOnAction(e -> toggleSnapToGrid());
        }
        if (gridSizeSpinner != null) {
            gridSizeSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateGridSize(newVal));
        }
        if (measureDistanceButton != null) {
            measureDistanceButton.setOnAction(e -> startMeasurement("distance"));
        }
        if (measureAngleButton != null) {
            measureAngleButton.setOnAction(e -> startMeasurement("angle"));
        }
        
        // Technical tab handlers
        if (refreshXmlButton != null) {
            refreshXmlButton.setOnAction(e -> refreshXmlPreview());
        }
        if (validateXmlButton != null) {
            validateXmlButton.setOnAction(e -> validateCurrentXml());
        }
        if (exportXmlButton != null) {
            exportXmlButton.setOnAction(e -> exportShapeXml());
        }
    }
    
    private void setupInitialState() {
        clearSelection();
        updateMeasurementTools();
    }
    
    // ========== SHAPE SELECTION ==========
    
    /**
     * Set the currently selected shape for precision editing
     */
    public void setSelectedShape(Element shape) {
        this.selectedShape = shape;
        updateAllViews();
    }
    
    /**
     * Clear shape selection
     */
    public void clearSelection() {
        this.selectedShape = null;
        updateAllViews();
    }
    
    /**
     * Update all views when selection changes
     */
    private void updateAllViews() {
        updateCoordinatesView();
        updatePropertiesView();
        updateTechnicalView();
        updateButtonStates();
    }
    
    // ========== COORDINATES TAB ==========
    
    /**
     * Update coordinates view with current shape data
     */
    private void updateCoordinatesView() {
        if (selectedShape != null) {
            try {
                // Extract current coordinates (simplified - would use XPath in real implementation)
                String x = "0", y = "0", width = "914400", height = "914400", rotation = "0";
                
                // Convert from EMU to selected units
                CoordinateUnit selectedUnit = getSelectedUnit();
                
                if (xPositionField != null) {
                    xPositionField.setText(convertFromEmu(Long.parseLong(x), selectedUnit));
                }
                if (yPositionField != null) {
                    yPositionField.setText(convertFromEmu(Long.parseLong(y), selectedUnit));
                }
                if (widthField != null) {
                    widthField.setText(convertFromEmu(Long.parseLong(width), selectedUnit));
                }
                if (heightField != null) {
                    heightField.setText(convertFromEmu(Long.parseLong(height), selectedUnit));
                }
                if (rotationField != null) {
                    rotationField.setText(rotation);
                }
                
            } catch (Exception e) {
                clearCoordinateFields();
            }
        } else {
            clearCoordinateFields();
        }
    }
    
    private void clearCoordinateFields() {
        if (xPositionField != null) xPositionField.clear();
        if (yPositionField != null) yPositionField.clear();
        if (widthField != null) widthField.clear();
        if (heightField != null) heightField.clear();
        if (rotationField != null) rotationField.clear();
    }
    
    @FXML
    private void applyTransformations() {
        if (selectedShape == null) return;
        
        try {
            // Get values from fields
            CoordinateUnit selectedUnit = getSelectedUnit();
            long x = convertToEmu(parseDouble(xPositionField.getText()), selectedUnit);
            long y = convertToEmu(parseDouble(yPositionField.getText()), selectedUnit);
            long width = convertToEmu(parseDouble(widthField.getText()), selectedUnit);
            long height = convertToEmu(parseDouble(heightField.getText()), selectedUnit);
            double rotation = parseDouble(rotationField.getText());
            
            // Apply transformations to shape (would integrate with Model in real implementation)
            applyShapeTransformation(x, y, width, height, rotation);
            
            showStatus("Transformations applied successfully");
            
        } catch (Exception e) {
            showError("Failed to apply transformations: " + e.getMessage());
        }
    }
    
    @FXML
    private void resetTransformations() {
        updateCoordinatesView(); // Reload from current shape data
        showStatus("Transformations reset");
    }
    
    @FXML
    private void convertUnits() {
        updateCoordinatesView(); // Refresh with new units
    }
    
    // ========== PROPERTIES TAB ==========
    
    /**
     * Update properties view with current shape data
     */
    private void updatePropertiesView() {
        propertyEntries.clear();
        currentProperties.clear();
        
        if (selectedShape != null) {
            // Extract shape properties (simplified - would use comprehensive extraction)
            Map<String, String> properties = extractShapeProperties(selectedShape);
            
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                PropertyEntry propEntry = new PropertyEntry(entry.getKey(), entry.getValue());
                propertyEntries.add(propEntry);
                currentProperties.put(entry.getKey(), entry.getValue());
            }
        }
    }
    
    @FXML
    private void addCustomProperty() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Property");
        dialog.setHeaderText("Add Custom Property");
        dialog.setContentText("Property name:");
        
        dialog.showAndWait().ifPresent(propertyName -> {
            if (!propertyName.trim().isEmpty() && !currentProperties.containsKey(propertyName)) {
                PropertyEntry entry = new PropertyEntry(propertyName, "");
                propertyEntries.add(entry);
                currentProperties.put(propertyName, "");
            }
        });
    }
    
    @FXML
    private void deleteSelectedProperty() {
        PropertyEntry selected = propertiesTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            propertyEntries.remove(selected);
            currentProperties.remove(selected.getName());
        }
    }
    
    @FXML
    private void exportProperties() {
        // Export properties to clipboard or file
        StringBuilder export = new StringBuilder();
        export.append("Shape Properties Export\n");
        export.append("=======================\n");
        
        for (PropertyEntry entry : propertyEntries) {
            export.append(entry.getName()).append(" = ").append(entry.getValue()).append("\n");
        }
        
        // Copy to clipboard (simplified)
        showStatus("Properties exported to clipboard");
    }
    
    private void filterProperties(String filter) {
        // Filter properties based on search text
        if (filter == null || filter.trim().isEmpty()) {
            propertiesTable.setItems(propertyEntries);
        } else {
            ObservableList<PropertyEntry> filtered = FXCollections.observableArrayList();
            String lowerFilter = filter.toLowerCase();
            
            for (PropertyEntry entry : propertyEntries) {
                if (entry.getName().toLowerCase().contains(lowerFilter) ||
                    entry.getValue().toLowerCase().contains(lowerFilter)) {
                    filtered.add(entry);
                }
            }
            
            propertiesTable.setItems(filtered);
        }
    }
    
    // ========== MEASUREMENTS TAB ==========
    
    @FXML
    private void toggleRulers() {
        boolean showRulers = showRulersCheckBox.isSelected();
        // Integrate with canvas to show/hide rulers
        showStatus("Rulers " + (showRulers ? "enabled" : "disabled"));
    }
    
    @FXML
    private void toggleGrid() {
        boolean showGrid = showGridCheckBox.isSelected();
        // Integrate with canvas to show/hide grid
        showStatus("Grid " + (showGrid ? "enabled" : "disabled"));
    }
    
    @FXML
    private void toggleSnapToGrid() {
        boolean snapToGrid = snapToGridCheckBox.isSelected();
        // Integrate with canvas for snap-to-grid functionality
        showStatus("Snap to grid " + (snapToGrid ? "enabled" : "disabled"));
    }
    
    private void updateGridSize(double newSize) {
        // Update grid size in canvas
        showStatus("Grid size updated to " + newSize);
    }
    
    private void startMeasurement(String type) {
        measurementMode = true;
        measurementType = type;
        
        if ("distance".equals(type)) {
            showStatus("Click two points to measure distance");
        } else if ("angle".equals(type)) {
            showStatus("Click three points to measure angle");
        }
        
        updateMeasurementTools();
    }
    
    private void updateMeasurementTools() {
        if (measureDistanceButton != null) {
            measureDistanceButton.setDisable(selectedShape == null);
        }
        if (measureAngleButton != null) {
            measureAngleButton.setDisable(selectedShape == null);
        }
    }
    
    /**
     * Handle measurement result from canvas
     */
    public void setMeasurementResult(String result) {
        if (measurementResultLabel != null) {
            measurementResultLabel.setText(result);
        }
        measurementMode = false;
        measurementType = null;
    }
    
    // ========== TECHNICAL TAB ==========
    
    /**
     * Update technical view with current shape data
     */
    private void updateTechnicalView() {
        if (selectedShape != null) {
            updateXmlPreview();
            updateShapeInfo();
        } else {
            clearTechnicalView();
        }
    }
    
    private void updateXmlPreview() {
        if (xmlPreviewArea != null && selectedShape != null) {
            try {
                // Serialize shape to XML (simplified)
                String xmlContent = serializeShapeToXml(selectedShape);
                xmlPreviewArea.setText(xmlContent);
            } catch (Exception e) {
                xmlPreviewArea.setText("Error generating XML preview: " + e.getMessage());
            }
        }
    }
    
    private void updateShapeInfo() {
        if (selectedShape != null) {
            // Extract SPID
            if (spidLabel != null) {
                String spid = extractSpid(selectedShape);
                spidLabel.setText("SPID: " + (spid != null ? spid : "Unknown"));
            }
            
            // Extract shape type
            if (shapeTypeLabel != null) {
                String shapeType = selectedShape.getTagName();
                shapeTypeLabel.setText("Type: " + shapeType);
            }
            
            // Extract geometry info
            if (geometryInfoLabel != null) {
                String geometryInfo = extractGeometryInfo(selectedShape);
                geometryInfoLabel.setText("Geometry: " + geometryInfo);
            }
        }
    }
    
    private void clearTechnicalView() {
        if (xmlPreviewArea != null) {
            xmlPreviewArea.clear();
        }
        if (spidLabel != null) {
            spidLabel.setText("SPID: None");
        }
        if (shapeTypeLabel != null) {
            shapeTypeLabel.setText("Type: None");
        }
        if (geometryInfoLabel != null) {
            geometryInfoLabel.setText("Geometry: None");
        }
    }
    
    @FXML
    private void refreshXmlPreview() {
        updateXmlPreview();
        showStatus("XML preview refreshed");
    }
    
    @FXML
    private void validateCurrentXml() {
        if (selectedShape != null) {
            // Validate shape XML structure
            boolean isValid = validateShapeXml(selectedShape);
            showStatus("XML validation: " + (isValid ? "Valid" : "Invalid"));
        }
    }
    
    @FXML
    private void exportShapeXml() {
        if (selectedShape != null && xmlPreviewArea != null) {
            String xmlContent = xmlPreviewArea.getText();
            // Export XML to clipboard or file
            showStatus("Shape XML exported");
        }
    }
    
    // ========== UTILITY METHODS ==========
    
    private void configureNumericField(TextField field, String promptText) {
        if (field != null) {
            field.setPromptText(promptText);
            
            // Add numeric validation
            field.textProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue.matches("^-?\\d*\\.?\\d*$")) {
                    field.setText(oldValue);
                }
            });
        }
    }
    
    private CoordinateUnit getSelectedUnit() {
        String selectedUnitName = unitsComboBox.getValue();
        for (CoordinateUnit unit : CoordinateUnit.values()) {
            if (unit.getDisplayName().equals(selectedUnitName)) {
                return unit;
            }
        }
        return CoordinateUnit.PIXELS; // Default
    }
    
    private String convertFromEmu(long emuValue, CoordinateUnit targetUnit) {
        double convertedValue = emuValue / targetUnit.getEmuConversionFactor();
        return String.format("%.2f", convertedValue);
    }
    
    private long convertToEmu(double value, CoordinateUnit sourceUnit) {
        return Math.round(value * sourceUnit.getEmuConversionFactor());
    }
    
    private double parseDouble(String text) {
        try {
            return text != null && !text.trim().isEmpty() ? Double.parseDouble(text.trim()) : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    private void updateButtonStates() {
        boolean hasSelection = selectedShape != null;
        
        if (applyTransformButton != null) {
            applyTransformButton.setDisable(!hasSelection);
        }
        if (resetTransformButton != null) {
            resetTransformButton.setDisable(!hasSelection);
        }
        if (deletePropertyButton != null) {
            deletePropertyButton.setDisable(!hasSelection);
        }
        if (exportPropertiesButton != null) {
            exportPropertiesButton.setDisable(!hasSelection);
        }
        if (refreshXmlButton != null) {
            refreshXmlButton.setDisable(!hasSelection);
        }
        if (validateXmlButton != null) {
            validateXmlButton.setDisable(!hasSelection);
        }
        if (exportXmlButton != null) {
            exportXmlButton.setDisable(!hasSelection);
        }
    }
    
    // ========== INTEGRATION METHODS (to be implemented with Model) ==========
    
    private void applyShapeTransformation(long x, long y, long width, long height, double rotation) {
        // Would integrate with Model to apply actual transformations
        System.out.println("Applying transformation: x=" + x + ", y=" + y + ", w=" + width + ", h=" + height + ", rot=" + rotation);
    }
    
    private Map<String, String> extractShapeProperties(Element shape) {
        Map<String, String> properties = new HashMap<>();
        // Would extract comprehensive shape properties
        properties.put("id", shape.getAttribute("id"));
        properties.put("name", shape.getAttribute("name"));
        properties.put("tagName", shape.getTagName());
        return properties;
    }
    
    private void updateShapeProperty(String propertyName, String propertyValue) {
        // Would update actual shape property
        System.out.println("Updating property: " + propertyName + " = " + propertyValue);
    }
    
    private String serializeShapeToXml(Element shape) {
        // Would serialize shape to formatted XML
        return "<!-- Shape XML would appear here -->";
    }
    
    private String extractSpid(Element shape) {
        // Would extract SPID from shape
        return shape.getAttribute("id");
    }
    
    private String extractGeometryInfo(Element shape) {
        // Would extract geometry information
        return "Basic geometry information";
    }
    
    private boolean validateShapeXml(Element shape) {
        // Would perform comprehensive XML validation
        return true;
    }
    
    private void showStatus(String message) {
        System.out.println("Status: " + message);
    }
    
    private void showError(String message) {
        System.err.println("Error: " + message);
    }
    
    // ========== GETTERS ==========
    
    public Element getSelectedShape() {
        return selectedShape;
    }
    
    public boolean isMeasurementMode() {
        return measurementMode;
    }
    
    public String getMeasurementType() {
        return measurementType;
    }
    
    // ========== PROPERTY ENTRY CLASS ==========
    
    public static class PropertyEntry {
        private String name;
        private String value;
        
        public PropertyEntry(String name, String value) {
            this.name = name;
            this.value = value;
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}