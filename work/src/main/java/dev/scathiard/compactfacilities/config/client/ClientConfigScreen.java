package dev.scathiard.compactfacilities.config.client;

import dev.scathiard.compactfacilities.CompactFacilities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CompactFacilities.ID, dist = Dist.CLIENT)
public final class ClientConfigScreen {
    public ClientConfigScreen(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
