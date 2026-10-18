# Architecture de la preuve de faisabilité

## Décisions

Kotlin et Jetpack Compose/Material 3 pour une APK native. `minSdk=26`, `compileSdk=targetSdk=36`. JDK 21 et Gradle 8.13/AGP 8.13.2 sont épinglés. Une seule activité et deux modules évitent de fragmenter prématurément ce petit banc de diagnostic.

```text
app (Android)
  MainActivity / Compose       écrans, permissions à la demande, documents SAF
  ProbeViewModel               StateFlow, observations, opérations et cycle de vie
  ConfigStore                  fichier atomique chiffré, clé Android Keystore
  DeviceDiagnostics            Wi-Fi, mémoire, codecs, batterie, thermique
  CameraTests                  Media3 RTSP, CameraX / analyse Y échantillonnée
       ↓
core (JVM indépendant d’Android)
  LocalEndpoint / CameraConfig validation des destinations et configuration
  LanDiscovery                 fenêtre UDP DVRIP / ONVIF de 5 secondes
  DvripClient                  session unique, échanges sérialisés, keepalive
  OnvifClient / SafeXml         SOAP/WSSE, HTTP Digest, profils, PTZ
  PtzPort                      abstraction partagée de contrôle PTZ
  DeviceDescriptor / WidgetDescriptor  contrats pour les prochaines phases
  DeviceEventSource            flux d’événements explicites
  SimulationConnector          événements toujours étiquetés simulated=true
  MotionDetector / BoundedLog   primitives testables et mémoire bornée
  vendor                       primitives MIT iCSee attribuées
```

## Exécution et ressources

Le ViewModel conserve l’état d’interface sans stocker les mots de passe dans un état Compose sauvegardé. Les opérations réseau bloquantes sont exécutées sur `Dispatchers.IO`. Chaque nouvelle connexion DVRIP est explicite, espacée d’au moins cinq secondes et limitée à trois essais par minute par destination pendant la session de l’application, même après remplacement du client ; aucun essai automatique d’identifiants. Un keepalive toutes les dix secondes entretient la session ouverte. La connexion perdue doit être rouverte manuellement pour éviter une boucle de tentatives d’authentification.

Un seul lecteur Media3 à la fois, RTSP/RTP sur TCP, son coupé et reprise bornée à cinq tentatives (2, 4, 8, 16, 30 secondes). Sortir de la page vidéo, mettre en veille ou quitter le premier plan libère le lecteur. Une nouvelle connexion RTSP est une vraie tentative d’authentification ; le nombre de reprises est borné. La caméra frontale analyse au plus huit grilles de luminance par seconde, avec résolution 320 × 240 demandée (la caméra peut négocier une autre taille). Les images sont fermées dans un `finally`.

Les métriques sont échantillonnées toutes les cinq secondes, avec au plus 120 mesures et 200 entrées de journal. Batterie non branchée à 10 % ou moins, ou état thermique Android sévère : arrêt des tests vidéo et caméra. Le mode veille est une couche noire à faible luminosité ; la caméra reste possible uniquement tant que l’activité reste au premier plan et que le système l’autorise.

## Sécurité et réseau

Les connexions caméra sont liées au réseau Wi-Fi Android sélectionné, sans exiger sa validation Internet. La version accepte seulement les IPv4 privées littérales. Les services annoncés par ONVIF doivent désigner exactement l’hôte sélectionné. Les redirections HTTP sont désactivées et les réponses XML/JSON sont bornées ; les entités XML externes et DTD sont refusées. TLS garde la vérification standard. Le multicast ONVIF et les broadcasts sont les seules destinations de découverte particulières.

RTSP et DVRIP sont souvent non chiffrés. Android autorise donc le trafic clair pour les périphériques locaux ; le filtrage applicatif contrôle les destinations. Les erreurs de bibliothèques ne sont pas recopiées dans l’interface : seuls les types/codes et messages contrôlés y entrent, pour éviter de journaliser des URL ou secrets.

Le fichier de configuration complet est chiffré AES-256-GCM avec une clé Android Keystore et écrit atomiquement. Les sauvegardes/transferts Android sont exclus. L’export manuel retire le mot de passe, l’URL RTSP entière et l’autorisation PTZ DVRIP ; il faut les renseigner après restauration. Aucun secret n’est intégré au build, aucun compte constructeur ni télémétrie ajouté.

## Évolution après validation matérielle

La preuve utilise un fichier chiffré pour un seul équipement ; Room et DataStore ne sont pas nécessaires pour ce petit périmètre. Le futur catalogue d’équipements, les dispositions de widgets, l’historique et les règles seront persistés dans Room ; les préférences non sensibles pourront migrer vers DataStore, avec migration explicite de version. L’injection manuelle suffit ici ; Hilt n’est pas nécessaire avant cette croissance.

Les widgets futurs consommeront des modèles et des événements du domaine, et pas un protocole caméra concret. La navigation multi-écrans pourra adopter Navigation Compose et des modules fonctionnels quand le dashboard commence. Les textes de cette preuve sont français ; l’extraction complète en ressources et la localisation font partie du jalon dashboard avant l’ajout d’une autre langue.
