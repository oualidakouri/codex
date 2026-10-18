# Maison OS : consignes du projet

- Application Android native Kotlin/Compose ; toute logique des premières versions doit fonctionner directement sur la tablette, sans serveur ou Home Assistant obligatoire.
- Le périmètre actuel est la V1. L’utilisateur a déclaré le diagnostic fonctionnel sur son matériel et demandé explicitement de passer à l’application. Lire README.md, ARCHITECTURE.md, docs/V1-GUIDE.md, docs/LIMITATIONS-V1.md et docs/VALIDATION-V1.md avant d’étendre le produit.
- Ne pas confondre tests JVM, émulateur et essais matériels réels. La seconde caméra non découverte reste un point matériel à vérifier ; elle ne bloque pas les fonctions indépendantes.
- Ne jamais inventer une compatibilité, un horaire de prière, une source religieuse ou une connexion réelle. Garder simulations et états non vérifiés explicites.
- Aucun secret, IP personnelle ou équipement personnel codé en dur. Les adresses de fixtures dans les tests sont des exemples, pas une configuration utilisateur.
- Préserver chiffrement Keystore, TLS, contrôle des destinations LAN, limites mémoire, arrêts liés au cycle de vie et STOP PTZ. Ne pas introduire de contournement des protections Android.
- Conserver les mentions MIT des composants iCSee adaptés et leur licence dans l’APK.
- Chaque tâche cloud est déjà isolée : utiliser le checkout existant ; ne pas créer de worktree sans demande explicite.
- Dans ce cloud, utiliser `python tools/cloud-gradle.py :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon`. Si les outils manquent, lancer `python tools/bootstrap-cloud.py`.
- Les tests instrumentés nécessitent un appareil ou un émulateur : leur compilation n’est pas leur exécution. Mettre à jour le rapport de validation après une modification significative.
- Pour les mises à jour de l’APK fournie, conserver applicationId et clé `.gradle/maison-debug.keystore`. Ne pas commiter la clé. Une APK de CI générée avec une autre clé ne met pas à jour cette installation.
