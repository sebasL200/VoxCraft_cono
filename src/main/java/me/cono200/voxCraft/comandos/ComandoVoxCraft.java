package me.cono200.voxCraft.comandos;

import me.cono200.voxCraft.VoxCraft;
import me.cono200.voxCraft.test.PruebaConexion;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ComandoVoxCraft implements CommandExecutor, TabCompleter {

    private final VoxCraft plugin;

    public ComandoVoxCraft(VoxCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("voxcraft.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permiso para ejecutar este comando.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.GRAY + "---------- " + ChatColor.AQUA + "Ayuda de VoxCraft" + ChatColor.GRAY + " ----------");
            sender.sendMessage(ChatColor.YELLOW + "/voxcraft reload " + ChatColor.WHITE + "- Recarga la configuración del plugin.");
            sender.sendMessage(ChatColor.YELLOW + "/voxcraft forcefetch " + ChatColor.WHITE + "- Descarga de inmediato anuncios.json.");
            sender.sendMessage(ChatColor.YELLOW + "/voxcraft test " + ChatColor.WHITE + "- Simula una inyección en HorasFelices.");
            sender.sendMessage(ChatColor.GRAY + "---------------------------------------");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                plugin.getConfigManager().cargarConfig();
                plugin.reiniciarTareas();
                sender.sendMessage(ChatColor.GREEN + "[✔] Configuración de VoxCraft recargada con éxito.");
                break;
            case "forcefetch":
                plugin.getGestorDescarga().descargarAnunciosAsync();
                sender.sendMessage(ChatColor.GREEN + "[✔] Petición de descarga forzada enviada en segundo plano.");
                break;
            case "test":
                PruebaConexion.ejecutarPruebaInyeccion(plugin, sender);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /voxcraft para ver la ayuda.");
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = Arrays.asList("reload", "forcefetch", "test");
            return list.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
