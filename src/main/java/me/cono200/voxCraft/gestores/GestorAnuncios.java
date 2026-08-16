package me.cono200.voxCraft.gestores;

import me.cono200.voxCraft.VoxCraft;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
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
    private final List<Anuncio> anunciosFijos;
    private final List<Anuncio> anunciosDinamicos;
    private final List<Anuncio> anunciosActivos;
    private final Set<String> idsLeidos;
    private final File archivoLeidos;
    private final FileConfiguration configLeidos;
    private final File archivoFijos;

    public GestorAnuncios(VoxCraft plugin) {
        this.plugin = plugin;
        this.anunciosFijos = new ArrayList<>();
        this.anunciosDinamicos = new ArrayList<>();
        this.anunciosActivos = new ArrayList<>();
        this.idsLeidos = new HashSet<>();

        this.archivoLeidos = new File(plugin.getDataFolder(), "leidos.yml");
        this.configLeidos = YamlConfiguration.loadConfiguration(archivoLeidos);

        this.archivoFijos = new File(plugin.getDataFolder(), "anuncios_fijos.yml");

        crearArchivoFijosPorDefecto();
        cargarLeidos();
        cargarAnunciosFijos();
    }

    /**
     * Genera anuncios_fijos.yml con ejemplos claros si no existe.
     */
    private void crearArchivoFijosPorDefecto() {
        if (!archivoFijos.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                archivoFijos.createNewFile();

                FileConfiguration config = YamlConfiguration.loadConfiguration(archivoFijos);
                config.options().header(
                    "====================================================================\n" +
                    "               ANUNCIOS FIJOS / PERSONALIZADOS DE VOXCRAFT\n" +
                    "====================================================================\n" +
                    "Agrega aquí tus anuncios estáticos personalizados (tienda web, discord, tips, etc.)\n" +
                    "Estos anuncios se transmitirán intercalados con las noticias de la IA.\n"
                );

                // Ejemplos por defecto
                config.set("anuncios_fijos.tienda_web.categoria", "tienda");
                config.set("anuncios_fijos.tienda_web.mensajes", Arrays.asList(
                    "&a&l¡VISITA NUESTRA TIENDA WEB!",
                    "&7Consigue rangos, llaves y beneficios VIP en &bhttps://tienda.tu-servidor.com"
                ));

                config.set("anuncios_fijos.discord_comunidad.categoria", "social");
                config.set("anuncios_fijos.discord_comunidad.mensajes", Arrays.asList(
                    "&b&l¡ÚNETE A NUESTRO DISCORD!",
                    "&7Entérate de eventos exclusivos y habla con la comunidad en &f&ldiscord.gg/tu-servidor"
                ));

                config.set("anuncios_fijos.tip_sethome.categoria", "tips");
                config.set("anuncios_fijos.tip_sethome.mensajes", Arrays.asList(
                    "&e&lCONSEJO DE JUEGO:",
                    "&7Guarda la ubicación de tu base con &f/sethome &7para no perderte jamás."
                ));

                config.save(archivoFijos);
                plugin.getLogger().info("Archivo anuncios_fijos.yml creado con ejemplos por defecto.");
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear el archivo anuncios_fijos.yml: " + e.getMessage());
            }
        }
    }

    /**
     * Carga o recarga los anuncios fijos desde anuncios_fijos.yml
     */
    public synchronized void cargarAnunciosFijos() {
        anunciosFijos.clear();
        if (!archivoFijos.exists()) {
            crearArchivoFijosPorDefecto();
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(archivoFijos);
        ConfigurationSection section = config.getConfigurationSection("anuncios_fijos");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection sub = section.getConfigurationSection(key);
                if (sub == null) continue;

                String categoria = sub.getString("categoria", "fijo");
                List<String> mensajes = sub.getStringList("mensajes");
                if (!mensajes.isEmpty()) {
                    anunciosFijos.add(new Anuncio("fijo_" + key, categoria, mensajes));
                }
            }
        }

        reconstruirAnunciosActivos();
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

    /**
     * Procesa los anuncios dinámicos provenientes del JSON descargado de GitHub.
     */
    public synchronized void actualizarAnuncios(JsonArray jsonAnuncios) {
        anunciosDinamicos.clear();
        if (jsonAnuncios != null) {
            for (JsonElement el : jsonAnuncios) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String id = obj.has("id") ? obj.get("id").getAsString() : "";
                String categoria = obj.has("categoria") ? obj.get("categoria").getAsString() : "noticias";

                List<String> mensajes = new ArrayList<>();
                if (obj.has("mensajes") && obj.get("mensajes").isJsonArray()) {
                    for (JsonElement line : obj.get("mensajes").getAsJsonArray()) {
                        String text = line.getAsString().trim();
                        if (!text.isEmpty()) {
                            mensajes.add(text);
                        }
                    }
                }

                if (!id.isEmpty() && !mensajes.isEmpty()) {
                    anunciosDinamicos.add(new Anuncio("dinamico_" + id, categoria, mensajes));
                }
            }
        }

        reconstruirAnunciosActivos();
    }

    /**
     * Combina anuncios fijos y dinámicos en la lista global activa.
     */
    private synchronized void reconstruirAnunciosActivos() {
        anunciosActivos.clear();
        anunciosActivos.addAll(anunciosFijos);
        anunciosActivos.addAll(anunciosDinamicos);

        plugin.getLogger().info("Cargados en memoria: " + anunciosFijos.size() + " anuncios fijos y "
                + anunciosDinamicos.size() + " anuncios dinámicos de IA (Total: " + anunciosActivos.size() + ").");
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
                plugin.getLogger().info("Todos los anuncios han sido mostrados. Reiniciando ciclo.");
                idsLeidos.clear();
                guardarLeidos();
                disponibles.addAll(anunciosActivos);
            } else {
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

        // Transmitir todas las líneas del anuncio juntas en la misma ejecución
        for (String linea : anuncio.getMensajes()) {
            if (linea == null || linea.trim().isEmpty()) continue;
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
