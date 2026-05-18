package utils;

import java.util.List;

/**
 * Interface définissant les opérations d'un connecteur vers une plateforme Git distante.
 * 
 * <p>Les implémentations de cette interface permettent d'interagir avec les APIs REST
 * de GitHub, GitLab, Gitea et autres plateformes compatibles pour lister et créer
 * des dépôts sans utiliser directement Git.</p>
 * 
 * <p><b>Authentification :</b> Les connecteurs utilisent un token d'accès personnel
 * fourni lors de la construction. Le token doit avoir les permissions nécessaires
 * pour lire et créer des dépôts.</p>
 * 
 * <p><b>Gestion des erreurs :</b> Toutes les méthodes peuvent lever une
 * {@link RemoteConnectorException} en cas d'échec (erreur réseau, authentification
 * invalide, limite de taux dépassée, etc.). Les erreurs sont loggées avant d'être
 * levées.</p>
 * 
 * @see RemoteConnectorFactory
 * @see RemoteConnectorException
 */
public interface RemoteConnector {

    /**
     * Retourne l'identifiant de la plateforme.
     * 
     * @return "github", "gitlab" ou "gitea"
     */
    String platform();

    /**
     * Liste les dépôts appartenant à l'utilisateur authentifié.
     * 
     * <p>Seuls les dépôts dont l'utilisateur est propriétaire sont retournés
     * (pas les forks, ni les dépôts d'organisations par défaut).</p>
     * 
     * @return La liste des dépôts (vide si aucun dépôt ou en cas d'erreur silencieuse)
     * @throws RemoteConnectorException En cas d'erreur API (auth invalide, réseau, etc.)
     */
    List<RemoteRepo> listRepositories() throws RemoteConnectorException;

    /**
     * Crée un nouveau dépôt distant.
     * 
     * @param name      Le nom du dépôt (sans espaces)
     * @param isPrivate {@code true} pour créer un dépôt privé, {@code false} pour public
     * @throws RemoteConnectorException En cas d'erreur API (nom déjà utilisé, quota dépassé, etc.)
     */
    void createRepository(String name, boolean isPrivate) throws RemoteConnectorException;

    /**
     * Record représentant un dépôt distant.
     * 
     * @param name          Le nom du dépôt
     * @param cloneUrl      L'URL de clone HTTPS (ex: https://github.com/user/repo.git)
     * @param description   La description du dépôt (peut être vide)
     * @param isPrivate     {@code true} si le dépôt est privé
     * @param defaultBranch Le nom de la branche par défaut (ex: "main", "master")
     */
    record RemoteRepo(
            String name,
            String cloneUrl,
            String description,
            boolean isPrivate,
            String defaultBranch) {
        
        /**
         * Construit un RemoteRepo avec tous les champs.
         */
        public RemoteRepo {
            // Validation basique
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Le nom du dépôt ne peut pas être vide");
            }
            if (cloneUrl == null || cloneUrl.isBlank()) {
                throw new IllegalArgumentException("L'URL de clone ne peut pas être vide");
            }
            // Normaliser les valeurs nulles
            description = description != null ? description : "";
            defaultBranch = defaultBranch != null && !defaultBranch.isBlank() ? defaultBranch : "main";
        }
    }
}
