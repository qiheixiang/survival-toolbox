package com.zzq.survival_toolbox.client.gui;

import com.zzq.survival_toolbox.util.XrayOreHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 矿透方块选择菜单（纯客户端）
 * <p>
 * 手持透视眼镜 Shift+右键打开。
 * 列出当前注册表全部方块（支持搜索过滤），点击行切换"矿透时是否显示"，
 * 右侧滚动条可点击/拖拽。默认已开启原版矿物与自动识别的模组矿石（注册名以 _ore 结尾）。
 * </p>
 */
public class XraySelectorScreen extends Screen {

    private static final int ROW_HEIGHT = 14;
    private static final int VISIBLE_ROWS = 16;
    private static final int LIST_WIDTH = 280;

    private final List<ResourceLocation> blockIds;
    private List<ResourceLocation> filteredBlocks = new ArrayList<>();
    private final Map<ResourceLocation, String> displayNameCache = new HashMap<>();

    private EditBox searchBox;
    private int scrollOffset = 0;
    private int listX;
    private int listY;
    private boolean draggingScroll = false;
    /** 只看已开启的方块 */
    private boolean showEnabledOnly = false;

    public XraySelectorScreen() {
        super(Component.translatable("gui.zzq_survival_toolbox.xray.title"));
        this.blockIds = XrayOreHelper.getAllBlockIds();
        this.filteredBlocks = blockIds;
    }

    @Override
    protected void init() {
        this.listX = (this.width - LIST_WIDTH) / 2;
        this.listY = 42;

        this.searchBox = new EditBox(this.font, listX, 20, LIST_WIDTH, 16,
                Component.translatable("gui.zzq_survival_toolbox.xray.search"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setResponder(this::onSearchChanged);
        this.addWidget(this.searchBox);

        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.translatable("gui.zzq_survival_toolbox.xray.filter_enabled"),
                        b -> {
                            this.showEnabledOnly = !this.showEnabledOnly;
                            applyFilter();
                            b.setMessage(Component.translatable(this.showEnabledOnly
                                    ? "gui.zzq_survival_toolbox.xray.filter_all"
                                    : "gui.zzq_survival_toolbox.xray.filter_enabled"));
                        })
                .bounds(listX, 4, 90, 12).build());
    }

    /** 搜索过滤：匹配注册名 / 中文名 / 拼音 / 拼音首字母，并可只看已开启 */
    private void onSearchChanged(String text) {
        applyFilter();
    }

