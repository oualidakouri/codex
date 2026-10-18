# Livraison de la preuve de faisabilité · 18 octobre 2026

APK de développement : `dist/maison-os-diagnostic-0.1.0.apk` (17 650 426 octets), application `fr.maisonos.diagnostic`, version `0.1.0-diagnostic`. Android API 26 minimum, API 36 ciblée.

SHA-256 : `6dfbe54816a0cbe2b5f61db619602372b7ac8de863db2773cd81dbb492ea0617`.

## Résultats vérifiés dans ce cloud

- Bootstrap des outils exécuté, puis réexécuté avec les outils déjà présents : succès.
- `:core:test` : **23 tests exécutés, 23 réussis, 0 échec, 0 erreur, 0 ignoré**.
- `:app:assembleDebug` et `:app:assembleDebugAndroidTest` : succès.
- `:app:lintDebug` : **0 erreur, 3 avertissements**. Deux suggestions de nouvelles versions de bibliothèques ; un avertissement pour le trafic clair nécessaire aux protocoles LAN caméra, limité par les contrôles applicatifs. TLS n’est pas désactivé.
- Signature de l’APK : vérifiée par `apksigner`, schéma v2, une signature de développement.
- APK principale et APK d’instrumentation installées avec succès sur l’émulateur Android 11/API 30, écran 1280 × 800, sans accélération KVM.
- Tests instrumentés : **3/3 réussis sur l’APK finale**, après une première exécution également réussie (configuration Keystore, parseur SOAP Android et navigation/simulation). Résultat final : `OK (3 tests)` en 76,276 secondes, consigné dans `dist/verification/android-instrumentation.txt`.

Les rapports bruts de compilation, lint, signature et empreinte sont sous `dist/verification/`. Une copie de l’APK vérifiée et des rapports est fournie dans `downloads/` pour permettre leur téléchargement depuis GitHub. Les répertoires de build, caches et clés restent exclus de Git. La CI fournie n’a pas été exécutée depuis ce cloud et aucune Release GitHub n’a été créée.

## Limites de ces résultats

L’émulateur logiciel est lent et son interface système a affiché une alerte de non-réponse ; ses mesures de performance ne sont pas transposables à la tablette. Un résultat positif d’instrumentation ne valide ni les performances à long terme ni Android 16 réel.

**En attente de validation matérielle** : tablette réelle, découverte et authentification des caméras de l’utilisateur, image RTSP et codecs matériels, latence, PTZ et STOP mécanique, présence frontale, température/consommation en continu, reprise Wi-Fi, choix du lanceur après redémarrage et fonctionnement avec WAN coupé.

Les échanges réseau des tests JVM utilisent des serveurs de fixture locaux. Ils ne sont jamais présentés comme des essais sur les caméras domestiques. La simulation du courrier est étiquetée comme telle.

## Configuration de l’environnement

`install_script` et `start_skill` sont enregistrés dans le brouillon de configuration : préparation des outils, commandes de vérification, reprise de l’environnement, limites d’ADB/émulateur et consignes de validation matérielle. Cet enregistrement ne publie pas l’environnement. Vérifier/enregistrer les changements dans les paramètres puis publier pour conserver une version réutilisable.

La suite du développement reste au jalon de preuve de faisabilité. Le dashboard et les modules religieux n’ont pas été commencés ; voir [la feuille de route](ROADMAP.md) et [la procédure de test sur tablette](VALIDATION.md).
