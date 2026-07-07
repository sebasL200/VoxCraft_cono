package me.cono200.voxCraft;

import me.cono200.voxCraft.comandos.ComandoVoxCraft;
import me.cono200.voxCraft.config.ConfigManager;
import me.cono200.voxCraft.gestores.GestorAnuncios;
import me.cono200.voxCraft.gestores.GestorDescarga;
import me.cono200.voxCraft.integracion.IntegracionHorasFelices;
import me.cono200.voxCraft.tareas.TareaAnuncio;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

public final class VoxCraft extends JavaPlugin {

    private ConfigManager configManager;
    private GestorAnuncios gestorAnuncios;
    private GestorDescarga gestorDescarga;
    private IntegracionHorasFelices integracionHorasFelices;

    private BukkitTask tareaDescarga;
    private BukkitTask tareaAnuncio;

    @Override
    public void onEnable() {
        getLogger().info("Iniciando VoxCraft...");

        // 1. Inicializar Gestores y Configuración
        this.configManager = new ConfigManager(this);
        this.gestorAnuncios = new GestorAnuncios(this);
        this.integracionHorasFelices = new IntegracionHorasFelices(this);
        this.gestorDescarga = new GestorDescarga(this);

        // 2. Registrar Comandos y Autocompletado
        ComandoVoxCraft comandoExecutor = new ComandoVoxCraft(this);
        Objects.requireNonNull(getCommand("voxcraft")).setExecutor(comandoExecutor);
        Objects.requireNonNull(getCommand("voxcraft")).setTabCompleter(comandoExecutor);

        // 3. Programar las Tareas Periódicas
        reiniciarTareas();

        getLogger().info("=========================================");
        getLogger().info("  VoxCraft 1.0 ha sido activado con éxito");
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        // Cancelar las tareas si están corriendo
        if (tareaDescarga != null) {
            tareaDescarga.cancel();
        }
        if (tareaAnuncio != null) {
            tareaAnuncio.cancel();
        }
        getLogger().info("VoxCraft desactivado.");
    }

    /**
     * Cancela las tareas activas y las vuelve a programar según los nuevos intervalos de configuración.
     */
    public void reiniciarTareas() {
        if (tareaDescarga != null) {
            tareaDescarga.cancel();
        }
        if (tareaAnuncio != null) {
            tareaAnuncio.cancel();
        }

        // Descarga inicial inmediata
        gestorDescarga.descargarAnunciosAsync();

        // 1. Tarea para descargar anuncios (Asíncrona)
        long ticksDescarga = configManager.getIntervaloDescargaMinutos() * 60L * 20L;
        tareaDescarga = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            gestorDescarga.descargarAnunciosAsync();
        }, ticksDescarga, ticksDescarga);

        // 2. Tarea para emitir anuncios (Síncrona en el hilo principal)
        long ticksAnuncio = configManager.getIntervaloAnuncioSegundos() * 20L;
        tareaAnuncio = new TareaAnuncio(this).runTaskTimer(this, ticksAnuncio, ticksAnuncio);

        getLogger().info("Tareas y temporizadores de VoxCraft programados / reiniciados.");
    }

    // --- GETTERS ---
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GestorAnuncios getGestorAnuncios() {
        return gestorAnuncios;
    }

    public GestorDescarga getGestorDescarga() {
        return gestorDescarga;
    }

    public IntegracionHorasFelices getIntegracionHorasFelices() {
        return integracionHorasFelices;
    }
}
