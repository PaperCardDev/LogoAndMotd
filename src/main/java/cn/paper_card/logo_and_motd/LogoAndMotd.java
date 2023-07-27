package cn.paper_card.logo_and_motd;

import cn.paper_card.player_last_quit.PlayerLastQuitApi;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.CachedServerIcon;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class LogoAndMotd extends JavaPlugin {

    private final @NotNull CachedServerIcon serverIcon;

    private final @NotNull TextComponent[] basicMotd;

    private final @NotNull SessionManager sessionManager;

    private final @NotNull McAvatarServerIconService avatarServerIconService;

    public LogoAndMotd() {

        this.serverIcon = this.loadCustomServerIconFromResource();

        this.sessionManager = new SessionManager();

        this.basicMotd = new TextComponent[]{
                this.createCustomMotd(Component.text("你在你的建筑上花费的时间使它变得如此重要")
                        .color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD)),
                this.createCustomMotd(Component.text("不谈声色犬马，花点时间生活 --半日闲")
                        .color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD)),
                this.createCustomMotd(Component.text("玩Minecraft最好的配置不是多好的电脑而是朋友")
                        .color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD)),

        };

        this.avatarServerIconService = new McAvatarServerIconService(this);
    }

    private @NotNull TextComponent createCustomMotd(@NotNull TextComponent secondLine) {
        return Component.text()
                .append(Component.text("纸片 PaperCard").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .append(Component.text(" | "))
                .append(Component.text(this.getServer().getMinecraftVersion()).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text(" | "))
                .append(Component.text("正版公益").color(NamedTextColor.BLUE).decorate(TextDecoration.BOLD))
                .append(Component.text(" | "))
                .append(Component.text("原版纯净生存").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                .append(Component.newline())
                .append(secondLine)
                .build();
    }


    private @NotNull CachedServerIcon loadCustomServerIconFromResource() {
        final InputStream resourceAsStream = this.getClass().getResourceAsStream("server-icon.png");

        assert resourceAsStream != null;

        final BufferedImage image;
        try {
            image = ImageIO.read(resourceAsStream);
        } catch (IOException e) {
            try {
                resourceAsStream.close();
            } catch (IOException ignored) {
            }
            throw new RuntimeException(e);
        }

        // 关流
        try {
            resourceAsStream.close();
        } catch (IOException ignored) {
        }


        try {
            return this.getServer().loadServerIcon(image);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean checkPossiblePlayer(@NotNull String ip, @NotNull PaperServerListPingEvent event) {
        final Plugin plugin = getServer().getPluginManager().getPlugin("PlayerLastQuit");

        if (!(plugin instanceof final PlayerLastQuitApi api)) {
            getLogger().warning("PlayerLatQuit插件未安装！");
            return false;
        }

        // 查询该IP可能的玩家
        final List<PlayerLastQuitApi.Info> players;

        // 数据库IO耗时

        try {
            players = api.queryByIp(ip);
        } catch (Exception e) {
            getLogger().severe(e.toString());
            e.printStackTrace();
            return false;
        }

        final int size = players.size();

        // 该IP没有查到玩家
        if (size == 0) return false;

        // 可能使用的Logo，元素非空
        final ArrayList<CachedServerIcon> icons = new ArrayList<>();
        icons.add(this.serverIcon);

        // MC头像
        for (final PlayerLastQuitApi.Info player : players) {
            final CachedServerIcon query = this.avatarServerIconService.getFromCache(player.uuid());
            if (query != null) icons.add(query);
        }

        // 会话
        final Session session = this.sessionManager.getSession(ip);

        // 选择Logo
        int logoIndex = session.getLogoIndex();
        logoIndex = Math.max(logoIndex, 0);
        logoIndex %= icons.size();
        session.setLogoIndex(logoIndex + 1);

        event.setServerIcon(icons.get(logoIndex));

        // motd
        final ArrayList<TextComponent> allMotd = new ArrayList<>(List.of(this.basicMotd));

        for (final PlayerLastQuitApi.Info player : players) {
            final String name = player.name();
            allMotd.add(Component.text()
                    .append(Component.text("这是 ").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
                    .append(Component.text(name).color(NamedTextColor.RED).decorate(TextDecoration.BOLD))
                    .append(Component.text(" 的专属服务器呀~").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
                    .build());
        }

        int motdIndex = session.getMotdIndex();
        motdIndex = Math.max(0, motdIndex);
        motdIndex %= allMotd.size();
        session.setMotdIndex(motdIndex + 1);

        event.motd(allMotd.get(motdIndex));

        return true;
    }

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(new OnServerListPing(), this);
    }

    private class OnServerListPing implements Listener {

        @EventHandler
        public void on(@NotNull PaperServerListPingEvent event) {

            final String ip = event.getAddress().getHostAddress(); // 源ip地址

            event.setHidePlayers(false); // 取消人数隐藏

            getServer().setMaxPlayers(Math.max(99, getServer().getOfflinePlayers().length));
            event.setMaxPlayers(getServer().getMaxPlayers()); // 最大在线人数

            event.getPlayerSample().clear(); // 不能预览在线玩家

            final Session session = sessionManager.getSession(ip);

            // 根据可能的玩家设置专属的头像和motd
            if (checkPossiblePlayer(ip, event)) return;

            // 我们的默认图标
            event.setServerIcon(serverIcon);

            // motd，轮着来
            int motdIndex = session.getMotdIndex();
            motdIndex = Math.max(0, motdIndex);
            motdIndex %= basicMotd.length;
            session.setMotdIndex(motdIndex + 1);

            event.motd(basicMotd[motdIndex]);
        }
    }

    private static class Session {
        private int logoIndex = 0;
        private int motdIndex = 0;


        public int getLogoIndex() {
            return logoIndex;
        }

        public void setLogoIndex(int logoIndex) {
            this.logoIndex = logoIndex;
        }

        public int getMotdIndex() {
            return motdIndex;
        }

        public void setMotdIndex(int motdIndex) {
            this.motdIndex = motdIndex;
        }
    }

    private static class SessionManager {

        private final HashMap<String, Session> sessions = new HashMap<>();


        @NotNull Session getSession(@NotNull String ip) {
            synchronized (this) {
                Session session = this.sessions.get(ip);
                if (session != null) return session;

                session = new Session();
                this.sessions.put(ip, session);
                return session;
            }
        }
    }
}
