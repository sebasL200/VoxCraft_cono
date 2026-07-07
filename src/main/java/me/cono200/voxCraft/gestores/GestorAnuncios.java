package me.cono200.voxCraft.gestores;

import me.cono200.voxCraft.VoxCraft;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GestorAnuncios {

    public static class Anuncio {
        private final String id;
        private final String categoria;
        private final List<String> mensajes;

        public Anuncio(String id, String categoria, List<String> mensajes) {
            this.id = id;
            this.categoria = categoria;
            this.mensajes = mensajes;
        }

        public String getId() { return id; }
        public String getCategoria() { return categoria; }
        public List<String> getMensajes() { return mensajes; }
    }

    private final VoxCraft plugin;
    private final List<Anuncio> anunciosActivos;
    private final Set<String> idsLeidos;
    private final File archivoLeidos;
    private final FileConfiguration configLeidos;

    public GestorAnuncios(VoxCraft plugin) {
        this.plugin = plugin;
        this.anunciosActivos = new ArrayList<>();
        this.idsLeidos = new HashSet<>();
        this.archivoLeidos = new File(plugin.getDataFolder(), "leidos.yml");
        this.configLeidos = YamlConfiguration.loadConfiguration(archivoLeidos);
        cargarLeidos();
    }

    private void cargarLeidos() {
        if (archivoLeidos.exists()) {
            List<String> lista = configLeidos.getStringList("leidos");
            idsLeidos.addAll(lista);
        }
    }

    private void guardarLeidos() {
        configLeidos.set("leidos", new ArrayList<>(idsLeidos));
        try {
            configLeidos.save(archivoLeidos);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar el archivo leidos.yml");
        }
    }

    public synchronized void actualizarAnuncios(JsonArray jsonAnuncios) {
        anunciosActivos.clear();
        if (jsonAnuncios == null) return;

        for (JsonElement el : jsonAnuncios) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            String id = obj.has("id") ? obj.get("id").getAsString() : "";
            String categoria = obj.has("categoria") ? obj.get("categoria").getAsString() : "";
            List<String> mensajes = new ArrayList<>();
            if (obj.has("mensajes") && obj.get("mensajes").isJsonArray()) {
                for (JsonElement line : obj.get("mensajes").getAsJsonArray()) {
                    mensajes.add(line.getAsString());
                }
            }
            if (!id.isEmpty()) {
                anunciosActivos.add(new Anuncio(id, categoria, mensajes));
            }
        }
        plugin.getLogger().info("Se han cargado " + anunciosActivos.size() + " anuncios activos en memoria.");
    }

    public synchronized void emitirSiguienteAnuncio() {
        if (anunciosActivos.isEmpty()) {
            return;
        }

        // Buscar anuncios no leídos
        List<Anuncio> disponibles = new ArrayList<>();
        for (Anuncio a : anunciosActivos) {
            if (!idsLeidos.contains(a.getId())) {
                disponibles.add(a);
            }
        }

        if (disponibles.isEmpty()) {
            if (plugin.getConfigManager().isCiclarAnuncios()) {
                plugin.getLogger().info("Todos los anuncios han sido mostrados. Reiniciando lista de leídos.");
                idsLeidos.clear();
                guardarLeidos();
                disponibles.addAll(anunciosActivos);
            } else {
                // No hay nuevos anuncios y ciclar está desactivado
                return;
            }
        }

        // Seleccionar uno al azar
        Anuncio seleccionado = disponibles.get(new Random().nextInt(disponibles.size()));
        transmitirAnuncio(seleccionado);

        // Registrar como leído
        idsLeidos.add(seleccionado.getId());
        guardarLeidos();
    }

    private void transmitirAnuncio(Anuncio anuncio) {
        String prefijo = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getPrefijoAnuncios());
        
        // Transmitir cada línea al chat del servidor
        for (String linea : anuncio.getMensajes()) {
            String msgFormateado = prefijo + ChatColor.translateAlternateColorCodes('&', linea);
            Bukkit.broadcastMessage(msgFormateado);
        }
    }

    public synchronized List<Anuncio> getAnunciosActivos() {
        return new ArrayList<>(anunciosActivos);
    }

    public synchronized void limpiarHistorialLeidos() {
        idsLeidos.clear();
        guardarLeidos();
    }
}
