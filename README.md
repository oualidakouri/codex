# Maison OS · Diagnostic Android

Première preuve de faisabilité de Maison OS : une application Android native installable, exécutant ses tests directement sur la tablette. Aucun serveur, Home Assistant, Docker ou compte cloud n’est nécessaire à l’exécution.

## Télécharger l’APK déjà compilée

**[Télécharger Maison OS Diagnostic 0.1.0 (.apk)](downloads/maison-os-diagnostic-0.1.0.apk?raw=true)**

Si le téléchargement ne démarre pas, ouvrir [la page du fichier APK](downloads/maison-os-diagnostic-0.1.0.apk), puis utiliser **Download raw file** (icône de téléchargement en haut à droite du fichier) dans GitHub, de préférence dans le navigateur. Pour un dépôt privé, se connecter au compte GitHub ayant accès au dépôt.

L’APK de développement de 17 Mo est fournie dans `downloads/` pour ne pas dépendre des liens locaux de Codex. Les [rapports de vérification](downloads/verification/) et l’empreinte SHA-256 sont inclus. Voir [les instructions de téléchargement](downloads/README.md).

**La phase dashboard n’est pas commencée.** Cette version sert à vérifier la tablette et les protocoles des caméras avant de poursuivre. Une adresse IP ou une réponse positive à une commande ne prouve jamais, à elle seule, une compatibilité complète.

## Application livrée

- Interface française sombre, paysage/portrait, navigation plein écran.
- Version Android/API, correctif, écran, RAM exposée par Android, décodeurs et accélération matérielle déclarée.
- PSS, tas Java, batterie, température batterie et état thermique Android ; historique borné et rapport exportable.
- Découverte locale DVRIP UDP et ONVIF WS-Discovery, sans essai de mot de passe.
- Configuration manuelle d’une caméra, authentification DVRIP-Web et interrogation ONVIF (profils/URI RTSP/capacité PTZ).
- Un lecteur RTSP Media3 avec transport TCP, son coupé, journal du décodeur réellement choisi et reprise bornée après erreur.
- PTZ en impulsions courtes : ONVIF si déclaré par la caméra, DVRIP après autorisation explicite. STOP au relâchement/annulation et après chaque impulsion. Le comportement mécanique reste à vérifier.
- Analyse locale des variations de luminance de la caméra frontale, sans enregistrement/transmission d’images.
- Écran sombre logiciel, réveil au toucher et par mouvement lorsque l’application reste au premier plan.
- Import d’un fichier audio personnel pour tester la lecture locale. Aucun enregistrement religieux distribué sans autorisation.
- Simulation explicite d’un événement courrier et de son animation.
- Configuration chiffrée AES-GCM/Android Keystore, export/restauration sans mot de passe ni URL RTSP.
- Candidat au rôle de lanceur Android, à choisir volontairement dans les réglages système.

Voir [les limites Android et protocolaires](docs/LIMITATIONS.md), [l’architecture](ARCHITECTURE.md), [les tests](docs/VALIDATION.md) et [la feuille de route](docs/ROADMAP.md).

Le [rapport de livraison vérifiée](docs/BUILD-REPORT.md) distingue les 23 tests JVM et les 3 tests Android réussis sur émulateur des essais encore attendus sur le matériel réel.

## Compiler

Outils : JDK 21, Android SDK 36, Gradle 8.13 (wrapper et empreinte SHA-256 fournis). Le SDK et les dépendances nécessitent Internet **à la compilation**, pas un service en ligne à l’exécution.

Depuis `/workspace/codex` dans cet environnement cloud :

```sh
python tools/bootstrap-cloud.py
python tools/cloud-gradle.py :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon
```

Le bootstrap installe les outils sous `/workspace`, conserve TLS et les vérifications d’intégrité, accepte les licences du SDK pour les composants demandés, et ne modifie aucun fichier source ou lockfile. Le wrapper cloud initialise le proxy Java à partir de la configuration existante, sans enregistrer ses valeurs dans le dépôt.

Sur un poste Android Studio avec JDK 21 et SDK 36 configurés :

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

APK : `app/build/outputs/apk/debug/app-debug.apk`. Identifiant : `fr.maisonos.diagnostic`. Android 8/API 26 minimum, Android 16/API 36 ciblé. Signature de développement générée localement dans `.gradle/`, exclue de Git ; conserver cette clé pour installer de futures mises à jour de cette APK. Aucune signature de production ou publication Play Store dans cette phase.

## Installer et effectuer le premier essai

1. Copier l’APK sur la tablette ; autoriser l’installation depuis la source choisie puis ouvrir Maison OS. Ou utiliser `adb install -r app/build/outputs/apk/debug/app-debug.apk` depuis un ordinateur de développement temporaire.
2. Ouvrir **Diagnostics** et exporter le rapport initial. Vérifier la RAM physique exposée, l’API réelle et les décodeurs ; les caractéristiques commerciales ne sont pas présumées.
3. Connecter la tablette au même Wi-Fi que les caméras. Ouvrir **Caméra LAN → Rechercher mes caméras**. L’ajout manuel reste disponible si le broadcast/multicast est bloqué.
4. Renseigner l’IPv4 privée, les ports, l’identifiant et le mot de passe réels. Enregistrer avant de tester. Les ports 34567/8899 sont des valeurs usuelles modifiables, pas une preuve de compatibilité.
5. Tester DVRIP puis ONVIF séparément. ONVIF peut renseigner une URI RTSP ; sinon saisir l’URL exacte fournie par le fabricant. Les identifiants se saisissent dans les champs dédiés. Certains firmwares ont des comptes RTSP distincts : modifier les identifiants et enregistrer avant le test correspondant.
6. Afficher le flux RTSP, commencer par le sous-flux basse définition. Vérifier les images et le décodeur choisi. Aucun chemin RTSP ou protocole propriétaire n’est deviné.
7. Tester le PTZ avec prudence, vérifier visuellement l’arrêt et exporter le résultat. Voir les réserves sur STOP dans les limites.
8. Tester présence, veille, perte/reprise Wi-Fi et coupure WAN selon [la procédure de validation](docs/VALIDATION.md).

Les catégories « non vérifié », « observé par l’API », « échec », « simulé » et « non implémenté » restent distinctes. Un test sur serveur simulé ou émulateur ne valide pas les caméras réelles.

## Périmètre reporté

Calcul salat, programmation d’adhan, base de douas vérifiées, dashboard à widgets, QR iCSee, vidéo DVRIP, audio bidirectionnel, presets PTZ, stockage vidéo, connecteurs capteurs et automatisations configurables ne sont pas implémentés ici. L’écran de veille indique explicitement que la prochaine salat n’est pas configurée. Ces fonctions suivent la validation de cette preuve, dans l’ordre demandé.

## Référence iCSee et licences

Étude de [voidnullvalue/Icsee-android](https://github.com/voidnullvalue/Icsee-android), commit `74a2ed41923f1c2b3e880222aa3d76af10389c82` : README, PROTOCOL_NOTES, PROTOCOL_STATUS, SECURITY, découverte, session, PTZ et vidéo. Six fichiers de primitives sont adaptés sous MIT, avec [mentions conservées](third_party/ICSEE-LICENSE.txt) et copie de la licence dans les assets de l’APK. Voir [la provenance détaillée](docs/ICSEE-REFERENCE.md).

L’application est un projet privé ; aucune licence générale du projet n’est choisie à la place du propriétaire. Les licences des dépendances et composants tiers continuent de s’appliquer.
