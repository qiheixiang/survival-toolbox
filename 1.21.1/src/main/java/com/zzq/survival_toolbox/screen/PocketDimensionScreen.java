package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.network.PocketDimensionPageActionPacket;
import com.zzq.survival_toolbox.network.PocketDimensionSearchPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 随身次元袋界面（多页版）
 * <p>
 * 复用原版大箱子贴图（54 格），左侧 64px 页列表（滚动）：每行页名 + 空页 ✕，
 * 底部 ➕ 新建页；左键行切换页、右键行重命名；顶部搜索框跨页搜索，◀▶ 翻搜索结果。
 * 每格右下角显示合并后的真实数量（≥1000 缩写，走原版渲染路径）。
 * </p>
 */
public class PocketDimensionScreen extends AbstractContainerScreen<PocketDimensionMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/generic_54.png");

    private static final int LIST_X = 2;
    private static final int LIST_Y = 18;
    private static final int LIST_W = 60;
    private static final int LIST_H = 180;
    private static final int ROW_H = 12;
    private static final int VISIBLE_ROWS = LIST_H / ROW_H;

    private EditBox searchBox;
    private EditBox renameBox;
    private int renamePageIndex = -1;
    private int pageScroll = 0;

    public PocketDimensionScreen(PocketDimensionMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176 + PocketDimensionMenu.PAGE_LIST_WIDTH;
        this.imageHeight = 222;
        // 标题移到屏幕外（顶部空间让给搜索框）
        this.titleLabelY = -20;
        // "物品栏"标签：玩家背包区（140）上方
        this.inventoryLabelX = 8 + PocketDimensionMenu.PAGE_LIST_WIDTH;
        this.inventoryLabelY = 128;
    }

    @Override
    protected void init() {
        super.init();
        // 顶部：搜索框（放在贴图标题区，不遮挡任何槽位）
        this.searchBox = new EditBox(this.font, this.leftPos + PocketDimensionMenu.PAGE_LIST_WIDTH + 8, this.topPos + 4, 112, 14,
                Component.translatable("gui.zzq_survival_toolbox.pocket.search"));
        this.searchBox.setMaxLength(32);
        this.searchBox.setResponder(this::onSearchChanged);
        this.addWidget(this.searchBox);

        // 页重命名输入框（默认隐藏）
        this.renameBox = new EditBox(this.font, this.leftPos + LIST_X + 4, this.topPos + LIST_Y, LIST_W - 20, ROW_H - 2,
                Component.literal("rename"));
        this.renameBox.setMaxLength(16);
        this.renameBox.setResponder(s -> {
        });
        this.renameBox.setVisible(false);
        this.addWidget(this.renameBox);
    }

    /** 搜索输入 → 发送关键词到服务端跨页搜索 */
    private void onSearchChanged(String text) {
        PacketDistributor.sendToServer(new PocketDimensionSearchPacket(text));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.renameBox.isVisible()) {
            if (this.renameBox.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(this.renameBox);
                return true;
            }
            // 点击输入框外：提交当前输入
            submitRename();
            this.setFocused(null);
            this.renameBox.setVisible(false);
            this.renamePageIndex = -1;
        }
        if (this.searchBox.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.searchBox);
            return true;
        }
        if (handlePageListClick(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 页列表点击：行 = 切页/删除/重命名，底部 ➕ = 新建 */
    private boolean handlePageListClick(double mouseX, double mouseY, int button) {
        double lx = mouseX - this.leftPos;
        double ly = mouseY - this.topPos;
        if (lx < LIST_X || lx > LIST_X + LIST_W) return false;
        List<String> names = this.menu.getPocketContainer().getPageNames();
        // ➕ 新建按钮
        if (ly >= LIST_Y + LIST_H + 4 && ly <= LIST_Y + LIST_H + 16) {
            PacketDistributor.sendToServer(new PocketDimensionPageActionPacket(
                    PocketDimensionPageActionPacket.ACTION_ADD, 0, ""));
            return true;
        }
        if (ly < LIST_Y || ly >= LIST_Y + LIST_H) return false;
        int row = (int) ((ly - LIST_Y) / ROW_H);
        int index = pageScroll + row;
        if (index < 0 || index >= names.size()) return false;
        // ✕ 删除（仅空页）
        if (button == 0 && lx >= LIST_X + LIST_W - 10 && isPageEmpty(index)) {
            PacketDistributor.sendToServer(new PocketDimensionPageActionPacket(
                    PocketDimensionPageActionPacket.ACTION_REMOVE, index, ""));
            return true;
        }
        if (button == 0) {
            // 左键：切换页
            PacketDistributor.sendToServer(new PocketDimensionPageActionPacket(
                    PocketDimensionPageActionPacket.ACTION_SET_PAGE, index, ""));
        } else if (button == 1) {
            // 右键：重命名
            startRename(index);
        }
        return true;
    }

    private boolean isPageEmpty(int index) {
        List<Integer> counts = this.menu.getPocketContainer().getPageCounts();
        if (index < 0 || index >= counts.size()) return false;
        return counts.get(index) == 0;
    }

    private void startRename(int index) {
        List<String> names = this.menu.getPocketContainer().getPageNames();
        if (index < 0 || index >= names.size()) return;
        this.renamePageIndex = index;
        this.renameBox.setValue(names.get(index));
        this.renameBox.setVisible(true);
        this.renameBox.setY(this.topPos + LIST_Y + (index - pageScroll) * ROW_H);
        this.renameBox.setX(this.leftPos + LIST_X + 2);
        this.setFocused(this.renameBox);
    }

    private void submitRename() {
        if (this.renamePageIndex >= 0) {
            PacketDistributor.sendToServer(new PocketDimensionPageActionPacket(
                    PocketDimensionPageActionPacket.ACTION_RENAME, this.renamePageIndex, this.renameBox.getValue()));
        }
        this.renameBox.setVisible(false);
        this.renamePageIndex = -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        double lx = mouseX - this.leftPos;
        if (lx >= LIST_X && lx <= LIST_X + LIST_W) {
            List<String> names = this.menu.getPocketContainer().getPageNames();
            int maxScroll = Math.max(0, names.size() - VISIBLE_ROWS);
            this.pageScroll = Math.max(0, Math.min(pageScroll - (int) deltaY, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        gui.blit(TEXTURE, this.leftPos + PocketDimensionMenu.PAGE_LIST_WIDTH, this.topPos, 0, 0,
                176, this.imageHeight, 256, 256);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        // 搜索结果超过一页时提示细化关键词
        if (this.menu.getPocketContainer().isSearching()
                && this.menu.getPocketContainer().isSearchTruncated()) {
            gui.drawString(this.font,
                    Component.translatable("gui.zzq_survival_toolbox.pocket.search_too_many"),
                    this.leftPos + PocketDimensionMenu.PAGE_LIST_WIDTH + 8, this.topPos + 20, 0xFF5555, false);
        }
        this.renderPageList(gui);
        this.renderTooltip(gui, mouseX, mouseY);
        this.searchBox.render(gui, mouseX, mouseY, partialTick);
        this.renameBox.render(gui, mouseX, mouseY, partialTick);
    }

    /**
     * 槽位渲染覆写：≥1000 的条目（getItem 返回真实数量，MC 画长数字）
     * 用缩写替换——遮住原版数字区域后画 k/万/亿。
     */
    @Override
    protected void renderSlot(GuiGraphics gui, net.minecraft.world.inventory.Slot slot) {
        super.renderSlot(gui, slot);
        if (slot instanceof PocketSlot) {
            int count = this.menu.getPocketContainer().getItem(slot.getSlotIndex()).getCount();
            if (count >= 1000) {
                int x = slot.x + this.leftPos;
                int y = slot.y + this.topPos;
                String text = abbreviate(count);
                int width = this.font.width(text);
                // 遮住原版真实数字区域（图标右下角一小块），再画缩写
                gui.fill(x + 17 - width - 2, y + 9, x + 17, y + 18, 0x90000000);
                gui.drawString(this.font, text, x + 17 - width, y + 9, 0xFFFFFF, true);
            }
        }
    }

    /** 数量缩写：≥1k 显示 k，≥1万 显示万，≥1亿 显示亿（最多 1 位小数，去尾零） */
    private static String abbreviate(long count) {
        if (count < 10000L) return trimZero(count / 1000.0) + "k";
        if (count < 100000000L) return trimZero(count / 10000.0) + "万";
        return trimZero(count / 100000000.0) + "亿";
    }

    private static String trimZero(double v) {
        long tenths = Math.round(v * 10);
        if (tenths % 10 == 0) return String.valueOf(tenths / 10);
        return (tenths / 10) + "." + (tenths % 10);
    }

    /** 左侧页列表：半透明背景 + 页行（当前页高亮、空页 ✕）+ 底部 ➕ */
    private void renderPageList(GuiGraphics gui) {
        int x0 = this.leftPos + LIST_X;
        int y0 = this.topPos + LIST_Y;
        // 背景
        gui.fill(x0, y0, x0 + LIST_W, y0 + LIST_H, 0xC0101010);
        List<String> names = this.menu.getPocketContainer().getPageNames();
        int current = this.menu.getPocketContainer().getCurrentPage();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = pageScroll + row;
            if (index >= names.size()) break;
            int y = y0 + row * ROW_H;
            boolean selected = index == current;
            // 选中页：亮背景 + 左侧白色竖条 + 白字，一眼看出当前页
            if (selected) {
                gui.fill(x0, y, x0 + LIST_W, y + ROW_H, 0x90FFFFFF);
                gui.fill(x0, y, x0 + 2, y + ROW_H, 0xFFFFFFFF);
            }
            String name = names.get(index);
            if (this.font.width(name) > LIST_W - 16) {
                name = this.font.plainSubstrByWidth(name, LIST_W - 16);
            }
            int tx = selected ? x0 + 5 : x0 + 3;
            gui.drawString(this.font, name, tx, y + 2, selected ? 0xFFFFFF : 0xB0B0B0, false);
            // 空页显示 ✕（仅真正为空的页）
            if (isPageEmpty(index)) {
                gui.drawString(this.font, "✕", x0 + LIST_W - 9, y + 2, 0xFF5555, false);
            }
        }
        // 底部 ➕ 新建
        int py = y0 + LIST_H + 4;
        gui.fill(x0, py, x0 + LIST_W, py + ROW_H, 0xC0303030);
        gui.drawString(this.font, "➕ 新页", x0 + 3, py + 2, 0xFFFFFF, false);
    }
}
