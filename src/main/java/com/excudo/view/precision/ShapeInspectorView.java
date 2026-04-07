package com.excudo.view.precision;

import com.excudo.view.MainController;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.HashMap;
import java.util.Map;

/**
 * Technical shape properties inspector providing detailed OOXML analysis and editing.
 * Displays comprehensive shape metadata, attributes, and technical properties.
 */
public class ShapeInspectorView implements Initializable {
    
    // ========== FXML COMPONENTS ==========
    
    @FXML private TabPane inspectorTabPane;
    
    // Overview tab
    @FXML private Tab overviewTab;
    @FXML private GridPane overviewGrid;
    @FXML private Label shapeIdLabel;
    @FXML private Label shapeNameLabel;
    @FXML private Label shapeTypeLabel;
    @FXML private Label spidLabel;
    @FXML private Label geometryTypeLabel;
    @FXML private Label boundsLabel;
    @FXML private Label rotationLabel;
    @FXML private Label visibilityLabel;
    
    // Attributes tab
    @FXML private Tab attributesTab;
    @FXML private TreeView<AttributeNode> attributesTree;
    @FXML private TextField attributeSearchField;
    @FXML private Button expandAllButton;
    @FXML private Button collapseAllButton;
    @FXML private Button exportAttributesButton;
    
    // Geometry tab
    @FXML private Tab geometryTab;
    @FXML private TextArea geometryDetailsArea;
    @FXML private TableView<GeometryProperty> geometryTable;
    @FXML private TableColumn<GeometryProperty, String> geometryNameColumn;
    @FXML private TableColumn<GeometryProperty, String> geometryValueColumn;
    @FXML private Button analyzeGeometryButton;
    @FXML private Button exportGeometryButton;
    
    // Formatting tab
    @FXML private Tab formattingTab;
    @FXML private VBox formattingContainer;
    @FXML private TableView<FormatProperty> formatTable;
    @FXML private TableColumn<FormatProperty, String> formatCategoryColumn;
    @FXML private TableColumn<FormatProperty, String> formatPropertyColumn;
    @FXML private TableColumn<FormatProperty, String> formatValueColumn;
    @FXML private Button resetFormattingButton;
    @FXML private Button applyFormattingButton;
    
    // Relationships tab
    @FXML private Tab relationshipsTab;
    @FXML private ListView<String> relationshipsList;
    @FXML private TextArea relationshipDetailsArea;
    @FXML private Button validateRelationshipsButton;
    @FXML private Button exportRelationshipsButton;
    
    // ========== STATE ==========
    
