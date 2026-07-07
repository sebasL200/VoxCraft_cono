package me.cono200.voxCraft.gestores;

import me.cono200.voxCraft.VoxCraft;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GestorDescarga {

    private final VoxCraft plugin;

    public GestorDescarga(VoxCraft plugin) {
        this.plugin = plugin;
    }

    public void descargarAnunciosAsync() {
        String urlString = plugin.getConfigManager().getUrlAnuncios();
        plugin.getLogger().info("Iniciando descarga de anuncios desde: " + urlString);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                        
                        // Procesar datos de vuelta en el hilo principal de Minecraft de forma segura
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (root.has("anuncios")) {
                                plugin.getGestorAnuncios().actualizarAnuncios(root.getAsJsonArray("anuncios"));
                            }
                            if (root.has("horas_felices")) {
                                plugin.getIntegracionHorasFelices().inyectarHorasFelices(root.getAsJsonArray("horas_felices"));
                            }
                            plugin.getLogger().info("¡Datos descargados y procesados correctamente!");
                        });
                    }
                } else {
                    plugin.getLogger().warning("No se pudo descargar anuncios. Código HTTP: " + responseCode);
                }
                connection.disconnect();
            } catch (Exception e) {
                plugin.getLogger().severe("Error al realizar la petición HTTP para descargar anuncios: " + e.getMessage());
            }
        });
    }
}
