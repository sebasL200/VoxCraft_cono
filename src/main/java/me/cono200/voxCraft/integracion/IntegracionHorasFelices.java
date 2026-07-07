package me.cono200.voxCraft.integracion;

import me.cono200.voxCraft.VoxCraft;
import me.cono200.horasFelicesEnAdminShop_cono.Api.HorasFelicesAPI;
import me.cono200.horasFelicesEnAdminShop_cono.Api.EventoPrision;
import me.cono200.horasFelicesEnAdminShop_cono.Api.TipoEvento;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.potion.PotionEffectType;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

public class IntegracionHorasFelices {

    private final VoxCraft plugin;

    public IntegracionHorasFelices(VoxCraft plugin) {
        this.plugin = plugin;
    }

    /**
     * Intenta obtener la API de HorasFelices desde el ServicesManager de Bukkit.
     */
    private HorasFelicesAPI obtenerAPI() {
        if (Bukkit.getPluginManager().getPlugin("HorasFelicesEnAdminShop_cono") == null) {
            return null;
        }
        RegisteredServiceProvider<HorasFelicesAPI> provider = Bukkit.getServicesManager().getRegistration(HorasFelicesAPI.class);
        return provider != null ? provider.getProvider() : null;
    }

    /**
     * Parsea e inyecta los eventos del JSON a través de la API.
     */
    public void inyectarHorasFelices(JsonArray horasFelicesJson) {
        HorasFelicesAPI api = obtenerAPI();
        if (api == null) {
            plugin.getLogger().warning("No se pudo inyectar las Horas Felices: El plugin 'HorasFelicesEnAdminShop_cono' o su API no están disponibles.");
            return;
        }

        // Limpiar todos los días del calendario semanal antes de inyectar los nuevos
        plugin.getLogger().info("Limpiando el itinerario semanal de Horas Felices previo a la inyección...");
        for (DayOfWeek diaSemana : DayOfWeek.values()) {
            try {
                api.inyectarEventoFestivo(diaSemana, null);
            } catch (Exception ignored) {}
        }

        if (horasFelicesJson == null || horasFelicesJson.size() == 0) {
            plugin.getLogger().info("No se encontraron Horas Felices en el archivo JSON.");
            return;
        }

        plugin.getLogger().info("Iniciando inyección de " + horasFelicesJson.size() + " Horas Felices inyectadas por la IA...");

        for (JsonElement el : horasFelicesJson) {
            if (!el.isJsonObject()) continue;
            try {
                JsonObject obj = el.getAsJsonObject();
                
                String diaStr = obj.get("dia").getAsString();
                String titulo = obj.get("titulo").getAsString();
                
                List<String> desc = new ArrayList<>();
                if (obj.has("descripcion") && obj.get("descripcion").isJsonArray()) {
                    for (JsonElement dEl : obj.getAsJsonArray("descripcion")) {
                        desc.add(dEl.getAsString());
                    }
                }

                String tipoStr = obj.get("tipo").getAsString();
                String itemStr = obj.get("item").getAsString();
                double porcentajeExtra = obj.get("porcentaje_extra").getAsDouble();
                String efectoPocionStr = obj.get("efecto_pocion").getAsString();
                int nivelEfecto = obj.get("nivel_efecto").getAsInt();
                boolean todoElDia = obj.get("todo_el_dia").getAsBoolean();
                int horaInicio = obj.get("hora_inicio").getAsInt();
                int horaFin = obj.get("hora_fin").getAsInt();

                // Resoluciones de Enums de Java/Bukkit
                DayOfWeek dia = DayOfWeek.valueOf(diaStr.toUpperCase());
                TipoEvento tipo = TipoEvento.valueOf(tipoStr.toUpperCase());
                Material material = Material.valueOf(itemStr.toUpperCase());
                
                PotionEffectType efectoPocion = null;
                if (!efectoPocionStr.equalsIgnoreCase("NONE")) {
                    efectoPocion = PotionEffectType.getByName(efectoPocionStr.toUpperCase());
                    if (efectoPocion == null) {
                        plugin.getLogger().warning("Efecto de poción no válido: " + efectoPocionStr + ". Se usará NONE.");
                    }
                }

                // Crear el evento utilizando las clases de HorasFelicesEnAdminShop_cono
                EventoPrision evento = new EventoPrision(
                        titulo,
                        desc,
                        tipo,
                        material,
                        porcentajeExtra,
                        efectoPocion,
                        nivelEfecto,
                        todoElDia,
                        horaInicio,
                        horaFin
                );

                // Inyectar el evento
                api.inyectarEventoFestivo(dia, evento);
                plugin.getLogger().info("¡Inyectado con éxito evento de Hora Feliz para el día " + dia + ": " + titulo);

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Error de formato al procesar un evento de Hora Feliz: " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().severe("Error inesperado al inyectar evento de Hora Feliz: " + e.getMessage());
            }
        }
    }
}
