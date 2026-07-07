package me.cono200.voxCraft.test;

import me.cono200.voxCraft.VoxCraft;
import me.cono200.horasFelicesEnAdminShop_cono.Api.HorasFelicesAPI;
import me.cono200.horasFelicesEnAdminShop_cono.Api.EventoPrision;
import me.cono200.horasFelicesEnAdminShop_cono.Api.TipoEvento;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.potion.PotionEffectType;

import java.time.DayOfWeek;
import java.util.Arrays;

public class PruebaConexion {

    public static void ejecutarPruebaInyeccion(VoxCraft plugin, CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "[Test] Intentando obtener el servicio HorasFelicesAPI...");

        if (Bukkit.getPluginManager().getPlugin("HorasFelicesEnAdminShop_cono") == null) {
            sender.sendMessage(ChatColor.RED + "[Test] ERROR: El plugin 'HorasFelicesEnAdminShop_cono' no está activo o cargado.");
            return;
        }

        RegisteredServiceProvider<HorasFelicesAPI> provider = Bukkit.getServicesManager().getRegistration(HorasFelicesAPI.class);
        if (provider == null) {
            sender.sendMessage(ChatColor.RED + "[Test] ERROR: El servicio 'HorasFelicesAPI' no está registrado en el ServicesManager.");
            return;
        }

        HorasFelicesAPI api = provider.getProvider();
        
        try {
            sender.sendMessage(ChatColor.YELLOW + "[Test] Inyectando evento ficticio de prueba para el LUNES (MONDAY)...");

            // Crear un evento de prueba representativo
            EventoPrision eventoTest = new EventoPrision(
                    "&a&l¡CONEXIÓN ESTABLECIDA!",
                    Arrays.asList(
                            "&7Evento inyectado de forma remota por &bVoxCraft&7.",
                            "&7¡El Diamante vale un &a100% EXTRA &7y recibes &dPrisa Minera II&7!"
                    ),
                    TipoEvento.AMBOS,
                    Material.DIAMOND,
                    100.0,
                    PotionEffectType.HASTE,
                    2,
                    true,
                    0,
                    24
            );

            // Inyectar en el Lunes
            api.inyectarEventoFestivo(DayOfWeek.MONDAY, eventoTest);

            sender.sendMessage(ChatColor.GREEN + "[Test] ¡ÉXITO! Se ha inyectado el evento de prueba en el plugin de Horas Felices.");
            sender.sendMessage(ChatColor.GREEN + "[Test] Revisa el itinerario usando /horafeliz lineup o /horafeliz admin.");

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "[Test] Ocurrió un error al inyectar el evento: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
