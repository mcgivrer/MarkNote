package utils;

import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

/**
 * Utilitaire pour débouncer (retarder) l'exécution d'une action.
 * Utile pour éviter des rafraîchissements trop fréquents (ex: à chaque frappe).
 */
public class Debouncer {
    
    private final Timer timer;
    private TimerTask pendingTask;
    private final long delayMillis;
    
    /**
     * Crée un nouveau Debouncer.
     * 
     * @param delayMillis Délai en millisecondes avant d'exécuter l'action après le dernier appel
     */
    public Debouncer(long delayMillis) {
        this.timer = new Timer(true); // Daemon thread
        this.delayMillis = delayMillis;
    }
    
    /**
     * Planifie l'exécution d'une action après le délai spécifié.
     * Si une action est déjà planifiée, elle est annulée et remplacée par la nouvelle.
     * 
     * @param action L'action à exécuter
     */
    public synchronized void debounce(Consumer<Runnable> action) {
        if (pendingTask != null) {
            pendingTask.cancel();
        }
        
        pendingTask = new TimerTask() {
            @Override
            public void run() {
                action.accept(this);
            }
        };
        
        timer.schedule(pendingTask, delayMillis);
    }
    
    /**
     * Planifie l'exécution d'une action simple après le délai spécifié.
     * 
     * @param action L'action à exécuter (sans paramètre)
     */
    public synchronized void debounce(Runnable action) {
        debounce(task -> action.run());
    }
    
    /**
     * Annule toute action planifiée.
     */
    public synchronized void cancel() {
        if (pendingTask != null) {
            pendingTask.cancel();
            pendingTask = null;
        }
    }
    
    /**
     * Arrête le Debouncer et libère les ressources.
     */
    public void shutdown() {
        cancel();
        timer.cancel();
    }
}
