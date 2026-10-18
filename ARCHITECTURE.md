# Architecture Maison OS 1.2

Deux modules Kotlin, sans backend : `core` (domaine JVM testable) et `app` (Android/Compose). Application ID historique `fr.maisonos.diagnostic` conservé pour mettre à jour le diagnostic sans perte de configuration. Version 1.2.0 / code 12, min API 26, cible API 36.

## Domaine

- `PrayerEngine` encapsule Adhan Kotlin 0.0.5 sous MIT : événements UTC, fuseau IANA, DST, méthodes, madhab, corrections, périodes silencieuses et prochaines alarmes par prière. Aucune position personnelle par défaut.
- `HomeModels` porte profils, dispositions paysage/portrait, widgets, médias, règles et préférences. `LayoutEngine` conserve les widgets et évite les chevauchements après déplacement/redimensionnement.
- `DvripClient`, `OnvifClient`, `PtzPort` et `LocalEndpoint` assurent les protocoles locaux. Le catalogue de caméras est distinct du nombre de décodeurs vidéo autorisés.
- `LanDiscovery` répète les probes bornés DVRIP/ONVIF ; `LanUnicastProbe` est un repli explicite sur un segment /24, vérifiant le protocole sans authentification. Les résultats sont regroupés par hôte.
- `RuleEngine`, `DeviceEventSource`, `SimulationConnector` et les descripteurs de périphériques/widgets séparent événements et présentation. Les données de simulation restent identifiées.

## Android

`ProbeViewModel` conserve son nom pour réutiliser les composants de diagnostic. Son StateFlow expose configuration et observations ; une Mutex sérialise les écritures. Les fichiers sont écrits via AtomicFile et chiffrés AES-256-GCM avec une clé Android Keystore. Les schémas 1 du diagnostic et 2 de la V1 sont migrés vers le schéma 3 ; les caméras et leurs secrets sont repris. Les deux premières caméras et une carte météo sont placées sur la première page sans supprimer les autres types de widgets. Les futures migrations doivent conserver cette propriété.

`MaisonApp`, `PrayerScreens`, `HomeCameras`, `EventsScreen` et `HomeSettingsScreen` composent l’interface. Les lecteurs Media3 sont attachés à leur visibilité et au premier plan. La résolution des images de fond/événement est sous-échantillonnée. Les cartes caméra privilégient les sous-flux ; le plein écran peut choisir un flux principal séparé. La présence CameraX compare des grilles Y à au plus huit échantillons/s et ferme systématiquement les images.

`AdhanScheduler` installe une prochaine alarme par prière activée, puis reprogramme à la réception. AlarmManager utilise le mode exact si autorisé, sinon un mode approximatif explicitement signalé. `AdhanReceiver` refuse les échéances anciennes/dupliquées, applique les plages silencieuses et démarre la lecture demandée. `RescheduleReceiver` ne fait que rétablir les alarmes après boot/heure/fuseau/date/mise à jour. Aucun démarrage d’activité ou service caméra au boot.

`AdhanService` est un service de premier plan `mediaPlayback`, avec notification d’arrêt, audio focus transitoire, volume applicatif, wake mode MediaPlayer, arrêt à la fin/erreur et limite de dix minutes. Il ne modifie pas le volume système ni les restrictions de confidentialité. `AdhanPlayback` expose l’état à l’écran de prière.

`LocalFiles` copie les médias dans le stockage privé, prépare les ZIP de configuration sans secrets et valide les restaurations avant installation des fichiers. Limites de taille, noms UUID canoniques, détection de doublons, refus de traversal et contrôle des médias déclarés. Les sauvegardes Android automatiques restent exclues.

## Ressources et sécurité

Deux flux vidéo par défaut, un ou deux configurables sur dashboard ; aucun décodage illimité. Veille, arrière-plan, vidéo masquée et protection thermique/batterie suspendent les lecteurs. Mesures bornées à 120 échantillons et journal borné à 200 entrées. Les métriques ne prouvent pas une stabilité longue durée.

Sockets caméra liées au Wi-Fi et adresses privées littérales. Pas de redirection HTTP, TLS standard conservé, réponses XML/JSON bornées, DTD et entités externes interdites. Aucun mot de passe par défaut essayé ; budget de login DVRIP partagé pendant la session. Les exceptions brutes et URL secrètes ne sont pas recopiées dans les journaux.

## Sources