    private void applyFilter() {
        String q = this.searchBox.getValue().toLowerCase(Locale.ROOT);
        this.filteredBlocks = blockIds.stream()
                .filter(id -> !showEnabledOnly || XrayOreHelper.isEnabled(id))
                .filter(id -> {
                    if (q.isEmpty()) return true;
                    if (id.toString().contains(q)) return true;
                    String name = getDisplayName(id).toLowerCase(Locale.ROOT);
                    if (name.contains(q)) return true;
                    // 拼音匹配（联动 JEC/PinIn，未安装自动跳过）
                    return com.zzq.survival_toolbox.util.PinyinUtil.matches(getDisplayName(id), q);
                })
                .collect(Collectors.toList());
        this.scrollOffset = 0;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui, mouseX, mouseY, partialTick);
        gui.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);

        this.searchBox.render(gui, mouseX, mouseY, partialTick);

        int maxScroll = Math.max(0, filteredBlocks.size() - VISIBLE_ROWS);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll);

        int visible = Math.min(VISIBLE_ROWS, filteredBlocks.size() - scrollOffset);
        for (int i = 0; i < visible; i++) {
            ResourceLocation id = filteredBlocks.get(scrollOffset + i);
            boolean enabled = XrayOreHelper.isEnabled(id);
            int rowY = listY + i * ROW_HEIGHT;

            if (mouseX >= listX && mouseX <= listX + LIST_WIDTH && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                gui.fill(listX, rowY, listX + LIST_WIDTH, rowY + ROW_HEIGHT, 0x33FFFFFF);
            }

            gui.drawString(this.font, getDisplayName(id), listX + 4, rowY + 3, enabled ? 0xE0E0E0 : 0x808080, false);
            Component state = Component.translatable(
                    enabled ? "gui.zzq_survival_toolbox.xray.enabled" : "gui.zzq_survival_toolbox.xray.disabled");
            gui.drawString(this.font, state, listX + LIST_WIDTH - 24, rowY + 3,
                    enabled ? 0x55FF55 : 0xFF5555, false);
        }

        drawScrollBar(gui, listY, visible, maxScroll);

        gui.fill(listX, listY + visible * ROW_HEIGHT, listX + LIST_WIDTH, listY + visible * ROW_HEIGHT + 1, 0xFF555555);
        gui.drawCenteredString(this.font,
                Component.translatable("gui.zzq_survival_toolbox.xray.summary",
                        filteredBlocks.size(), XrayOreHelper.enabledCount()),
                this.width / 2, listY + visible * ROW_HEIGHT + 8, 0xAAAAAA);
        gui.drawCenteredString(this.font,
                Component.translatable("gui.zzq_survival_toolbox.xray.hint"),
                this.width / 2, listY + visible * ROW_HEIGHT + 20, 0x777777);

        super.render(gui, mouseX, mouseY, partialTick);
    }

    /** 右侧滚动条：轨道 + 滑块（高度按可见比例） */
    private void drawScrollBar(GuiGraphics gui, int listTop, int visible, int maxScroll) {
        int barX = listX + LIST_WIDTH + 3;
        int barHeight = visible * ROW_HEIGHT;
        gui.fill(barX, listTop, barX + 3, listTop + barHeight, 0xFF3A3A3A);
        if (maxScroll > 0 && !filteredBlocks.isEmpty()) {
            int sliderHeight = Math.max(12, barHeight * VISIBLE_ROWS / filteredBlocks.size());
            int sliderY = listTop + (int) ((long) scrollOffset * (barHeight - sliderHeight) / maxScroll);
            gui.fill(barX, sliderY, barX + 3, sliderY + sliderHeight, 0xFFB0B0B0);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.searchBox.mouseClicked(mouseX, mouseY, button)) {
            // 让 Screen 把键盘输入焦点交给搜索框（否则点击后无法输入文字）
            this.setFocused(this.searchBox);
            return true;
        }
        if (button == 0) {
            int barX = listX + LIST_WIDTH + 3;
            int barHeight = Math.min(VISIBLE_ROWS, filteredBlocks.size()) * ROW_HEIGHT;
            int maxScroll = Math.max(0, filteredBlocks.size() - VISIBLE_ROWS);
            // 滚动条点击/开始拖拽
            if (mouseX >= barX && mouseX <= barX + 3 && mouseY >= listY && mouseY < listY + barHeight) {
                if (maxScroll > 0) {
                    this.scrollOffset = (int) ((mouseY - listY) / (double) barHeight * (maxScroll + 1));
                    this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll);
                }
                this.draggingScroll = true;
                return true;
            }
            // 行点击切换
            int row = (int) ((mouseY - listY) / ROW_HEIGHT) + scrollOffset;
            if (row >= 0 && row < filteredBlocks.size()
                    && mouseX >= listX && mouseX <= listX + LIST_WIDTH
                    && mouseY >= listY && mouseY < listY + VISIBLE_ROWS * ROW_HEIGHT) {
                this.setFocused(null);
                XrayOreHelper.toggle(filteredBlocks.get(row));
                // 立即重编译区块，让开关即时生效
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.level != null) {
                    mc.levelRenderer.allChanged();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScroll) {
            int barHeight = Math.min(VISIBLE_ROWS, filteredBlocks.size()) * ROW_HEIGHT;
            int maxScroll = Math.max(0, filteredBlocks.size() - VISIBLE_ROWS);
            if (maxScroll > 0) {
                this.scrollOffset = (int) ((mouseY - listY) / (double) barHeight * (maxScroll + 1));
                this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollOffset -= (int) verticalAmount;
        return true;
    }

    /** 方块显示名（缓存）：优先方块物品名，无物品（技术性方块）则显示注册名 */
    private String getDisplayName(ResourceLocation id) {
        String cached = displayNameCache.get(id);
        if (cached != null) return cached;
        String name = id.toString();
        try {
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block != null && block != Blocks.AIR) {
                ItemStack stack = new ItemStack(block.asItem());
                if (!stack.isEmpty()) {
                    name = stack.getHoverName().getString();
                }
            }
        } catch (Exception ignored) {
        }
        displayNameCache.put(id, name);
        return name;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
