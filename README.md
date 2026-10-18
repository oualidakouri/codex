# Maison OS · V1 Android

Tableau de bord mural pour tablette Android : salat et adhan hors ligne, douas sourcées, caméras locales, veille et événements. Application native Kotlin / Jetpack Compose, sans serveur ou Home Assistant obligatoire.

## Installer l’APK

**[Télécharger Maison OS 1.0.0 (.apk)](downloads/maison-os-1.0.0.apk?raw=true)**

Si le lien ne télécharge pas, ouvrir [la page du fichier](downloads/maison-os-1.0.0.apk), puis cliquer sur **Download raw file** en haut à droite dans GitHub. Ouvrir GitHub dans le navigateur ; se connecter si le dépôt est privé.

La V1 se met à jour **par-dessus l’APK de diagnostic**, sans désinstallation : même identifiant `fr.maisonos.diagnostic` et même clé de développement dans cet environnement. La configuration existante est migrée, y compris la caméra sélectionnée et ses identifiants. Les APK générées ailleurs, notamment en CI, ont leur propre clé de développement ; utiliser l’APK fournie dans `downloads` pour cette mise à jour.

## Ce que contient cette V1

- Dashboard avec vrai déplacement par appui long, redimensionnement par poignée, ajout/suppression, transparence, pages et profils. Dispositions paysage et portrait indépendantes.
- Horloge, dates grégorienne/hégirienne, prochaine salat, compte à rebours, horaires, doua, caméras, état de la tablette et raccourcis.
- Thèmes sombre/clair, couleurs et fond d’écran personnel.
- Calcul local des cinq prières avec Adhan Kotlin : choix volontaire de ville ou coordonnées, fuseau IANA, méthode, madhab, hautes latitudes, ajustements, calendrier mensuel et correction hégirienne.
- Adhan configurable par prière, audio personnel copié sur la tablette, récitation Fajr distincte, volume, silence horaire, écoute immédiate, test d’alarme dans 10 secondes et bouton d’arrêt. Reprogrammation après démarrage, changement d’heure/fuseau/date et mise à jour.
- Sept invocations coraniques complètes, arabe vocalisé et traduction française reproduits sans modification, références, notes, favoris, historique et rotation quotidienne.
- Catalogue de plusieurs caméras, migration de la caméra existante, découverte DVRIP/ONVIF répétée et recherche directe facultative DVRIP/RTSP sur le segment Wi-Fi /24. Les résultats sont regroupés par IP.
- RTSP en widget et plein écran, sous-flux/principal configurables, PTZ selon les capacités testées. Un ou deux flux simultanés dans le dashboard, selon le réglage ; cette limite ne concerne pas le scan.
- Veille sombre avec prochaine prière, réveil au toucher ou par mouvement frontal, analyse locale au premier plan, protection thermique/batterie et rôle de lanceur Android facultatif.
- Règles locales pour courrier simulé, mouvement frontal et test manuel : délai, durée, repos entre événements, message, son/vidéo/image personnels et retour automatique.
- Paramètres chiffrés Android Keystore, import/export ZIP avec médias, diagnostics et rapports.

Les widgets et règles de simulation portent cette mention. Aucun capteur externe, compte cloud ou Tesla n’est simulé comme une connexion réelle.

## Premier lancement

1. Installer la V1 par-dessus le diagnostic, puis ouvrir **Maison OS**.
2. Dans **Salat → Ville et méthode de calcul**, choisir la position et vérifier les horaires par rapport à la mosquée. Aucune position personnelle n’est prédéfinie.
3. Importer un audio personnel, choisir la récitation puis activer les prières voulues. Autoriser les alarmes exactes et les notifications depuis cet écran. Sans autorisation exacte, les horaires d’alarme sont approximatifs et Android peut refuser la lecture en arrière-plan.
4. Dans **Caméras**, retrouver la caméra migrée et ajouter la seconde. Une caméra absente du broadcast peut être ajoutée manuellement ou trouvée par recherche approfondie. Deux protocoles sur la même IP ne représentent pas deux appareils.
5. Dans **Accueil → Personnaliser**, ajouter un widget caméra puis choisir son équipement dans `⋯`. Utiliser un sous-flux basse résolution. Le nombre de flux est réglable dans **Paramètres**.
6. Configurer les médias et la veille dans **Paramètres**, puis tester les règles dans **Événements**.

Voir [le guide V1](docs/V1-GUIDE.md), [les résultats et critères de validation](docs/VALIDATION-V1.md), [l’architecture](ARCHITECTURE.md) et [les limites](docs/LIMITATIONS-V1.md).

## Compiler et tester

Dans le cloud, depuis `/workspace/codex` :

```sh
python tools/bootstrap-cloud.py
python tools/cloud-gradle.py :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon
```

Sur un poste Android configuré (JDK 21, SDK 36) :

```sh
./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Les tests instrumentés doivent être exécutés sur un appareil de développement : ils sauvegardent/restaurent sa configuration pendant les essais. Leur compilation ne vaut pas exécution. Sortie APK : `app/build/outputs/apk/debug/app-debug.apk`.

Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21. Android 8/API 26 minimum, Android 16/API 36 ciblé. Signature de développement locale conservée sous `.gradle/` et exclue de Git. Les dépendances nécessitent Internet à la compilation ; l’application n’en a pas besoin pour ses fonctions locales.

## Sources et licences

- [Adhan Kotlin](https://github.com/batoulapps/adhan-kotlin), version 0.0.5, MIT : compatible avec le compilateur retenu. Les versions récentes utilisant Kotlin 2.4 ne sont pas introduites dans cette V1.
- [iCSee Android](https://github.com/voidnullvalue/Icsee-android), composants DVRIP/PTZ sous MIT ; attributions conservées.
- Arabe : [Tanzil](https://tanzil.net), CC BY 3.0, reproduction verbatim. Traduction : [QuranEnc / Noor International Center, French Montada 1.0.0](https://quranenc.com/en/browse/french_montada/). Sélection depuis risan/quran-json, avec empreintes, notices et licences conservées.

Les sources, versions et mentions sont accessibles dans **Paramètres → Sources et licences**, dans les assets et dans `third_party/`. Aucun fichier audio religieux tiers n’est distribué sans autorisation.
