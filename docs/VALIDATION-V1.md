# Validation Maison OS 1.0

## Ce que prouve chaque niveau

Le retour utilisateur valide le diagnostic initial dans son contexte et la vidéo/PTZ d’au moins une caméra. La deuxième caméra absente de la découverte reste à vérifier sur son réseau. Les nouvelles sondes ne sont pas certifiées sur ce matériel depuis le cloud.

Les tests JVM utilisent des références de calcul et des fixtures réseau locales. Les tests instrumentés tournent sur l’émulateur Android 11/API 30 sans KVM. L’AVD 1280 × 800 a utilisé une taille de rendu finale de 640 × 400 à densité 80 (1280 dp logiques) pour alléger le rendu logiciel. Ils ne remplacent pas l’essai de l’APK V1 sur la tablette Android 16. Les résultats précis et empreintes de livraison sont consignés ci-dessous et dans `downloads/verification-v1/` après exécution.

## Vérifications automatisées

```sh
python tools/cloud-gradle.py :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r fr.maisonos.diagnostic.test/androidx.test.runner.AndroidJUnitRunner
```

Couverture JVM : protocoles et frontières LAN, authentification et STOP en fixture, SOAP/WSSE/Digest, refus de redirection/XXE, login limité, luminance, événements simulés ; plus calculs des cinq prières, référence de La Mecque publiée avec Adhan, DST, passage au lendemain, madhab, ajustements, silence nocturne, alarmes futures activées ; déplacement/redimensionnement sans chevauchement et cooldown des règles.

Couverture Android : navigation/simulation, chiffrement et absence de secrets dans les exports de toutes les caméras, parseur XML Android, migration réelle d’une configuration chiffrée du diagnostic, configuration de ville via l’interface, vrai glisser-déposer avec relecture du stockage, corpus religieux local, sauvegarde ZIP/médias et refus de traversal, vrai déclenchement AlarmManager → receiver → MediaPlayer (son de test généré dans le test, non fourni comme adhan).

Le test d’alarme peut être ignoré explicitement si l’appareil de développement n’autorise pas les alarmes exactes ; il faut alors le signaler comme non exécuté, pas comme réussi.

## Résultats de livraison — 18 octobre 2026

- Compilation APK et APK de tests : réussie. 37 tests JVM réussis, aucun ignoré. Lint : 0 erreur, 18 avertissements.
- Android : huit scénarios réussis sur un candidat intermédiaire ; un test de recréation d’activité réussi séparément. Le parcours de configuration de ville a d’abord rencontré un timeout de focus Android ; l’écran a été corrigé et son test a réussi sur l’APK finale. Les dix méthodes ont un passage réussi, réparti entre ces campagnes ; la suite complète n’a pas été réexécutée sur l’APK finale.
- Signature vérifiée et identique au diagnostic. APK finale : 17,577,670 octets ; SHA-256 `8edbdd04edab08962048760139eeaa5b63313f4cafe04445d2ff8619667e6f22`.
- Journaux et distinction des campagnes : [verification-v1](../downloads/verification-v1/README.md).
- Non vérifiés depuis le cloud : matériel Android 16, seconde caméra, codecs et PTZ réels, présence physique, audio audible et endurance. Les événements de courrier restent simulés.

## Essais à faire sur la tablette

1. Installer par-dessus le diagnostic : retrouver la caméra et ses identifiants, puis tester la configuration après fermeture/redémarrage.
2. Choisir la ville/méthode et comparer les horaires à la mosquée ; tester le calendrier, les corrections, l’adhan immédiat puis l’alarme avec écran sombre et permissions accordées.
3. Ajouter la seconde caméra manuellement ou par scan approfondi ; vérifier ses protocoles séparément. Confirmer deux équipements distincts avant d’afficher deux widgets vidéo.
4. Vérifier RTSP, codec choisi, PTZ/STOP mécanique et reconnexion sur chaque caméra ; utiliser les sous-flux avant d’activer deux lecteurs.
5. Déplacer/redimensionner les widgets, changer de page/profil et d’orientation, fermer et retrouver les dispositions.
6. Importer une récitation, une vidéo et une image personnelles ; tester règle simulée puis mouvement frontal, silence horaire, arrêt audio et retour automatique.
7. Exporter/restaurer le ZIP, vérifier les médias, puis ressaisir les secrets caméra exclus.
8. Couper le WAN en conservant le Wi-Fi et répéter les contrôles locaux. Effectuer ensuite une session de 30 min, 4 h et 24 h en relevant mémoire, batterie et température.

Les résultats matériels non rapportés restent « en attente de validation matérielle », même si le code correspondant et les tests d’émulateur passent.
