package com.ronm19.enderism.client;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.codex.EnderCodex;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/** A searchable, fully unlocked field guide with independent species and reading navigation. */
public final class EnderCodexScreen extends Screen {
    private static final int INK = 0xFF30263B, MUTED = 0xFF655A70, HEADING = 0xFF67487E, PAPER = 0xFFF0E8D5;
    private static final int ROW_HEIGHT = 21;
    private static final Identifier BOOK = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "textures/item/ender_codex.png");
    private int selected, listOffset, contentScroll, totalTextHeight;
    private boolean survival, indexView, spread, focusSearch;
    private int left, top, bookWidth, bookHeight, listWidth, rows, detailX, detailWidth, contentY, contentHeight;
    private int portraitWidth, portraitHeight, textWidth, dragTarget;
    private double dragOffset;
    private String query = "";
    private List<Integer> filtered = List.of();
    private final List<Button> listButtons = new ArrayList<>();
    private @Nullable EditBox searchBox, resizingSearchBox;
    private @Nullable Button clearSearch, overviewButton, survivalButton, previousSpecies, nextSpecies;
    private @Nullable EndermanRenderState portrait;
    private List<Line> lines = List.of();

    public EnderCodexScreen() { this(1); this.indexView = true; }
    /** Opens a numbered species without requiring discoveries or encounters. */
    public EnderCodexScreen(int speciesNumber) {
        super(Component.translatable("codex.enderism.title"));
        this.selected = Mth.clamp(speciesNumber - 1, 0, EnderCodex.ENTRIES.size() - 1);
    }

    @Override protected void init() {
        this.bookWidth = Math.min(760, Math.max(180, this.width - 20));
        this.bookHeight = Math.min(430, Math.max(190, this.height - 20));
        this.left = (this.width - this.bookWidth) / 2; this.top = (this.height - this.bookHeight) / 2;
        this.spread = this.bookWidth >= 540; this.listWidth = this.spread ? 184 : this.bookWidth;
        if (!this.spread && this.focusSearch) this.indexView = true;
        this.rows = Math.max(1, (this.bookHeight - 136) / ROW_HEIGHT);
        this.detailX = this.spread ? this.left + this.listWidth + 16 : this.left + 12;
        this.detailWidth = this.spread ? this.bookWidth - this.listWidth - 30 : this.bookWidth - 24;
        this.contentY = this.top + 92; this.contentHeight = Math.max(24, this.bookHeight - 160);
        this.portraitHeight = Math.min(208, this.contentHeight - 12);
        this.portraitWidth = Math.min(this.spread ? 96 : 88, Math.max(32, (int)(this.portraitHeight * .56)));
        this.textWidth = this.detailWidth - this.portraitWidth - 14;
        this.filtered = this.matchingEntries(this.query);
        if (!this.filtered.isEmpty() && !this.filtered.contains(this.selected)) this.selected = this.filtered.getFirst();
        this.keepSelectionVisible(); this.listButtons.clear();
        this.searchBox = null; this.clearSearch = null; this.overviewButton = null; this.survivalButton = null;
        this.previousSpecies = null; this.nextSpecies = null; this.dragTarget = 0;
        if (this.spread || this.indexView) this.buildIndex();
        if (this.spread || !this.indexView) this.buildEntry();
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.left + this.bookWidth / 2 - 40, this.top + this.bookHeight - 25, 80, 20).build());
    }
    @Override protected void repositionElements() {
        this.focusSearch = this.searchBox != null && this.searchBox.isFocused();
        this.resizingSearchBox = this.searchBox;
        this.rebuildWidgets();
        this.resizingSearchBox = null;
    }
    @Override protected void setInitialFocus() {
        if (this.focusSearch && this.searchBox != null) super.setInitialFocus(this.searchBox);
        else if (this.overviewButton != null && this.hasSelection()) super.setInitialFocus(this.survival ? this.survivalButton : this.overviewButton);
        else super.setInitialFocus();
    }
    private boolean hasSelection() { return this.filtered.contains(this.selected); }
    private int maxListOffset() { return Math.max(0, this.filtered.size() - this.rows); }
    private int maxContentScroll() { return Math.max(0, this.totalTextHeight - this.contentHeight); }
    private void keepSelectionVisible() {
        int index = this.filtered.indexOf(this.selected);
        if (index >= 0 && index < this.listOffset) this.listOffset = index;
        if (index >= this.listOffset + this.rows) this.listOffset = index - this.rows + 1;
        this.listOffset = Mth.clamp(this.listOffset, 0, this.maxListOffset());
    }

    private void buildIndex() {
        EditBox box = this.resizingSearchBox != null ? this.resizingSearchBox : new EditBox(this.font,
                this.left + 12, this.top + 60, this.listWidth - 52, 18, Component.translatable("codex.enderism.search_help"));
        box.setRectangle(this.listWidth - 52, 18, this.left + 12, this.top + 60);
        box.setBordered(true);
        this.searchBox = this.addRenderableWidget(box);
        this.searchBox.setMaxLength(64); this.searchBox.setHint(Component.translatable("codex.enderism.search"));
        this.searchBox.setTooltip(Tooltip.create(Component.translatable("codex.enderism.search_help")));
        if (!this.searchBox.getValue().equals(this.query)) this.searchBox.setValue(this.query);
        this.searchBox.moveCursorTo(this.searchBox.getCursorPosition(), true);
        this.searchBox.setResponder(this::filterEntries);
        this.clearSearch = this.addRenderableWidget(Button.builder(Component.literal("x"), b -> {
            this.searchBox.setValue(""); this.setInitialFocus(this.searchBox);
        }).bounds(this.left + this.listWidth - 34, this.top + 59, 22, 20)
                .tooltip(Tooltip.create(Component.translatable("codex.enderism.clear_search"))).build());
        this.refreshIndexRows();
    }
    private List<Integer> matchingEntries(String value) {
        String needle = value.strip().toLowerCase(Locale.ROOT); ArrayList<Integer> result = new ArrayList<>();
        boolean numberQuery = needle.matches("#?\\d+");
        for (int i = 0; i < EnderCodex.ENTRIES.size(); i++) {
            EnderCodex.Entry entry = EnderCodex.ENTRIES.get(i); boolean matches;
            if (numberQuery) {
                String digits = needle.replace("#", "").replaceFirst("^0+(?!$)", "");
                matches = Integer.toString(entry.number()).equals(digits);
            } else {
                String searchable = entry.numberLabel() + " " + Component.translatable(entry.key("name")).getString()
                        + " " + entry.id().replace('_', ' ') + " " + Component.translatable(entry.key("habitat")).getString();
                matches = searchable.toLowerCase(Locale.ROOT).contains(needle);
            }
            if (matches) result.add(i);
        }
        return List.copyOf(result);
    }
    private void filterEntries(String value) {
        this.query = value; this.filtered = this.matchingEntries(value);
        if (!this.filtered.isEmpty() && !this.filtered.contains(this.selected)) this.selected = this.filtered.getFirst();
        this.listOffset = 0; this.contentScroll = 0; this.keepSelectionVisible(); this.refreshIndexRows();
        if (this.spread) this.updateEntry();
    }
    /** Refresh only result rows so native typing retains its EditBox, caret and focus. */
    private void refreshIndexRows() {
        for (Button button : this.listButtons) this.removeWidget(button);
        this.listButtons.clear(); this.listOffset = Mth.clamp(this.listOffset, 0, this.maxListOffset());
        for (int row = 0; row < this.rows && this.listOffset + row < this.filtered.size(); row++) {
            int number = this.filtered.get(this.listOffset + row); EnderCodex.Entry entry = EnderCodex.ENTRIES.get(number);
            String name = Component.translatable(entry.key("name")).getString().replace(" Enderman", "");
            Component label = Component.literal((number == this.selected ? "> " : "") + entry.numberLabel() + " " + name);
            if (number == this.selected) label = label.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
            this.listButtons.add(this.addRenderableWidget(Button.builder(label, b -> this.select(number))
                    .bounds(this.left + 12, this.top + 86 + row * ROW_HEIGHT, this.listWidth - 36, 19).build()));
        }
        if (this.clearSearch != null) this.clearSearch.active = !this.query.isEmpty();
    }
    private void select(int number) {
        this.selected = Mth.clamp(number, 0, EnderCodex.ENTRIES.size() - 1);
        if (!this.filtered.contains(this.selected)) this.query = "";
        this.contentScroll = 0; this.indexView = false; this.focusSearch = false; this.rebuildWidgets();
    }
    private void selectRelative(int amount) {
        int position = this.filtered.indexOf(this.selected), next = position + amount;
        if (position >= 0 && next >= 0 && next < this.filtered.size()) this.select(this.filtered.get(next));
    }
    private void buildEntry() {
        int tabWidth = Math.min(104, (this.detailWidth - (this.spread ? 0 : 80) - 4) / 2);
        this.overviewButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> this.setSection(false))
                .bounds(this.detailX, this.top + 64, tabWidth, 20).build());
        this.survivalButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> this.setSection(true))
                .bounds(this.detailX + tabWidth + 4, this.top + 64, tabWidth, 20).build());
        if (!this.spread) this.addRenderableWidget(Button.builder(Component.translatable("codex.enderism.all_entries"), b -> {
            this.indexView = true; this.focusSearch = false; this.rebuildWidgets();
        }).bounds(this.detailX + this.detailWidth - 72, this.top + 64, 72, 20).build());
        int width = Math.min(112, (this.detailWidth - 44) / 2), y = this.top + this.bookHeight - 54;
        this.previousSpecies = this.addRenderableWidget(Button.builder(Component.translatable("codex.enderism.previous_species"), b -> this.selectRelative(-1))
                .bounds(this.detailX, y, width, 20).build());
        this.nextSpecies = this.addRenderableWidget(Button.builder(Component.translatable("codex.enderism.next_species"), b -> this.selectRelative(1))
                .bounds(this.detailX + this.detailWidth - width, y, width, 20).build());
        this.updateEntry();
    }
    private void updateEntry() {
        ArrayList<Line> text = new ArrayList<>(); this.portrait = null;
        if (this.hasSelection()) {
            EnderCodex.Entry entry = EnderCodex.ENTRIES.get(this.selected); this.portrait = this.createPortrait(entry);
            if (this.survival) {
                this.section(text, "avoidance", Component.translatable(entry.key("avoidance")));
                this.section(text, "escape", Component.translatable(entry.key("escape")));
                this.section(text, "combat", Component.translatable(entry.key("combat")));
            } else {
                this.section(text, "habitat", Component.translatable(entry.key("habitat")));
                this.section(text, "activity", Component.translatable(entry.key("activity")));
                this.section(text, "description", Component.translatable(entry.key("description")));
            }
        }
        this.lines = List.copyOf(text); this.totalTextHeight = text.stream().mapToInt(Line::height).sum();
        this.contentScroll = Mth.clamp(this.contentScroll, 0, this.maxContentScroll());
        if (this.overviewButton != null) {
            this.overviewButton.active = this.hasSelection(); this.survivalButton.active = this.hasSelection();
            this.overviewButton.setMessage(tabLabel("overview", !this.survival)); this.survivalButton.setMessage(tabLabel("survival", this.survival));
            int position = this.filtered.indexOf(this.selected);
            this.previousSpecies.active = position > 0; this.nextSpecies.active = position >= 0 && position + 1 < this.filtered.size();
        }
    }
    private static Component tabLabel(String key, boolean selected) {
        var label = Component.translatable("codex.enderism." + key);
        return selected ? label.withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD) : label;
    }
    private void setSection(boolean survival) {
        if (this.survival != survival) { this.survival = survival; this.contentScroll = 0; this.updateEntry(); }
    }
    private void section(List<Line> output, String title, Component value) {
        for (FormattedCharSequence line : this.font.split(Component.translatable("codex.enderism." + title).withStyle(ChatFormatting.BOLD), this.textWidth - 12))
            output.add(new Line(line, HEADING, 16));
        for (FormattedCharSequence line : this.font.split(value, this.textWidth - 12)) output.add(new Line(line, INK, 13));
        output.add(new Line(FormattedCharSequence.EMPTY, INK, 8));
    }
    private void scrollIndex(int amount) {
        int next = Mth.clamp(this.listOffset + amount, 0, this.maxListOffset());
        if (next != this.listOffset) { this.listOffset = next; this.refreshIndexRows(); }
    }
    private void scrollText(int amount) { this.contentScroll = Mth.clamp(this.contentScroll + amount, 0, this.maxContentScroll()); }

    private @Nullable EndermanRenderState createPortrait(EnderCodex.Entry entry) {
        String path = entry.id().equals("enderman_golem") ? entry.id() : entry.id() + "_enderman";
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.fromNamespaceAndPath(Enderism.MOD_ID, path)).orElse(null);
        if (type == null) return null;
        var seed = new EndermanRenderState(); seed.entityType = type;
        var state = this.minecraft.getEntityRenderDispatcher().getRenderer(seed).createRenderState();
        if (!(state instanceof EndermanRenderState enderman)) return null;
        enderman.entityType = type; enderman.boundingBoxWidth = type.getWidth(); enderman.boundingBoxHeight = type.getHeight();
        enderman.eyeHeight = type.getHeight() * .88F; enderman.bodyRot = 162; enderman.yRot = 12; return enderman;
    }
    private void extractPortrait(GuiGraphicsExtractor graphics) {
        int x = this.detailX + this.detailWidth - this.portraitWidth, y = this.contentY + 12;
        Component label = Component.translatable("codex.enderism.illustration");
        if (this.font.width(label) <= this.portraitWidth) graphics.text(this.font, label, x, this.contentY, MUTED, false);
        graphics.fill(x - 2, y - 2, x + this.portraitWidth + 2, y + this.portraitHeight + 2, 0xFF31263D);
        graphics.fill(x, y, x + this.portraitWidth, y + this.portraitHeight, 0xFF514A57);
        if (this.portrait != null) {
            float height = Math.max(3.4F, this.portrait.boundingBoxHeight + .2F);
            float width = Math.max(1.4F, this.portrait.boundingBoxWidth > 1.4F ? this.portrait.boundingBoxWidth + .6F : 1.4F);
            float scale = Math.min((this.portraitHeight - 12) / height, (this.portraitWidth - 12) / width);
            graphics.entity(this.portrait, Math.max(1, scale), new Vector3f(0, height / 2, 0),
                    new Quaternionf().rotateZ((float)Math.PI), new Quaternionf(), x, y, x + this.portraitWidth, y + this.portraitHeight);
        } else {
            int size = Math.max(8, Math.min(this.portraitWidth, this.portraitHeight) - 16);
            graphics.blit(RenderPipelines.GUI_TEXTURED, BOOK, x + (this.portraitWidth - size) / 2, y + (this.portraitHeight - size) / 2,
                    0, 0, size, size, 16, 16, 16, 16);
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.fill(this.left - 3, this.top - 3, this.left + this.bookWidth + 3, this.top + this.bookHeight + 3, 0xFF34223F);
        graphics.fill(this.left, this.top, this.left + this.bookWidth, this.top + this.bookHeight, PAPER);
        graphics.fill(this.left, this.top, this.left + this.bookWidth, this.top + 31, 0xFF21162C);
        graphics.text(this.font, this.title, this.left + 12, this.top + 10, 0xFFF2E6CD, false);
        Component unlocked = Component.translatable("codex.enderism.all_species");
        if (this.font.width(this.title) + this.font.width(unlocked) + 32 < this.bookWidth)
            graphics.text(this.font, unlocked, this.left + this.bookWidth - this.font.width(unlocked) - 12, this.top + 11, 0xFFC4AED8, false);
        if (this.spread) graphics.fill(this.left + this.listWidth, this.top + 32, this.left + this.listWidth + 1, this.top + this.bookHeight - 32, 0xFFB4A0BB);
        if (this.spread || this.indexView) this.extractIndex(graphics);
        if (this.spread || !this.indexView) this.extractEntry(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }
    private void extractIndex(GuiGraphicsExtractor graphics) {
        graphics.text(this.font, Component.translatable("codex.enderism.species").withStyle(ChatFormatting.BOLD), this.left + 12, this.top + 43, HEADING, false);
        if (this.filtered.isEmpty()) {
            graphics.text(this.font, Component.translatable("codex.enderism.no_results"), this.left + 12, this.top + 94, INK, false);
            int y = this.top + 113;
            for (var line : this.font.split(Component.translatable("codex.enderism.no_results_hint"), this.listWidth - 28)) {
                graphics.text(this.font, line, this.left + 12, y, MUTED, false); y += 13;
            }
        } else {
            Component results = Component.translatable("codex.enderism.results", this.listOffset + 1,
                    Math.min(this.filtered.size(), this.listOffset + this.rows), this.filtered.size());
            graphics.text(this.font, results, this.left + 12, this.top + this.bookHeight - 42, MUTED, false);
        }
        this.extractBar(graphics, this.listBar());
    }
    private void extractEntry(GuiGraphicsExtractor graphics) {
        if (!this.hasSelection()) {
            int y = this.contentY;
            for (var line : this.font.split(Component.translatable("codex.enderism.empty_detail"), this.detailWidth)) {
                graphics.text(this.font, line, this.detailX, y, MUTED, false); y += 13;
            }
            return;
        }
        EnderCodex.Entry entry = EnderCodex.ENTRIES.get(this.selected);
        Component entryTitle = Component.literal(entry.numberLabel() + " ").append(Component.translatable(entry.key("name"))).withStyle(ChatFormatting.BOLD);
        graphics.text(this.font, entryTitle, this.detailX, this.top + 40, INK, false);
        Component gender = Component.translatable("codex.enderism.gender").append(": ").append(Component.translatable(entry.key("gender")));
        graphics.text(this.font, gender, this.detailX, this.top + 53, MUTED, false);
        Button activeTab = this.survival ? this.survivalButton : this.overviewButton;
        graphics.fill(activeTab.getX(), activeTab.getY() + 21, activeTab.getX() + activeTab.getWidth(), activeTab.getY() + 23, HEADING);
        this.extractPortrait(graphics);
        graphics.enableScissor(this.detailX, this.contentY, this.detailX + this.textWidth - 9, this.contentY + this.contentHeight);
        int y = this.contentY - this.contentScroll;
        for (Line line : this.lines) {
            if (y + 9 >= this.contentY && y < this.contentY + this.contentHeight) graphics.text(this.font, line.text(), this.detailX, y, line.color(), false);
            y += line.height();
        }
        graphics.disableScissor(); this.extractBar(graphics, this.textBar());
        if (this.maxContentScroll() > 0) graphics.text(this.font, Component.translatable("codex.enderism.read_hint"), this.detailX,
                this.contentY + this.contentHeight + 2, MUTED, false);
        String count = (this.filtered.indexOf(this.selected) + 1) + " / " + this.filtered.size();
        graphics.text(this.font, count, this.detailX + (this.detailWidth - this.font.width(count)) / 2,
                this.top + this.bookHeight - 48, MUTED, false);
    }

    private @Nullable Bar listBar() {
        return this.maxListOffset() == 0 || !(this.spread || this.indexView) ? null
                : bar(this.left + this.listWidth - 18, this.top + 86, this.rows * ROW_HEIGHT - 2, this.filtered.size(), this.rows, this.listOffset);
    }
    private @Nullable Bar textBar() {
        return this.maxContentScroll() == 0 || !this.hasSelection() || !(this.spread || !this.indexView) ? null
                : bar(this.detailX + this.textWidth - 5, this.contentY, this.contentHeight, this.totalTextHeight, this.contentHeight, this.contentScroll);
    }
    private static Bar bar(int x, int y, int height, int total, int visible, int offset) {
        int thumbHeight = Math.max(12, height * visible / total), max = total - visible;
        int thumbY = y + (int)((long)offset * (height - thumbHeight) / max);
        return new Bar(x, y, height, thumbY, thumbHeight, max);
    }
    private void extractBar(GuiGraphicsExtractor graphics, @Nullable Bar bar) {
        if (bar == null) return;
        graphics.fill(bar.x(), bar.y(), bar.x() + 4, bar.y() + bar.height(), 0xFFD5C7D8);
        graphics.fill(bar.x(), bar.thumbY(), bar.x() + 4, bar.thumbY() + bar.thumbHeight(), HEADING);
    }
    private void dragBar(@Nullable Bar bar, double mouseY) {
        if (bar == null || bar.height() <= bar.thumbHeight()) return;
        int offset = (int)Math.round((mouseY - this.dragOffset - bar.y()) * bar.max() / (bar.height() - bar.thumbHeight()));
        if (this.dragTarget == 1) this.scrollIndex(Mth.clamp(offset, 0, bar.max()) - this.listOffset);
        else this.contentScroll = Mth.clamp(offset, 0, bar.max());
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) for (int target = 1; target <= 2; target++) {
            Bar bar = target == 1 ? this.listBar() : this.textBar();
            if (bar != null && event.x() >= bar.x() - 3 && event.x() < bar.x() + 7 && event.y() >= bar.y() && event.y() < bar.y() + bar.height()) {
                this.dragTarget = target;
                this.dragOffset = event.y() >= bar.thumbY() && event.y() < bar.thumbY() + bar.thumbHeight() ? event.y() - bar.thumbY() : bar.thumbHeight() / 2.0;
                this.dragBar(bar, event.y()); return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragTarget != 0) { this.dragBar(this.dragTarget == 1 ? this.listBar() : this.textBar(), event.y()); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragTarget != 0) { this.dragTarget = 0; return true; }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int direction = scrollY > 0 ? -1 : 1;
            if ((this.spread || this.indexView) && x >= this.left && x < this.left + this.listWidth && y >= this.top + 84 && y < this.top + this.bookHeight - 46) {
                this.scrollIndex(direction * Math.max(1, (int)Math.round(Math.abs(scrollY) * 3))); return true;
            }
            if ((this.spread || !this.indexView) && x >= this.detailX && x < this.detailX + this.detailWidth && y >= this.contentY && y < this.contentY + this.contentHeight) {
                this.scrollText(direction * Math.max(1, (int)Math.round(Math.abs(scrollY) * 26))); return true;
            }
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == 70 && event.hasControlDownWithQuirk()) {
            this.focusSearch = true;
            if (!this.spread && !this.indexView) { this.indexView = true; this.rebuildWidgets(); }
            if (this.searchBox != null) this.setInitialFocus(this.searchBox);
            return true;
        }
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (event.isConfirmation() && this.hasSelection()) { this.select(this.selected); return true; }
            return super.keyPressed(event);
        }
        if (!this.spread && this.indexView) {
            if (event.key() == 266 || event.key() == 267) { this.scrollIndex(event.key() == 266 ? -this.rows : this.rows); return true; }
        } else {
            if (event.key() == 266 || event.key() == 267) { this.scrollText((event.key() == 266 ? -1 : 1) * Math.max(13, this.contentHeight - 13)); return true; }
            if (event.key() == 268 || event.key() == 269) { this.contentScroll = event.key() == 268 ? 0 : this.maxContentScroll(); return true; }
            if (event.isLeft() || event.isRight()) { this.selectRelative(event.isLeft() ? -1 : 1); return true; }
        }
        return super.keyPressed(event);
    }
    private record Line(FormattedCharSequence text, int color, int height) {}
    private record Bar(int x, int y, int height, int thumbY, int thumbHeight, int max) {}
}
