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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IntegracionHorasFelices {

    private final VoxCraft plugin;
    private final File diasFile;

    public IntegracionHorasFelices(VoxCraft plugin) {
        this.plugin = plugin;
        this.diasFile = new File(plugin.getDataFolder(), "voxcraft_dias.yml");
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
     * Carga los días que VoxCraft inyectó en la ejecución anterior.
     */
    private Set<DayOfWeek> cargarDiasPrevios() {
        Set<DayOfWeek> dias = new HashSet<>();
        if (!diasFile.exists()) return dias;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(diasFile);
        List<String> lista = cfg.getStringList("dias_voxcraft");
        for (String d : lista) {
            try {
                dias.add(DayOfWeek.valueOf(d.toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }
        return dias;
    }

    /**
     * Guarda en disco los días que VoxCraft acaba de inyectar.
     */
    private void guardarDiasInyectados(Set<DayOfWeek> dias) {
        FileConfiguration cfg = new YamlConfiguration();
        List<String> lista = new ArrayList<>();
        for (DayOfWeek d : dias) {
            lista.add(d.name());
        }
        cfg.set("dias_voxcraft", lista);
        try {
            cfg.save(diasFile);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar voxcraft_dias.yml: " + e.getMessage());
        }
    }

    /**
     * Parsea e inyecta los 2 eventos de la IA respetando los días nativos del plugin HorasFelices.
     */
    public void inyectarHorasFelices(JsonArray horasFelicesJson) {
        HorasFelicesAPI api = obtenerAPI();
        if (api == null) {
            plugin.getLogger().warning("No se pudo inyectar las Horas Felices: El plugin 'HorasFelicesEnAdminShop_cono' o su API no están disponibles.");
            return;
        }

        // Cargar los días que VoxCraft controló la semana pasada y limpiar SOLO esos días
        Set<DayOfWeek> diasPrevios = cargarDiasPrevios();
        if (!diasPrevios.isEmpty()) {
            plugin.getLogger().info("Limpiando " + diasPrevios.size() + " días previos de VoxCraft del itinerario...");
            for (DayOfWeek dia : diasPrevios) {
                try {
                    api.inyectarEventoFestivo(dia, null);
                } catch (Exception ignored) {}
            }
        }

        if (horasFelicesJson == null || horasFelicesJson.size() == 0) {
            plugin.getLogger().info("No se encontraron Horas Felices en el archivo JSON.");
            guardarDiasInyectados(new HashSet<>());
            return;
        }

        plugin.getLogger().info("Iniciando inyección de " + horasFelicesJson.size() + " Horas Felices inyectadas por la IA...");

        Set<DayOfWeek> diasNuevos = new HashSet<>();

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

                EventoPrision evento = new EventoPrision(
                        titulo, desc, tipo, material,
                        porcentajeExtra, efectoPocion, nivelEfecto,
                        todoElDia, horaInicio, horaFin
                );

                api.inyectarEventoFestivo(dia, evento);
                diasNuevos.add(dia);
                plugin.getLogger().info("¡Inyectado con éxito evento de Hora Feliz para el día " + dia + ": " + titulo);

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Error de formato al procesar un evento de Hora Feliz: " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().severe("Error inesperado al inyectar evento de Hora Feliz: " + e.getMessage());
            }
        }

        // Guardar los días nuevos para limpiarlos selectivamente la próxima semana
        guardarDiasInyectados(diasNuevos);
        plugin.getLogger().info("VoxCraft registró " + diasNuevos.size() + " días propios en voxcraft_dias.yml.");
    }
}
