package cn.paper_card.logo_and_motd;

import org.bukkit.util.CachedServerIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.UUID;

class McAvatarServerIconService {

    private final @NotNull HashMap<UUID, CachedServerIcon> cache;
    private final @NotNull LogoAndMotd plugin;

    McAvatarServerIconService(@NotNull LogoAndMotd plugin) {
        this.cache = new HashMap<>();
        this.plugin = plugin;
    }

    private @NotNull CachedServerIcon transform(@NotNull BufferedImage image) throws Exception {

        final BufferedImage image1 = new BufferedImage(64, 64, BufferedImage.TYPE_4BYTE_ABGR);
        final Graphics graphics = image1.getGraphics();
        graphics.drawImage(image.getScaledInstance(64, 64, Image.SCALE_SMOOTH), 0, 0, null);
        graphics.dispose();

        return this.plugin.getServer().loadServerIcon(image1);
    }

    private static @NotNull URL avatarUrl(@NotNull UUID uuid) {
        // https://crafatar.com/avatars/10d69de6-b0e9-4a59-a6f3-b2dc51219828?size=64&overlay

        final String formatted = "https://crafatar.com/avatars/%s?&overlay".formatted(uuid.toString().replace("-", ""));

        try {
            return new URL(formatted);
        } catch (MalformedURLException e) {
            // 确认是没问题的URL
            throw new RuntimeException(e);
        }
    }


    private @NotNull BufferedImage fetchByNetwork(@NotNull UUID uuid) throws Exception {

        final URL url = avatarUrl(uuid);

        final URLConnection connection = url.openConnection();

        final InputStream inputStream = connection.getInputStream();

        final BufferedImage read;
        try {
            assert inputStream != null;
            read = ImageIO.read(inputStream);

            if (read == null) throw new IOException("Failed to read image.");

        } catch (IOException e) {
            try {
                inputStream.close();
            } catch (IOException ignored) {
            }
            throw e;
        }

        inputStream.close();

        return read;
    }

    public @Nullable CachedServerIcon getFromCache(@NotNull UUID uuid) {
        synchronized (this.cache) {
            final CachedServerIcon icon = this.cache.get(uuid);
            if (icon != null) return icon;

            this.plugin.getServer().getAsyncScheduler().runNow(this.plugin, (task) -> {

                final BufferedImage image;
                try {
                    image = this.fetchByNetwork(uuid);
                } catch (Exception e) {
                    this.plugin.getLogger().warning(e.toString());
                    return;
                }

                final CachedServerIcon i;

                try {
                    i = this.transform(image);
                } catch (Exception e) {
                    plugin.getSLF4JLogger().error("将BufferedImage转为CachedServerIcon时异常：", e);
                    return;
                }

                // 放入缓存
                synchronized (this.cache) {
                    this.cache.put(uuid, i);
                }
            });

            return null;
        }
    }
}
