package me.totalfreedom.bukkittelnet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import me.totalfreedom.bukkittelnet.api.TelnetRequestDataTagsEvent;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class PlayerEventListener implements Listener
{

    private final BukkitTelnet plugin;

    public PlayerEventListener(BukkitTelnet plugin)
    {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        triggerPlayerListUpdates();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        triggerPlayerListUpdates();
    }

    private static BukkitTask updateTask = null;

    public void triggerPlayerListUpdates()
    {
        if (updateTask != null)
        {
            updateTask.cancel();
        }

        updateTask = new BukkitRunnable()
        {
            @Override
            public void run()
            {
                final SocketListener socketListener = plugin.telnet.getSocketListener();
                if (socketListener != null)
                {
                    final TelnetRequestDataTagsEvent event = new TelnetRequestDataTagsEvent();
                    Bukkit.getServer().getPluginManager().callEvent(event);
                    socketListener.triggerPlayerListUpdates(generatePlayerList(event.getDataTags()));
                }
            }
        }.runTaskLater(plugin, 20L * 2L);
    }

    @SuppressWarnings("unchecked")
    private static String generatePlayerList(final Map<Player, Map<String, Object>> dataTags)
    {
        final JSONArray players = new JSONArray();

        final Iterator<Map.Entry<Player, Map<String, Object>>> dataTagsIt = dataTags.entrySet().iterator();
        while (dataTagsIt.hasNext())
        {
            final HashMap<String, String> info = new HashMap<>();

            final Map.Entry<Player, Map<String, Object>> dataTagsEntry = dataTagsIt.next();
            final Player player = dataTagsEntry.getKey();
            final Map<String, Object> playerTags = dataTagsEntry.getValue();

            info.put("name", player.getName());
            info.put("ip", player.getAddress().getAddress().getHostAddress());
            info.put("displayName", StringUtils.trimToEmpty(player.getDisplayName()));
            info.put("uuid", player.getUniqueId().toString());

            final Iterator<Map.Entry<String, Object>> playerTagsIt = playerTags.entrySet().iterator();
            while (playerTagsIt.hasNext())
            {
                final Map.Entry<String, Object> playerTagsEntry = playerTagsIt.next();
                final Object value = playerTagsEntry.getValue();
                info.put(playerTagsEntry.getKey(), value != null ? value.toString() : "null");
            }

            players.add(info);
        }

        final JSONObject response = new JSONObject();
        response.put("players", players);

        return response.toJSONString();
    }
}
