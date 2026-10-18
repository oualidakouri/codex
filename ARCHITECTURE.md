# Architecture Maison OS V1

Deux modules Kotlin, sans backend : `core` (domaine JVM testable) et `app` (Android/Compose). Application ID historique `fr.maisonos.diagnostic` conservé pour mettre à jour le diagnostic sans perte de configuration. Version 1.0.0 / code 10, min API 26, cible API 36.

## Domaine

- `PrayerEngine` encapsule Adhan Kotlin 0.0.5 sous MIT : événements UTC, fuseau IANA, DST, méthodes, madhab, corrections, périodes silencieuses et prochaines alarmes par prière. Aucune position personnelle par défaut.
- `HomeModels` porte profils, dispositions paysage/portrait, widgets, médias, règles et préférences. `LayoutEngine` conserve les widgets et évite les chevauchements après déplacement/redimensionnement.
- `DvripClient`, `OnvifClient`, `PtzPort` et `LocalEndpoint` assurent les protocoles locaux. Le catalogue de caméras est distinct du nombre de décodeurs vidéo autorisés.
- `LanDiscovery` répète les probes bornés DVRIP/ONVIF ; `LanUnicastProbe` est un repli explicite sur un segment /24, vérifiant le protocole sans authentification. Les résultats sont regroupés par hôte.
- `RuleEngine`, `DeviceEventSource`, `SimulationConnector` et les descripteurs de périphériques/widgets séparent événements et présentation. Les données de simulation restent identifiées.

## Android

`ProbeViewModel` conserve son nom pour réutiliser les composants de diagnostic. Son StateFlow expose configuration et observations ; une Mutex sérialise les écritures. Les fichiers sont écrits via AtomicFile et chiffrés AES-256-GCM avec une clé Android Keystore. Le schéma 1 du diagnostic est migré vers le schéma 2 ; la caméra et les secrets sont repris et une carte caméra est ajoutée. Les futures migrations doivent conserver cette propriété.

`MaisonApp`, `PrayerScreens`, `HomeCameras`, `EventsScreen` et `HomeSettingsScreen` composent l’interface. Les lecteurs Media3 sont attachés à leur visibilité et au premier plan. La résolution des images de fond/événement est sous-échantillonnée. Les cartes caméra privilégient les sous-flux ; le plein écran peut choisir un flux principal séparé. La présence CameraX compare des grilles Y à au plus huit échantillons/s et ferme systématiquement les images.

`AdhanScheduler` installe une prochaine alarme par prière activée, puis reprogramme à la réception. AlarmManager utilise le mode exact si autorisé, sinon un mode approximatif explicitement signalé. `AdhanReceiver` refuse les échéances anciennes/dupliquées, applique les plages silencieuses et démarre la lecture demandée. `RescheduleReceiver` ne fait que rétablir les alarmes après boot/heure/fuseau/date/mise à jour. Aucun démarrage d’activité ou service caméra au boot.

`AdhanService` est un service de premier plan `mediaPlayback`, avec notification d’arrêt, audio focus transitoire, volume applicatif, wake mode MediaPlayer, arrêt à la fin/erreur et limite de dix minutes. Il ne modifie pas le volume système ni les restrictions de confidentialité. `AdhanPlayback` expose l’état à l’écran de prière.

`LocalFiles` copie les médias dans le stockage privé, prépare les ZIP de configuration sans secrets et valide les restaurations avant installation des fichiers. Limites de taille, noms UUID canoniques, détection de doublons, refus de traversal et contrôle des médias déclarés. Les sauvegardes Android automatiques restent exclues.

## Ressources et sécurité

Un flux vidéo par défaut, deux configurables sur dashboard ; aucun décodage illimité. Veille, arrière-plan, vidéo masquée et protection thermique/batterie suspendent les lecteurs. Mesures bornées à 120 échantillons et journal borné à 200 entrées. Les métriques ne prouvent pas une stabilité longue durée.

Sockets caméra liées au Wi-Fi et adresses privées littérales. Pas de redirection HTTP, TLS standard conservé, réponses XML/JSON bornées, DTD et entités externes interdites. Aucun mot de passe par défaut essayé ; budget de login DVRIP partagé pendant la session. Les exceptions brutes et URL secrètes ne sont pas recopiées dans les journaux.

## Sources

Les invocations embarquées sont des versets complets issus de Tanzil, avec traduction/notes QuranEnc French Montada 1.0.0, copiés sans modification depuis une révision figée de risan/quran-json. Notices de source et licences dans l’APK et `third_party/`. Pas de corpus religieux généré. Aucun enregistrement audio tiers distribué.

Room/DataStore et Navigation Compose ne sont pas ajoutés mécaniquement : cette V1 reste une activité et une configuration chiffrée. Les modèles de domaine et versions de schéma permettent une migration lorsque le catalogue/historique grandira. Les ressources de traduction complètes, connecteurs externes, scènes avancées et Tesla restent des extensions distinctes ; voir les limites V1.