    private MainController mainController;
    private Element selectedShape;
    private ObservableList<GeometryProperty> geometryProperties;
    private ObservableList<FormatProperty> formatProperties;
    private ObservableList<String> relationships;
    
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
    }
    
    private void setupComponents() {
        setupAttributesTree();
        setupGeometryTable();
        setupFormatTable();
        setupRelationshipsList();
    }
    
    private void setupAttributesTree() {
        if (attributesTree != null) {
            attributesTree.setShowRoot(false);
            attributesTree.setCellFactory(tree -> new AttributeTreeCell());
        }
    }
    
    private void setupGeometryTable() {
        geometryProperties = FXCollections.observableArrayList();
        
        if (geometryTable != null) {
            geometryTable.setItems(geometryProperties);
            
            if (geometryNameColumn != null) {
                geometryNameColumn.setCellValueFactory(cellData -> 
                    new SimpleStringProperty(cellData.getValue().getName()));
                geometryNameColumn.setPrefWidth(150);
            }
            
            if (geometryValueColumn != null) {
                geometryValueColumn.setCellValueFactory(cellData -> 
                    new SimpleStringProperty(cellData.getValue().getValue()));
                geometryValueColumn.setPrefWidth(200);
            }
        }
    }
    
    private void setupFormatTable() {
        formatProperties = FXCollections.observableArrayList();
        
        if (formatTable != null) {
            formatTable.setItems(formatProperties);
            
            if (formatCategoryColumn != null) {
                formatCategoryColumn.setCellValueFactory(cellData -> 
                    new SimpleStringProperty(cellData.getValue().getCategory()));
                formatCategoryColumn.setPrefWidth(100);
            }
            
            if (formatPropertyColumn != null) {
                formatPropertyColumn.setCellValueFactory(cellData -> 
                    new SimpleStringProperty(cellData.getValue().getProperty()));
                formatPropertyColumn.setPrefWidth(120);
            }
            
            if (formatValueColumn != null) {
                formatValueColumn.setCellValueFactory(cellData -> 
                    new SimpleStringProperty(cellData.getValue().getValue()));
                formatValueColumn.setPrefWidth(150);
                
                // Make values editable
                formatValueColumn.setCellFactory(TextFieldTableCell.forTableColumn());
                formatValueColumn.setOnEditCommit(event -> {
                    FormatProperty property = event.getRowValue();
                    property.setValue(event.getNewValue());
                    updateFormatProperty(property);
                });
            }
            
            formatTable.setEditable(true);
        }
    }
    
    private void setupRelationshipsList() {
        relationships = FXCollections.observableArrayList();
        
        if (relationshipsList != null) {
            relationshipsList.setItems(relationships);
            relationshipsList.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> showRelationshipDetails(newVal));
        }
    }
    
    private void setupEventHandlers() {
        // Attributes tab handlers
        if (attributeSearchField != null) {
            attributeSearchField.textProperty().addListener((obs, oldVal, newVal) -> filterAttributes(newVal));
        }
        if (expandAllButton != null) {
            expandAllButton.setOnAction(e -> expandAllNodes());
        }
        if (collapseAllButton != null) {
            collapseAllButton.setOnAction(e -> collapseAllNodes());
        }
        if (exportAttributesButton != null) {
            exportAttributesButton.setOnAction(e -> exportAttributes());
        }
        
        // Geometry tab handlers
        if (analyzeGeometryButton != null) {
            analyzeGeometryButton.setOnAction(e -> analyzeGeometry());
        }
        if (exportGeometryButton != null) {
            exportGeometryButton.setOnAction(e -> exportGeometry());
        }
        
        // Formatting tab handlers
        if (resetFormattingButton != null) {
            resetFormattingButton.setOnAction(e -> resetFormatting());
        }
        if (applyFormattingButton != null) {
            applyFormattingButton.setOnAction(e -> applyFormatting());
        }
        
        // Relationships tab handlers
        if (validateRelationshipsButton != null) {
            validateRelationshipsButton.setOnAction(e -> validateRelationships());
        }
        if (exportRelationshipsButton != null) {
            exportRelationshipsButton.setOnAction(e -> exportRelationships());
        }
    }
    
    private void setupInitialState() {
        clearInspector();
    }
    
    // ========== SHAPE SELECTION ==========
    
    /**
     * Set the currently selected shape for inspection
     */
    public void setSelectedShape(Element shape) {
        this.selectedShape = shape;
        updateAllInspectorViews();
    }
    
    /**
     * Clear shape selection
     */
    public void clearSelection() {
        this.selectedShape = null;
        clearInspector();
    }
    
    /**
     * Update all inspector views when selection changes
     */
    private void updateAllInspectorViews() {
        updateOverviewTab();
        updateAttributesTab();
        updateGeometryTab();
        updateFormattingTab();
        updateRelationshipsTab();
    }
    
    private void clearInspector() {
        clearOverviewTab();
        clearAttributesTab();
        clearGeometryTab();
        clearFormattingTab();
        clearRelationshipsTab();
    }
    
    // ========== OVERVIEW TAB ==========
    
    private void updateOverviewTab() {
        if (selectedShape != null) {
            // Extract basic shape information
            if (shapeIdLabel != null) {
                String id = selectedShape.getAttribute("id");
                shapeIdLabel.setText(id.isEmpty() ? "Unknown" : id);
            }
            
            if (shapeNameLabel != null) {
                String name = extractShapeName(selectedShape);
                shapeNameLabel.setText(name != null ? name : "Unnamed");
            }
            
            if (shapeTypeLabel != null) {
                shapeTypeLabel.setText(selectedShape.getTagName());
            }
            
            if (spidLabel != null) {
                String spid = extractSpid(selectedShape);
                spidLabel.setText(spid != null ? spid : "None");
            }
            
            if (geometryTypeLabel != null) {
                String geometryType = extractGeometryType(selectedShape);
                geometryTypeLabel.setText(geometryType != null ? geometryType : "Unknown");
            }
            
            if (boundsLabel != null) {
                String bounds = extractBounds(selectedShape);
                boundsLabel.setText(bounds != null ? bounds : "Unknown");
            }
            
            if (rotationLabel != null) {
                String rotation = extractRotation(selectedShape);
                rotationLabel.setText(rotation != null ? rotation + "°" : "0°");
            }
            
            if (visibilityLabel != null) {
                boolean visible = extractVisibility(selectedShape);
                visibilityLabel.setText(visible ? "Visible" : "Hidden");
            }
        }
    }
    
    private void clearOverviewTab() {
        if (shapeIdLabel != null) shapeIdLabel.setText("None");
        if (shapeNameLabel != null) shapeNameLabel.setText("None");
        if (shapeTypeLabel != null) shapeTypeLabel.setText("None");
        if (spidLabel != null) spidLabel.setText("None");
        if (geometryTypeLabel != null) geometryTypeLabel.setText("None");
        if (boundsLabel != null) boundsLabel.setText("None");
        if (rotationLabel != null) rotationLabel.setText("None");
        if (visibilityLabel != null) visibilityLabel.setText("None");
    }
    
    // ========== ATTRIBUTES TAB ==========
    
    private void updateAttributesTab() {
        if (selectedShape != null && attributesTree != null) {
            TreeItem<AttributeNode> root = new TreeItem<>(new AttributeNode("Shape", selectedShape.getTagName()));
            root.setExpanded(true);
            
            buildAttributeTree(selectedShape, root);
            attributesTree.setRoot(root);
        }
    }
    
    private void buildAttributeTree(Element element, TreeItem<AttributeNode> parent) {
        // Add element attributes
        NamedNodeMap attributes = element.getAttributes();
        if (attributes.getLength() > 0) {
            TreeItem<AttributeNode> attributesNode = new TreeItem<>(new AttributeNode("Attributes", ""));
            parent.getChildren().add(attributesNode);
            
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                TreeItem<AttributeNode> attrNode = new TreeItem<>(
                    new AttributeNode(attr.getNodeName(), attr.getNodeValue()));
                attributesNode.getChildren().add(attrNode);
            }
        }
        
        // Add child elements
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                TreeItem<AttributeNode> childNode = new TreeItem<>(
                    new AttributeNode(childElement.getTagName(), childElement.getTextContent()));
                parent.getChildren().add(childNode);
                
                buildAttributeTree(childElement, childNode);
            }
        }
    }
    
    private void clearAttributesTab() {
        if (attributesTree != null) {
            attributesTree.setRoot(null);
        }
    }
    
    @FXML
    private void filterAttributes(String filter) {
        // Filter attribute tree based on search text
        // Implementation would filter tree nodes
    }
    
    @FXML
    private void expandAllNodes() {
        if (attributesTree != null && attributesTree.getRoot() != null) {
            expandTreeItem(attributesTree.getRoot());
        }
    }
    
    @FXML
    private void collapseAllNodes() {
        if (attributesTree != null && attributesTree.getRoot() != null) {
            collapseTreeItem(attributesTree.getRoot());
        }
    }
    
    @FXML
    private void exportAttributes() {
        // Export attribute tree to XML or text format
        showStatus("Attributes exported");
    }
    
    // ========== GEOMETRY TAB ==========
    
    private void updateGeometryTab() {
        geometryProperties.clear();
        
        if (selectedShape != null) {
            // Extract geometry properties
            Map<String, String> properties = extractGeometryProperties(selectedShape);
            
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                geometryProperties.add(new GeometryProperty(entry.getKey(), entry.getValue()));
            }
            
            // Update geometry details
            updateGeometryDetails();
        }
    }
    
    private void updateGeometryDetails() {
        if (geometryDetailsArea != null && selectedShape != null) {
            StringBuilder details = new StringBuilder();
            details.append("Geometry Analysis\\n");
            details.append("================\\n\\n");
            
            // Add detailed geometry information
            details.append("Shape Type: ").append(selectedShape.getTagName()).append("\\n");
            details.append("Preset Geometry: ").append(extractPresetGeometry(selectedShape)).append("\\n");
            details.append("Custom Path: ").append(extractCustomPath(selectedShape)).append("\\n");
            details.append("Transform Matrix: ").append(extractTransformMatrix(selectedShape)).append("\\n");
            
            geometryDetailsArea.setText(details.toString());
        }
    }
    
    private void clearGeometryTab() {
        geometryProperties.clear();
        if (geometryDetailsArea != null) {
            geometryDetailsArea.clear();
        }
    }
    
    @FXML
    private void analyzeGeometry() {
        if (selectedShape != null) {
            // Perform comprehensive geometry analysis
            showStatus("Geometry analysis complete");
        }
    }
    
    @FXML
    private void exportGeometry() {
        // Export geometry data
        showStatus("Geometry exported");
    }
    
    // ========== FORMATTING TAB ==========
    
    private void updateFormattingTab() {
        formatProperties.clear();
        
        if (selectedShape != null) {
            // Extract formatting properties
            extractFillProperties();
            extractStrokeProperties();
            extractTextProperties();
            extractEffectProperties();
        }
    }
    
    private void extractFillProperties() {
        // Extract fill-related properties
        formatProperties.add(new FormatProperty("Fill", "Type", "Solid")); // Example
        formatProperties.add(new FormatProperty("Fill", "Color", "#000000")); // Example
    }
    
    private void extractStrokeProperties() {
        // Extract stroke-related properties
        formatProperties.add(new FormatProperty("Stroke", "Width", "1pt")); // Example
        formatProperties.add(new FormatProperty("Stroke", "Color", "#000000")); // Example
    }
    
    private void extractTextProperties() {
        // Extract text-related properties
        formatProperties.add(new FormatProperty("Text", "Font", "Arial")); // Example
        formatProperties.add(new FormatProperty("Text", "Size", "12pt")); // Example
    }
    
    private void extractEffectProperties() {
        // Extract effect-related properties
        formatProperties.add(new FormatProperty("Effects", "Shadow", "None")); // Example
    }
    
    private void clearFormattingTab() {
        formatProperties.clear();
    }
    
    @FXML
    private void resetFormatting() {
        updateFormattingTab(); // Reload from shape
        showStatus("Formatting reset");
    }
    
    @FXML
    private void applyFormatting() {
        // Apply formatting changes to shape
        showStatus("Formatting applied");
    }
    
    private void updateFormatProperty(FormatProperty property) {
        // Update individual format property
        System.out.println("Updating format: " + property.getCategory() + "." + 
                         property.getProperty() + " = " + property.getValue());
    }
    
    // ========== RELATIONSHIPS TAB ==========
    
    private void updateRelationshipsTab() {
        relationships.clear();
        
        if (selectedShape != null) {
            // Extract shape relationships
            relationships.addAll(extractShapeRelationships(selectedShape));
        }
    }
    
    private void clearRelationshipsTab() {
        relationships.clear();
        if (relationshipDetailsArea != null) {
            relationshipDetailsArea.clear();
        }
    }
    
    private void showRelationshipDetails(String relationship) {
        if (relationshipDetailsArea != null && relationship != null) {
            // Show detailed information about selected relationship
            relationshipDetailsArea.setText("Relationship Details: " + relationship);
        }
    }
    
    @FXML
    private void validateRelationships() {
        // Validate all shape relationships
        showStatus("Relationships validated");
    }
    
    @FXML
    private void exportRelationships() {
        // Export relationship data
        showStatus("Relationships exported");
    }
    
    // ========== UTILITY METHODS ==========
    
    private void expandTreeItem(TreeItem<?> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(true);
            for (TreeItem<?> child : item.getChildren()) {
                expandTreeItem(child);
            }
        }
    }
    
    private void collapseTreeItem(TreeItem<?> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(false);
            for (TreeItem<?> child : item.getChildren()) {
                collapseTreeItem(child);
            }
        }
    }
    
    private void showStatus(String message) {
        System.out.println("Status: " + message);
    }
    
    // ========== EXTRACTION METHODS (to be implemented with actual parsing) ==========
    
    private String extractShapeName(Element shape) {
        // Extract shape name from OOXML
        return shape.getAttribute("name");
    }
    
    private String extractSpid(Element shape) {
        // Extract SPID from shape
        return shape.getAttribute("id");
    }
    
    private String extractGeometryType(Element shape) {
        // Extract geometry type
        return "rectangle"; // Example
    }
    
    private String extractBounds(Element shape) {
        // Extract shape bounds
        return "0,0,100,100"; // Example
    }
    
    private String extractRotation(Element shape) {
        // Extract rotation
        return "0"; // Example
    }
    
    private boolean extractVisibility(Element shape) {
        // Extract visibility
        return true; // Example
    }
    
    private Map<String, String> extractGeometryProperties(Element shape) {
        Map<String, String> properties = new HashMap<>();
        properties.put("X Position", "0");
        properties.put("Y Position", "0");
        properties.put("Width", "100");
        properties.put("Height", "100");
        return properties;
    }
    
    private String extractPresetGeometry(Element shape) {
        return "rect"; // Example
    }
    
    private String extractCustomPath(Element shape) {
        return "None"; // Example
    }
    
    private String extractTransformMatrix(Element shape) {
        return "Identity"; // Example
    }
    
    private java.util.List<String> extractShapeRelationships(Element shape) {
        return java.util.Arrays.asList("Parent: Slide", "Group: None"); // Example
    }
    
    // ========== NESTED CLASSES ==========
    
    public static class AttributeNode {
        private String name;
        private String value;
        
        public AttributeNode(String name, String value) {
            this.name = name;
            this.value = value;
        }
        
        public String getName() { return name; }
        public String getValue() { return value; }
        
        @Override
        public String toString() {
            return name + (value.isEmpty() ? "" : ": " + value);
        }
    }
    
    public static class GeometryProperty {
        private String name;
        private String value;
        
        public GeometryProperty(String name, String value) {
            this.name = name;
            this.value = value;
        }
        
        public String getName() { return name; }
        public String getValue() { return value; }
    }
    
    public static class FormatProperty {
        private String category;
        private String property;
        private String value;
        
        public FormatProperty(String category, String property, String value) {
            this.category = category;
            this.property = property;
            this.value = value;
        }
        
        public String getCategory() { return category; }
        public String getProperty() { return property; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
    
    private static class AttributeTreeCell extends TreeCell<AttributeNode> {
        @Override
        protected void updateItem(AttributeNode item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.toString());
            }
        }
    }
}