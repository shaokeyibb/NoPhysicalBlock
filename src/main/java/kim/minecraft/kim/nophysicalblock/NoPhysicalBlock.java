package kim.minecraft.kim.nophysicalblock;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;

public final class NoPhysicalBlock extends JavaPlugin implements Listener, CommandExecutor {

    FileConfiguration config;

    File data = new File(getDataFolder(), "data.json");

    Material tool;
    String permission;

    final List<Ground> groundList = Lists.newArrayList();

    final Map<UUID, Ground> tempGround = Maps.newHashMap();

    public boolean isInGround(final Location loc) {
        return groundList.stream().anyMatch(result -> loc.toVector().isInAABB(result.from.toVector(), result.to.toVector()));
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent e) {
        if (isInGround(e.getSourceBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler
    public void onSelect(PlayerInteractEvent e) {
        if (!e.hasItem() || e.getMaterial() != tool || !e.getPlayer().hasPermission(permission)) return;
        if (!tempGround.containsKey(e.getPlayer().getUniqueId()))
            tempGround.put(e.getPlayer().getUniqueId(), new Ground(null, null));
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            tempGround.get(e.getPlayer().getUniqueId()).from = e.getClickedBlock().getLocation();
            e.getPlayer().sendMessage("点1已设置");
            e.setCancelled(true);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            tempGround.get(e.getPlayer().getUniqueId()).to = e.getClickedBlock().getLocation();
            e.getPlayer().sendMessage("点2已设置");
            e.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return false;
        if (!(sender instanceof Player)) {
            sender.sendMessage("只有玩家可以执行此指令");
            return true;
        }
        if (!sender.hasPermission(permission)) {
            sender.sendMessage("您没有权限这么做");
            return true;
        }

        if ("create".equalsIgnoreCase(args[0])) {
            final Player player = (Player) sender;
            if (!tempGround.containsKey(player.getUniqueId()) || (tempGround.get(player.getUniqueId()).from == null || tempGround.get(player.getUniqueId()).to == null)) {
                sender.sendMessage("请先使用 " + tool.name() + " 圈点后再执行该指令");
                return true;
            }
            if (tempGround.get(player.getUniqueId()).from.getWorld() != tempGround.get(player.getUniqueId()).to.getWorld()) {
                sender.sendMessage("错误: 两次圈点必须位于同一世界");
                return true;
            }
            if (tempGround.get(player.getUniqueId()).from == tempGround.get(player.getUniqueId()).to) {
                sender.sendMessage("错误: 两次圈点不能相同");
                return true;
            }

            groundList.add(tempGround.get(player.getUniqueId()));
            sender.sendMessage("地块添加成功");
            return true;
        } else if ("remove".equalsIgnoreCase(args[0])) {
            final Player player = (Player) sender;
            Optional<Ground> optional = groundList.stream().filter(result -> isInGround(player.getLocation())).findAny();
            if (!optional.isPresent()) {
                sender.sendMessage("您所在的区域没有地块可被移除");
                return true;
            }

            groundList.remove(optional.get());
            sender.sendMessage("地块移除成功");
            return true;
        } else {
            return false;
        }

    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveResource("config.yml", false);

        config = getConfig();

        final Material material = Material.getMaterial(config.getString("tool", "WOODEN_PICKAXE"));
        if (material == null) tool = Material.getMaterial("WOODEN_PICKAXE");
        else tool = material;

        permission = config.getString("permission", "npb.use");

        try {
            if (!data.exists()) saveResource("data.json", false);
            else new JsonParser().parse(new FileReader(data)).getAsJsonArray().forEach(element ->
            {
                JsonObject from = element.getAsJsonObject().get("from").getAsJsonObject();
                JsonObject to = element.getAsJsonObject().get("to").getAsJsonObject();
                groundList.add(new Ground(
                        new Location(Bukkit.getWorld(UUID.fromString(from.get("world").getAsString())),
                                from.get("x").getAsInt(),
                                from.get("y").getAsInt(),
                                from.get("z").getAsInt()),
                        new Location(Bukkit.getWorld(UUID.fromString(to.get("world").getAsString())),
                                to.get("x").getAsInt(),
                                to.get("y").getAsInt(),
                                to.get("z").getAsInt()))
                );
            });
        } catch (IOException ignored) {
        }

        Bukkit.getPluginCommand("npb").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        JsonArray array = new JsonArray();
        groundList.forEach(it -> {

            JsonObject global = new JsonObject();

            JsonObject from = new JsonObject();
            from.addProperty("world", it.from.getWorld().getUID().toString());
            from.addProperty("x", it.from.getBlockX());
            from.addProperty("y", it.from.getBlockY());
            from.addProperty("z", it.from.getBlockZ());

            JsonObject to = new JsonObject();
            to.addProperty("world", it.to.getWorld().getUID().toString());
            to.addProperty("x", it.to.getBlockX());
            to.addProperty("y", it.to.getBlockY());
            to.addProperty("z", it.to.getBlockZ());

            global.add("from", from);
            global.add("to", to);

            array.add(global);

            try (PrintWriter writer = new PrintWriter(new FileWriter(data, true))) {
                writer.print(array.toString());
            } catch (IOException ignored) {
            }
        });
    }

    public static class Ground {

        private Location from;
        private Location to;

        public Ground(Location from, Location to) {
            this.from = from;
            this.to = to;
        }
    }
}
