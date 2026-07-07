package me.cono200.voxCraft.tareas;

import me.cono200.voxCraft.VoxCraft;
import org.bukkit.scheduler.BukkitRunnable;

public class TareaAnuncio extends BukkitRunnable {

    private final VoxCraft plugin;

    public TareaAnuncio(VoxCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getGestorAnuncios().emitirSiguienteAnuncio();
    }
}