Les invocations embarquées sont des versets complets issus de Tanzil, avec traduction/notes QuranEnc French Montada 1.0.0, copiés sans modification depuis une révision figée de risan/quran-json. Notices de source et licences dans l’APK et `third_party/`. Pas de corpus religieux généré. Aucun enregistrement audio tiers distribué.

Room/DataStore et Navigation Compose ne sont pas ajoutés mécaniquement : cette V1 reste une activité et une configuration chiffrée. Les modèles de domaine et versions de schéma permettent une migration lorsque le catalogue/historique grandira. Les ressources de traduction complètes, connecteurs externes, scènes avancées et Tesla restent des extensions distinctes ; voir les limites V1.

## Extensions 1.1

`ThemeCatalog` définit six palettes et une palette classique ; le Surface racine propage explicitement onBackground. Les tests vérifient contraste des textes et boutons, plus la couleur réellement appliquée à la navigation et au titre Android.

`WeatherController` appelle Open-Meteo en HTTPS, toutes les 30 minutes au premier plan, après configuration. Cache privé par coordonnées, date d’observation visible, aucune température de batterie utilisée comme météo. La géolocalisation approximative est ponctuelle, déclenchée par un bouton ; la recherche de ville et la ville de salat évitent cette permission.

`AppUpdates` lit un manifeste public HTTPS borné à 64 Kio, vérifie la version, télécharge dans le cache privé (100 Mio maximum), vérifie taille/SHA-256, package, version et certificat identique à l’application. `FileProvider` partage uniquement le dossier updates et l’installateur Android demande confirmation. Pas de service ni d’installation silencieuse. Les fixtures de tests APK sont générées avec la clé locale du build et une clé temporaire différente ; aucune clé n’entre dans les assets.

`SmartLifeClient : SwitchPort` encapsule les trames Tuya 3.3 (AES natif du protocole, CRC et tailles bornées). Lecture de l’état booléen du point configuré ; un ACK n’est pas assimilé à l’état demandé. Commandes explicites, sans scan de points de données, extraction de clé ou login cloud. Simulation isolée. Les clés locales sont chiffrées dans la configuration et exclues des exports.

Le lecteur RTSP conserve le TCP, suspend les reprises sur refus d’identifiants et affiche des indications sans recopier les exceptions/URL secrètes. Le format iCSee/Xiongmai construit les identifiants dans l’URL de lecture en mémoire uniquement. Les URI ONVIF standard sont nettoyées de leur userinfo ; le profil de moindre résolution annoncée est préféré.

## Extensions 1.2

Schéma 4 : les versions 1 à 3 sont migrées, les widgets SWITCHES sont retirés, QURAN/NEWS sont ajoutés et le widget météo est agrandi pour les prévisions. Les caméras, médias, profils et autres identifiants de widget sont conservés. Les anciens modèles Tuya restent décodables, sans écran ni commande exposés. Les futures connectiques seront traitées séparément via Home Assistant quand le serveur de l’utilisateur sera prêt.

`WeatherForecast` lit quatre jours Open-Meteo en date locale de la ville et expose les trois dates futures, y compris lors du changement d’heure. Une prévision incomplète n’est pas remplacée par une estimation. `WeatherDay.clothing` donne une indication déterministe à partir du ressenti/minimum, de la pluie et du vent.

`QuranCatalogController` initialise un catalogue de sept récitations complètes et 114 noms de sourates depuis les métadonnées versionnées de risan/quran-json (MP3Quran/Tanzil, sources et empreintes dans third_party). L’API MP3Quran actualise le catalogue ; les audios restent en réseau. `QuranService` est un foreground service mediaPlayback Media3 : HTTP(S), MP3/AAC/HLS, piste vidéo désactivée, audio focus, volume, notification d’arrêt, START_NOT_STICKY. Aucune reprise au boot. L’adhan arrête explicitement la récitation. Les URL personnelles sont chiffrées et exclues des exports.

`NewsParser` lit RSS 2/Atom avec le parseur XML sécurisé existant, garde des titres/source/date/lien, refuse les URL non HTTPS des articles, et borne la taille et le nombre d’éléments. `NewsController` actualise au plus toutes les quinze minutes au premier plan, conserve un cache privé par URL et isole les échecs France/local. La recherche locale est une recherche Google Actualités sur un nom de ville, pas une géolocalisation de chaque article. Les URL RSS personnelles sont exclues des exports.
