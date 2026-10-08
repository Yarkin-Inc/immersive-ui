package it.hurts.shatterbyte.immersiveui;

import it.hurts.shatterbyte.immersiveui.compat.ExtraScreenData;
import it.hurts.shatterbyte.immersiveui.fabric.ImmersiveUIModMenu;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.concurrent.atomic.AtomicBoolean;

/** Exercises the actual client rendering path, including mixins missed by Java compilation. */
public class ImmersiveUIGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> client.gui.setScreen(
                new ImmersiveUIModMenu().getModConfigScreenFactory().create(client.gui.screen())));
        context.waitTicks(5);
        context.takeScreenshot("immersiveui-config");
        context.setScreen(() -> null);

        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("advancement grant @a only minecraft:story/root");
            context.runOnClient(client -> {
                client.player.getInventory().setSelectedSlot(8);
                var curse = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.BINDING_CURSE);
                check(!Enchantment.getFullname(curse, 1).getString().isEmpty(), "Curse formatting");
            });
            context.waitTicks(10);

            context.runOnClient(client -> {
                var inventory = client.player.getInventory();
                var menu = new FurnaceMenu(1, inventory);
                menu.getSlot(0).set(new ItemStack(Items.RAW_IRON, 16));
                menu.getSlot(1).set(new ItemStack(Items.COAL, 16));
                menu.setData(0, 200);
                menu.setData(1, 200);
                client.player.containerMenu = menu;
                client.gui.setScreen(new FurnaceScreen(menu, inventory, Component.literal("Furnace")));
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                var screen = (AbstractContainerScreen<?>) client.gui.screen();
                var data = (ExtraScreenData) screen;
                check(!data.getExpandingProgress().isEmpty(), "Slot rendering hook ran");
                check(data.getMouseInfo().oX != Integer.MIN_VALUE, "Screen mouse tracking ran");
                try {
                    var field = AbstractFurnaceScreen.class.getDeclaredField("shouldBurst");
                    field.setAccessible(true);
                    check(((AtomicBoolean) field.get(screen)).get(), "Lit furnace particle hook ran");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Furnace particle state unavailable", e);
                }
                // Keep animations alive long enough to observe them during client frames.
                ImmersiveUI.CONFIG.setItemTransferAnimationDuration(2000);
                screen.getMenu().setCarried(screen.getMenu().getSlot(0).getItem().copy());
                screen.getMenu().getSlot(0).set(ItemStack.EMPTY);
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                var screen = (AbstractContainerScreen<?>) client.gui.screen();
                try {
                    var field = AbstractContainerScreen.class.getDeclaredField("pickupAnimation");
                    field.setAccessible(true);
                    check(field.get(screen) != null, "Pickup animation started");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Pickup animation state unavailable", e);
                }
            });
            context.waitTicks(45);
            context.runOnClient(client -> {
                var menu = client.player.containerMenu;
                menu.getSlot(0).set(menu.getCarried().copy());
                menu.setCarried(ItemStack.EMPTY);
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                var screen = (AbstractContainerScreen<?>) client.gui.screen();
                check(!((ExtraScreenData) screen).getReturnAnimations().isEmpty(), "Return animation started");
            });
            context.takeScreenshot("immersiveui-furnace-return");
            context.waitTicks(45);
            context.runOnClient(client -> check(((ExtraScreenData) client.gui.screen())
                    .getReturnAnimations().isEmpty(), "Return animation finished"));

            context.runOnClient(client -> {
                var inventory = client.player.getInventory();
                var menu = new EnchantmentMenu(2, inventory);
                client.player.containerMenu = menu;
                client.gui.setScreen(new EnchantmentScreen(menu, inventory, Component.literal("Enchanting")));
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                var inventory = client.player.getInventory();
                var menu = new AnvilMenu(3, inventory);
                client.player.containerMenu = menu;
                client.gui.setScreen(new AnvilScreen(menu, inventory, Component.literal("Anvil")));
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                var inventory = client.player.getInventory();
                var menu = new SmithingMenu(4, inventory);
                client.player.containerMenu = menu;
                client.gui.setScreen(new SmithingScreen(menu, inventory, Component.literal("Smithing")));
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                client.player.containerMenu = client.player.inventoryMenu;
                client.gui.setScreen(null);
            });
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
