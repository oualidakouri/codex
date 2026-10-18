# Validation et procédure sur tablette

## Règles de preuve

Un test JVM avec faux serveur valide le comportement logiciel sur le protocole exercé, pas un modèle de caméra. Un émulateur valide certains chemins Android, pas la tablette physique. « Observé par l’API » n’équivaut pas à « confirmé visuellement ». Aucune fonctionnalité nécessitant le matériel de l’utilisateur n’est déclarée réussie à sa place.

## Commandes reproductibles

```sh
# Cloud, depuis /workspace/codex
python tools/bootstrap-cloud.py
python tools/cloud-gradle.py :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon

# Poste de développement Android configuré, avec appareil/émulateur connecté
./gradlew :app:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Rapports : `core/build/test-results/test/`, `core/build/reports/tests/test/`, `app/build/reports/lint-results-debug.html` et, après exécution instrumentée, `app/build/outputs/androidTest-results/connected/`.

Tests JVM : frontières LAN, encodage des identifiants, configuration, cadrage DVRIP et réponses fragmentées, hash Sofia, construction STOP, parsing de découverte, protection XML, SOAP/WSSE, refus de redirection, PTZ ONVIF à durée bornée, détection de mouvement, journal borné et événement simulé explicite.

Tests instrumentés : navigation/simulation, chiffrement Keystore et relecture par une nouvelle instance de stockage, export sans secrets, parsing SOAP et rejet DTD sur le parseur Android. Ils ne peuvent être déclarés exécutés qu’après retour de l’instrumentation.

## Matrice à remplir sur la tablette réelle

| Essai | Procédure / résultat attendu | État avant matériel |
| --- | --- | --- |
| Caractéristiques | Lire Android/API, RAM totale, écran, codecs ; comparer aux valeurs commerciales | En attente de validation matérielle |
| Permissions | Refuser puis autoriser la caméra ; afficher une erreur compréhensible, sans planter | En attente de validation matérielle |
| Découverte | Même Wi-Fi que la caméra ; identifier une réponse DVRIP ou ONVIF, noter le firmware | En attente de validation matérielle |
| Ajout manuel | IP/ports réels ; ne pas dépendre de la découverte | En attente de validation matérielle |
| DVRIP | Un essai avec les identifiants fournis ; session acceptée et keepalive, ou erreur explicite | En attente de validation matérielle |
| ONVIF | Profils/URI obtenus ; noter si le PTZ continu est annoncé | En attente de validation matérielle |
| RTSP | Sous-flux H.264 en premier ; image visible, décodeur réellement choisi, pas d’URL secrète dans le rapport | En attente de validation matérielle |
| H.265 | Tester seulement si flux et décodeur compatibles ; surveiller chaleur et pertes d’images | En attente de validation matérielle |
| Latence | Filmer un chronomètre avec la caméra ; comparer au même instant sur l’écran, répéter plusieurs fois | En attente de validation matérielle |
| PTZ | Tester chaque direction séparément, petite impulsion ; observer le sens réel | En attente de validation matérielle |
| STOP | Relâcher, annuler le geste, quitter la page et l’activité ; observer l’arrêt mécanique | En attente de validation matérielle |
| Rupture réseau PTZ | Seulement dans une position sans risque et sous surveillance ; vérifier timeout caméra, aucune garantie réseau de STOP | En attente de validation matérielle |
| Présence | Autoriser caméra, rester immobile puis bouger ; observer compteurs et faux positifs | En attente de validation matérielle |
| Veille | Lancer le mode sombre ; réveil par toucher puis par mouvement lorsque l’analyse reste active | En attente de validation matérielle |
| Arrière-plan | Quitter l’application ; vérifier que vidéo et analyse sont arrêtées, puis réactiver explicitement | En attente de validation matérielle |
| Reconnexion | Perdre/rétablir le Wi-Fi pendant la vidéo ; reprises bornées, pas de crash. DVRIP se reconnecte manuellement | En attente de validation matérielle |
| Persistance | Enregistrer, fermer/forcer l’arrêt puis rouvrir ; vérifier paramètres et identifiants | En attente de validation matérielle |
| Export/restauration | Exporter, restaurer ; conserver paramètres mais ressaisir mot de passe/URL RTSP | En attente de validation matérielle |
| Audio | Importer un fichier personnel ; entendre la lecture et l’arrêter | En attente de validation matérielle |
| Simulation | Activer le mode simulation, déclencher courrier ; animation et journal étiquetés SIMULÉ | En attente de validation matérielle |
| Sans Internet | Couper le WAN au routeur sans désactiver le Wi-Fi ; refaire vidéo, PTZ, présence et simulation | En attente de validation matérielle |
| Longue durée | Tester 30 min, puis 4 h et 24 h ; relever PSS, pertes vidéo, erreurs, batterie, chaleur réelle | En attente de validation matérielle |
| Protection | Vérifier comportement en batterie faible et état thermique sévère si reproductible sans surchauffer volontairement l’appareil | En attente de validation matérielle |
| Redémarrage | Choisir volontairement le lanceur, redémarrer ; constater le comportement réel du constructeur | En attente de validation matérielle |

## Rapport à conserver

Ouvrir **Journal → Exporter le rapport de diagnostic** après les essais. Ajouter manuellement modèle/firmware caméra, protocole et résultat visuel pour chaque fonction. Ne jamais ajouter un mot de passe ou un lien contenant des secrets au rapport partagé.

La phase dashboard reste suspendue aux résultats critiques : vidéo stable, contrôle/STOP si caméra motorisée, présence/veille, persistance et fonctionnement local sans WAN. Les fonctions non supportées d’un périphérique sont identifiées séparément des erreurs de l’application.
