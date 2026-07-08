package com.cybersammy.citiesarise.client.config;

import com.cybersammy.citiesarise.config.CitiesAriseConfig;
import com.cybersammy.citiesarise.config.CitiesAriseConfigSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CitiesAriseConfigScreen extends Screen {
    private static final Component TITLE = Component.literal("Cities Arise Config");
    private static final Component WARNING = Component.literal("Debug placement permanently changes the world.");
    private static final int FIELD_WIDTH = 86;
    private static final int LABEL_WIDTH = 152;
    private static final int ROW_HEIGHT = 24;
    private static final int COLUMN_WIDTH = 270;

    private final Screen parent;
    private final List<IntField> intFields = new ArrayList<>();
    private final List<DoubleField> doubleFields = new ArrayList<>();
    private final List<ToggleField> toggleFields = new ArrayList<>();
    private Component status = Component.empty();

    private IntField debugSurveyWidth;
    private IntField debugSurveyDepth;
    private IntField debugRoadWidth;
    private DoubleField debugMaxBuildableSlope;
    private IntField debugTargetParcelCount;
    private IntField debugParcelWidth;
    private IntField debugParcelDepth;
    private IntField debugBuildingMargin;
    private ToggleField debugPlacementEnabled;
    private ToggleField debugLoggingEnabled;
    private ToggleField terrainLoggingEnabled;
    private ToggleField planningLoggingEnabled;
    private ToggleField placementLoggingEnabled;
    private ToggleField commandLoggingEnabled;

    public CitiesAriseConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        intFields.clear();
        doubleFields.clear();
        toggleFields.clear();

        CitiesAriseConfigSnapshot snapshot = CitiesAriseConfig.snapshot();
        int leftX = contentLeft();
        int rightX = leftX + COLUMN_WIDTH;
        int topY = 42;

        debugSurveyWidth = addIntField("Survey width", snapshot.debugSurveyWidth(), leftX, topY);
        debugSurveyDepth = addIntField("Survey depth", snapshot.debugSurveyDepth(), leftX, topY + ROW_HEIGHT);
        debugRoadWidth = addIntField("Road width", snapshot.debugRoadWidth(), leftX, topY + ROW_HEIGHT * 2);
        debugMaxBuildableSlope = addDoubleField(
                "Max buildable slope",
                snapshot.debugMaxBuildableSlope(),
                leftX,
                topY + ROW_HEIGHT * 3
        );
        debugTargetParcelCount = addIntField(
                "Target parcels",
                snapshot.debugTargetParcelCount(),
                leftX,
                topY + ROW_HEIGHT * 4
        );
        debugParcelWidth = addIntField("Parcel width", snapshot.debugParcelWidth(), leftX, topY + ROW_HEIGHT * 5);
        debugParcelDepth = addIntField("Parcel depth", snapshot.debugParcelDepth(), leftX, topY + ROW_HEIGHT * 6);
        debugBuildingMargin = addIntField(
                "Building margin",
                snapshot.debugBuildingMargin(),
                leftX,
                topY + ROW_HEIGHT * 7
        );

        debugPlacementEnabled = addToggle(
                "Debug placement",
                snapshot.debugPlacementEnabled(),
                rightX,
                topY
        );
        debugLoggingEnabled = addToggle("Debug logging", snapshot.debugLoggingEnabled(), rightX, topY + ROW_HEIGHT);
        terrainLoggingEnabled = addToggle(
                "Terrain logging",
                snapshot.terrainLoggingEnabled(),
                rightX,
                topY + ROW_HEIGHT * 2
        );
        planningLoggingEnabled = addToggle(
                "Planning logging",
                snapshot.planningLoggingEnabled(),
                rightX,
                topY + ROW_HEIGHT * 3
        );
        placementLoggingEnabled = addToggle(
                "Placement logging",
                snapshot.placementLoggingEnabled(),
                rightX,
                topY + ROW_HEIGHT * 4
        );
        commandLoggingEnabled = addToggle(
                "Command logging",
                snapshot.commandLoggingEnabled(),
                rightX,
                topY + ROW_HEIGHT * 5
        );

        int buttonY = Math.min(height - 32, topY + ROW_HEIGHT * 10);
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
                .bounds(leftX, buttonY, 80, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Defaults"), button -> loadDefaults())
                .bounds(leftX + 88, buttonY, 80, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> closeScreen())
                .bounds(rightX + 88, buttonY, 80, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        renderLabels(guiGraphics);
        guiGraphics.drawString(font, WARNING, contentLeft() + COLUMN_WIDTH, 42 + ROW_HEIGHT * 7, 0xFFAA33);
        guiGraphics.drawCenteredString(font, status, width / 2, Math.min(height - 56, 42 + ROW_HEIGHT * 9), 0xFF5555);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    private IntField addIntField(String label, int value, int x, int y) {
        EditBox editBox = createEditBox(x + LABEL_WIDTH, y, Integer.toString(value));
        addRenderableWidget(editBox);

        IntField field = new IntField(label, editBox);
        intFields.add(field);
        return field;
    }

    private DoubleField addDoubleField(String label, double value, int x, int y) {
        EditBox editBox = createEditBox(x + LABEL_WIDTH, y, Double.toString(value));
        addRenderableWidget(editBox);

        DoubleField field = new DoubleField(label, editBox);
        doubleFields.add(field);
        return field;
    }

    private EditBox createEditBox(int x, int y, String value) {
        EditBox editBox = new EditBox(font, x, y, FIELD_WIDTH, 18, Component.empty());
        editBox.setValue(value);
        return editBox;
    }

    private ToggleField addToggle(String label, boolean value, int x, int y) {
        ToggleField field = new ToggleField(label, value);
        Button button = Button.builder(field.message(), clickedButton -> {
                    field.toggle();
                    clickedButton.setMessage(field.message());
                })
                .bounds(x + LABEL_WIDTH, y, FIELD_WIDTH, 18)
                .build();
        field.setButton(button);
        addRenderableWidget(button);
        toggleFields.add(field);
        return field;
    }

    private void renderLabels(GuiGraphics guiGraphics) {
        for (IntField field : intFields) {
            renderFieldLabel(guiGraphics, field.label(), field.editBox());
        }

        for (DoubleField field : doubleFields) {
            renderFieldLabel(guiGraphics, field.label(), field.editBox());
        }

        for (ToggleField field : toggleFields) {
            renderToggleLabel(guiGraphics, field);
        }
    }

    private void renderFieldLabel(GuiGraphics guiGraphics, String label, EditBox editBox) {
        guiGraphics.drawString(font, label, editBox.getX() - LABEL_WIDTH, editBox.getY() + 5, 0xDDDDDD);
    }

    private void renderToggleLabel(GuiGraphics guiGraphics, ToggleField field) {
        Button button = field.button();
        guiGraphics.drawString(font, field.label(), button.getX() - LABEL_WIDTH, button.getY() + 5, 0xDDDDDD);
    }

    private int contentLeft() {
        int contentWidth = COLUMN_WIDTH * 2;
        return Math.max(12, (width - contentWidth) / 2);
    }

    private void save() {
        try {
            CitiesAriseConfig.applySnapshot(createSnapshot());
            status = Component.literal("Saved.");
        } catch (IllegalArgumentException exception) {
            status = Component.literal(exception.getMessage());
        }
    }

    private CitiesAriseConfigSnapshot createSnapshot() {
        return new CitiesAriseConfigSnapshot(
                debugSurveyWidth.value(),
                debugSurveyDepth.value(),
                debugRoadWidth.value(),
                debugMaxBuildableSlope.value(),
                debugTargetParcelCount.value(),
                debugParcelWidth.value(),
                debugParcelDepth.value(),
                debugBuildingMargin.value(),
                debugPlacementEnabled.value(),
                debugLoggingEnabled.value(),
                terrainLoggingEnabled.value(),
                planningLoggingEnabled.value(),
                placementLoggingEnabled.value(),
                commandLoggingEnabled.value()
        );
    }

    private void loadDefaults() {
        CitiesAriseConfigSnapshot defaults = CitiesAriseConfigSnapshot.defaults();
        debugSurveyWidth.setValue(defaults.debugSurveyWidth());
        debugSurveyDepth.setValue(defaults.debugSurveyDepth());
        debugRoadWidth.setValue(defaults.debugRoadWidth());
        debugMaxBuildableSlope.setValue(defaults.debugMaxBuildableSlope());
        debugTargetParcelCount.setValue(defaults.debugTargetParcelCount());
        debugParcelWidth.setValue(defaults.debugParcelWidth());
        debugParcelDepth.setValue(defaults.debugParcelDepth());
        debugBuildingMargin.setValue(defaults.debugBuildingMargin());
        debugPlacementEnabled.setValue(defaults.debugPlacementEnabled());
        debugLoggingEnabled.setValue(defaults.debugLoggingEnabled());
        terrainLoggingEnabled.setValue(defaults.terrainLoggingEnabled());
        planningLoggingEnabled.setValue(defaults.planningLoggingEnabled());
        placementLoggingEnabled.setValue(defaults.placementLoggingEnabled());
        commandLoggingEnabled.setValue(defaults.commandLoggingEnabled());
        status = Component.literal("Defaults loaded. Press Save to apply.");
    }

    private void closeScreen() {
        Objects.requireNonNull(minecraft, "minecraft").setScreen(parent);
    }

    private record IntField(String label, EditBox editBox) {
        int value() {
            try {
                return Integer.parseInt(editBox.getValue().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(label + " must be an integer", exception);
            }
        }

        void setValue(int value) {
            editBox.setValue(Integer.toString(value));
        }
    }

    private record DoubleField(String label, EditBox editBox) {
        double value() {
            try {
                return Double.parseDouble(editBox.getValue().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(label + " must be a number", exception);
            }
        }

        void setValue(double value) {
            editBox.setValue(Double.toString(value));
        }
    }

    private static final class ToggleField {
        private final String label;
        private boolean value;
        private Button button;

        private ToggleField(String label, boolean value) {
            this.label = label;
            this.value = value;
        }

        private String label() {
            return label;
        }

        private boolean value() {
            return value;
        }

        private void toggle() {
            value = !value;
        }

        private void setValue(boolean value) {
            this.value = value;
            button.setMessage(message());
        }

        private void setButton(Button button) {
            this.button = Objects.requireNonNull(button, "button");
        }

        private Button button() {
            return Objects.requireNonNull(button, "button");
        }

        private Component message() {
            if (value) {
                return Component.literal("On");
            }

            return Component.literal("Off");
        }
    }
}
