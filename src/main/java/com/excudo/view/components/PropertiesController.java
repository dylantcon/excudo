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
     * Display properties for a slide
     */
    public void displaySlideProperties(SlideMetadata slide) {
        currentSelection = slide;
        properties.clear();
        
        if (slide != null) {
            updateTitle("Slide " + slide.getSlideNumber() + " Properties");
            
            // Add slide properties
            properties.add(new PropertyItem("Slide Number", String.valueOf(slide.getSlideNumber()), false));
            properties.add(new PropertyItem("Title", slide.getTitle() != null ? slide.getTitle() : "Untitled", true));
            properties.add(new PropertyItem("Type", slide.getType().toString(), false));
            properties.add(new PropertyItem("Layout", slide.getLayoutName() != null ? slide.getLayoutName() : "Custom", false));
            properties.add(new PropertyItem("Shape Count", String.valueOf(slide.getShapeCount()), false));
            properties.add(new PropertyItem("Animation Count", String.valueOf(slide.getAnimationCount()), false));
            
            // Add SPID information
            if (slide.getSpids() != null && !slide.getSpids().isEmpty()) {
                properties.add(new PropertyItem("SPIDs", slide.getSpids().toString(), false));
            }
        } else {
            updateTitle("Properties");
        }
    }
    
    /**
     * Display properties for a shape
     * TODO: Implement when Shape class is available
     */
    public void displayShapeProperties(Object shape) {
        currentSelection = shape;
        properties.clear();
        
        if (shape != null) {
            updateTitle("Shape Properties");
            
            // Placeholder for shape properties - will implement when Shape class is ready
            properties.add(new PropertyItem("Shape ID", "Unknown", false));
            properties.add(new PropertyItem("Name", "Unknown Shape", true));
            properties.add(new PropertyItem("Type", "Unknown", false));
            
            // TODO: Implement actual shape property extraction
            
        } else {
            updateTitle("Properties");
        }
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
        if (currentSelection instanceof SlideMetadata) {
            displaySlideProperties((SlideMetadata) currentSelection);
        } else if (currentSelection != null) {
            // TODO: Check for Shape class when implemented
            displayShapeProperties(currentSelection);
        }
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