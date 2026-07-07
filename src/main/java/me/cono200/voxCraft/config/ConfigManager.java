package me.cono200.voxCraft.config;

import me.cono200.voxCraft.VoxCraft;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final VoxCraft plugin;
    private String urlAnuncios;
    private int intervaloDescargaMinutos;
    private int intervaloAnuncioSegundos;
    private String prefijoAnuncios;
    private boolean ciclarAnuncios;

    public ConfigManager(VoxCraft plugin) {
        this.plugin = plugin;
        cargarConfig();
    }

    public void cargarConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.urlAnuncios = config.getString("url_anuncios", "https://raw.githubusercontent.com/sebasL200/VoxCraft_cono/main/anuncios.json");
        this.intervaloDescargaMinutos = config.getInt("intervalo_descarga_minutos", 60);
        this.intervaloAnuncioSegundos = config.getInt("intervalo_anuncio_segundos", 300);
        this.prefijoAnuncios = config.getString("prefijo_anuncios", "&8[&b&lVoxCraft&8] ");
        this.ciclarAnuncios = config.getBoolean("ciclar_anuncios", true);
    }

    public String getUrlAnuncios() {
        return urlAnuncios;
    }

    public int getIntervaloDescargaMinutos() {
        return intervaloDescargaMinutos;
    }

    public int getIntervaloAnuncioSegundos() {
        return intervaloAnuncioSegundos;
    }

    public String getPrefijoAnuncios() {
        return prefijoAnuncios;
    }

    public boolean isCiclarAnuncios() {
        return ciclarAnuncios;
    }
}
