package utils;

import org.eclipse.jgit.transport.URIish;

import java.net.URISyntaxException;

/**
 * Factory statique pour créer des instances de {@link RemoteConnector} à partir d'une URL Git.
 * 
 * <p>Cette factory détecte automatiquement la plateforme (GitHub, GitLab, Gitea) en analysant
 * l'URL du dépôt distant et instancie le connecteur approprié.</p>
 * 
 * <h2>Formats d'URL supportés :</h2>
 * <ul>
 *   <li>HTTPS: {@code https://github.com/user/repo.git}</li>
 *   <li>SSH: {@code git@github.com:user/repo.git}</li>
 *   <li>SSH avec protocole: {@code ssh://git@github.com/user/repo.git}</li>
 * </ul>
 * 
 * <h2>Détection de plateforme :</h2>
 * <ul>
 *   <li><b>GitHub</b>: détecté si l'hôte contient "github.com"</li>
 *   <li><b>GitLab</b>: détecté si l'hôte contient "gitlab.com"</li>
 *   <li><b>Gitea</b>: assumé pour toute autre URL (instance custom)</li>
 * </ul>
 * 
 * @see RemoteConnector
 */
public final class RemoteConnectorFactory {

    private static final LogService log = LogService.getInstance();
    private static final String LOG_SOURCE = "RemoteConnectorFactory";

    // Empêcher l'instanciation
    private RemoteConnectorFactory() {
    }

    /**
     * Crée un connecteur approprié pour l'URL fournie.
     * 
     * <p>L'URL est analysée pour déterminer la plateforme, puis le connecteur
     * correspondant est instancié avec le token fourni.</p>
     * 
     * @param remoteUrl L'URL du dépôt distant (HTTPS ou SSH)
     * @param token     Le token d'accès personnel pour l'authentification
     * @return Une instance de RemoteConnector, ou {@code null} si la plateforme n'est pas supportée
     */
    public static RemoteConnector create(String remoteUrl, String token) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            log.warn(LOG_SOURCE, "URL de dépôt vide");
            return null;
        }

        if (token == null || token.isBlank()) {
            log.warn(LOG_SOURCE, "Token d'authentification vide");
            return null;
        }

        try {
            // Parser l'URL avec JGit URIish pour supporter tous les formats Git
            URIish uri = new URIish(remoteUrl);
            String host = uri.getHost();

            if (host == null || host.isBlank()) {
                log.warn(LOG_SOURCE, "Impossible d'extraire l'hôte de l'URL: " + remoteUrl);
                return null;
            }

            host = host.toLowerCase();

            // Détection de la plateforme par le nom d'hôte
            if (host.contains("github.com")) {
                log.debug(LOG_SOURCE, "Détection de GitHub pour: " + host);
                return new GitHubConnector(token);
            } else if (host.contains("gitlab.com")) {
                log.debug(LOG_SOURCE, "Détection de GitLab public pour: " + host);
                return new GitLabConnector("https://gitlab.com", token);
            } else if (host.contains("gitlab")) {
                // Instance GitLab self-hosted (contient "gitlab" dans le domaine)
                String instanceUrl = uri.getScheme() + "://" + host;
                log.debug(LOG_SOURCE, "Détection de GitLab self-hosted: " + instanceUrl);
                return new GitLabConnector(instanceUrl, token);
            } else {
                // Par défaut, on assume Gitea pour les instances custom
                String instanceUrl = uri.getScheme() + "://" + host;
                if (uri.getPort() > 0) {
                    instanceUrl += ":" + uri.getPort();
                }
                log.debug(LOG_SOURCE, "Détection de Gitea (ou instance custom): " + instanceUrl);
                return new GiteaConnector(instanceUrl, token);
            }

        } catch (URISyntaxException e) {
            log.error(LOG_SOURCE, "URL invalide: " + remoteUrl + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Détecte le type de plateforme à partir d'une URL sans créer de connecteur.
     * 
     * @param remoteUrl L'URL du dépôt distant
     * @return "github", "gitlab", "gitea" ou {@code null} si non détecté
     */
    public static String detectPlatform(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return null;
        }

        try {
            URIish uri = new URIish(remoteUrl);
            String host = uri.getHost();

            if (host == null || host.isBlank()) {
                return null;
            }

            host = host.toLowerCase();

            if (host.contains("github.com")) {
                return "github";
            } else if (host.contains("gitlab")) {
                return "gitlab";
            } else {
                return "gitea";
            }

        } catch (URISyntaxException e) {
            return null;
        }
    }
}
