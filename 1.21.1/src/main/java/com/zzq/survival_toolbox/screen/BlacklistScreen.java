package com.zzq.survival_toolbox.screen;

import com.mojang.blaze3d.vertex.Tesselator;
import com.zzq.survival_toolbox.data.BlacklistEntry;
import com.zzq.survival_toolbox.item.BlacklistItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.gui.widget.ScrollPanel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 黑白名单 GUI 界面
 * <p>
 * 显示已记录的实体列表，支持：
 * <ul>
 *   <li>切换个体/类别匹配模式</li>
 *   <li>切换白名单/黑名单/无差别动作</li>
 *   <li>删除条目</li>
 * </ul>
 * 所有修改通过客户端网络包同步到服务端。
 * </p>
 */
public class BlacklistScreen extends AbstractContainerScreen<BlacklistMenu> {

    private List<BlacklistEntry> entries;
    private EntryScrollPanel scrollPanel;

    public BlacklistScreen(BlacklistMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 256;
        this.imageHeight = 222;
        this.entries = new ArrayList<>();
        refreshEntries();
    }

    /**
     * 获取玩家手持或副手的黑白名单物品
     *
     * @return 黑白名单物品栈，若不存在则返回空栈
     */
    private ItemStack getBlacklistItem() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return ItemStack.EMPTY;
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof BlacklistItem) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof BlacklistItem) return off;
        return ItemStack.EMPTY;
    }

    /**
     * 刷新条目列表
     */
    private void refreshEntries() {
        ItemStack stack = getBlacklistItem();
        if (stack.getItem() instanceof BlacklistItem) {
            this.entries = BlacklistItem.getEntries(stack);
        } else {
            this.entries = new ArrayList<>();
        }
        if (scrollPanel != null) {
            scrollPanel.setEntries(this.entries);
        }
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        // ---- 刷新按钮 ----
        Button btnRefresh = Button.builder(
                Component.translatable("gui.zzq_survival_toolbox.blacklist.refresh"),
                b -> refreshEntries()
        ).bounds(x + imageWidth - 22, y + 6, 16, 16).build();
        addRenderableWidget(btnRefresh);

        // ---- 滚动面板 ----
        scrollPanel = new EntryScrollPanel(
                Minecraft.getInstance(),
                240,
                120,
                y + 30,
                x + 8,
                this.entries
        );
        addRenderableWidget(scrollPanel);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        guiGraphics.fill(x + 4, y + 4, x + imageWidth - 4, y + imageHeight - 4, 0xFF8B8B8B);

        guiGraphics.drawString(font, this.title, x + 8, y + 6, 0x404040, false);

        int count = entries.size();
        guiGraphics.drawString(font,
                Component.translatable("gui.zzq_survival_toolbox.blacklist.count", count),
                x + 100, y + 10, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 标签在 renderBg 中绘制
    }

    // ============================================================
    // 滚动面板
    // ============================================================

    private class EntryScrollPanel extends ScrollPanel {

        private List<BlacklistEntry> entries;
        private final int entryHeight = 24;
        private final java.util.Map<Rect, Runnable> buttonRects = new java.util.HashMap<>();

        public EntryScrollPanel(Minecraft client, int width, int height,
                                int top, int left, List<BlacklistEntry> entries) {
            super(client, width, height, top, left);
            this.entries = entries != null ? entries : new ArrayList<>();
        }

        public void setEntries(List<BlacklistEntry> entries) {
            this.entries = entries != null ? entries : new ArrayList<>();
            this.buttonRects.clear();
        }

        @Override
        protected int getContentHeight() {
            return Math.max(20, entries.size() * entryHeight + 4);
        }

        @Override
        protected void drawPanel(GuiGraphics guiGraphics, int entryRight,
                                 int relativeY, Tesselator tess, int mouseX, int mouseY) {
            int y = relativeY + 2;
            buttonRects.clear();
            Player player = Minecraft.getInstance().player;

            int panelLeft = this.left;
            int panelRight = entryRight;
            int panelWidth = panelRight - panelLeft;

            if (entries.isEmpty()) {
                guiGraphics.drawString(font,
                        Component.translatable("gui.zzq_survival_toolbox.blacklist.empty"),
                        panelLeft + 4, y + 4, 0x888888, false);
                return;
            }

            for (int i = 0; i < entries.size(); i++) {
                BlacklistEntry entry = entries.get(i);
                int rowY = y + i * entryHeight;

                if (rowY > this.bottom - entryHeight) break;
                if (rowY + entryHeight < this.top) continue;

                // ---- 行背景 ----
                guiGraphics.fill(panelLeft, rowY, panelRight, rowY + entryHeight, 0xCC444444);
                guiGraphics.fill(panelLeft, rowY + entryHeight - 1, panelRight, rowY + entryHeight, 0xCC666666);

                // ---- 名称 + 类别 ----
                String displayText = entry.name + " §7(" + entry.category + ")";
                guiGraphics.drawString(font, Component.literal(displayText), panelLeft + 4, rowY + 5, 0xFFFFFF, false);

                // ---- 模式按钮：个体/类别 ----
                int bx1 = panelRight - 140;
                drawButton(guiGraphics, bx1, rowY,
                        entry.mode == BlacklistEntry.Mode.INDIVIDUAL ?
                                Component.translatable("gui.zzq_survival_toolbox.blacklist.mode.individual").getString() :
                                Component.translatable("gui.zzq_survival_toolbox.blacklist.mode.type").getString(),
                        0x335577, () -> {
                            ItemStack stack = getBlacklistItem();
                            if (stack.getItem() instanceof BlacklistItem) {
                                entry.mode = entry.mode == BlacklistEntry.Mode.INDIVIDUAL ?
                                        BlacklistEntry.Mode.TYPE : BlacklistEntry.Mode.INDIVIDUAL;
                                BlacklistItem.updateEntryClient(stack, entry);
                                refreshEntries();
                            }
                        });

                // ---- 动作按钮：白名单/黑名单/无差别 ----
                int bx2 = panelRight - 95;
                String actionText = switch (entry.action) {
                    case WHITELIST -> Component.translatable("gui.zzq_survival_toolbox.blacklist.action.whitelist").getString();
                    case BLACKLIST -> Component.translatable("gui.zzq_survival_toolbox.blacklist.action.blacklist").getString();
                    default -> Component.translatable("gui.zzq_survival_toolbox.blacklist.action.none").getString();
                };
                drawButton(guiGraphics, bx2, rowY, actionText, 0x775533, () -> {
                    ItemStack stack = getBlacklistItem();
                    if (stack.getItem() instanceof BlacklistItem) {
                        entry.action = switch (entry.action) {
                            case NONE -> BlacklistEntry.Action.WHITELIST;
                            case WHITELIST -> BlacklistEntry.Action.BLACKLIST;
                            case BLACKLIST -> BlacklistEntry.Action.NONE;
                        };
                        BlacklistItem.updateEntryClient(stack, entry);
                        refreshEntries();
                    }
                });

                // ---- 删除按钮 ----
                int bx3 = panelRight - 40;
                drawButton(guiGraphics, bx3, rowY,
                        Component.translatable("gui.zzq_survival_toolbox.blacklist.delete").getString(),
                        0x883333, () -> {
                            ItemStack stack = getBlacklistItem();
                            if (stack.getItem() instanceof BlacklistItem) {
                                BlacklistItem.removeEntryClient(stack, entry.uuid);
                                refreshEntries();
                            }
                        });
            }
        }

        private void drawButton(GuiGraphics guiGraphics, int x, int y,
                                String text, int color, Runnable action) {
            int width = 40;
            int height = 18;
            guiGraphics.fill(x, y + 2, x + width, y + 2 + height, 0xFF222222);
            guiGraphics.fill(x + 1, y + 3, x + width - 1, y + 1 + height, color);
            guiGraphics.drawString(font, Component.literal(text), x + 3, y + 4, 0xFFFFFF, false);
            buttonRects.put(new Rect(x, y + 2, width, height), action);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

            for (var entry : buttonRects.entrySet()) {
                Rect rect = entry.getKey();
                if (mouseX >= rect.x && mouseX <= rect.x + rect.w &&
                        mouseY >= rect.y && mouseY <= rect.y + rect.h) {
                    entry.getValue().run();
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        protected int getScrollAmount() {
            return 30;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
            this.scrollDistance += -vertical * getScrollAmount();
            int max = getContentHeight() - (this.height - this.border);
            if (max < 0) max = 0;
            if (this.scrollDistance < 0) this.scrollDistance = 0;
            if (this.scrollDistance > max) this.scrollDistance = max;
            return true;
        }

        @Override
        public NarrationPriority narrationPriority() {
            return NarrationPriority.NONE;
        }

        @Override
        public void updateNarration(NarrationElementOutput narrationElementOutput) {
        }

        private static class Rect {
            int x, y, w, h;

            Rect(int x, int y, int w, int h) {
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
            }
        }
    }
}