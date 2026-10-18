# Maison OS : consignes du projet

- Application Android native Kotlin/Compose ; toute logique des premières versions doit fonctionner directement sur la tablette, sans serveur ou Home Assistant obligatoire.
- Le périmètre actuel est l’APK de diagnostic. Lire README.md, ARCHITECTURE.md, docs/LIMITATIONS.md, docs/VALIDATION.md et docs/ROADMAP.md avant d’étendre le produit.
- Ne pas commencer le dashboard tant que la validation critique tablette/caméras n’a pas été effectuée et ses défauts stabilisés. Ne pas confondre tests JVM, émulateur et essais matériels réels.
- Ne jamais inventer une compatibilité, un horaire de prière, une source religieuse ou une connexion réelle. Garder simulations et états non vérifiés explicites.
- Aucun secret, IP personnelle ou équipement personnel codé en dur. Les adresses de fixtures dans les tests sont des exemples, pas une configuration utilisateur.
- Préserver chiffrement Keystore, TLS, contrôle des destinations LAN, limites mémoire, arrêts liés au cycle de vie et STOP PTZ. Ne pas introduire de contournement des protections Android.
- Conserver les mentions MIT des composants iCSee adaptés et leur licence dans l’APK.
- Chaque tâche cloud est déjà isolée : utiliser le checkout existant ; ne pas créer de worktree sans demande explicite.
- Dans ce cloud, utiliser `python tools/cloud-gradle.py :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon`. Si les outils manquent, lancer `python tools/bootstrap-cloud.py`.
- Les tests instrumentés nécessitent un appareil ou un émulateur : leur compilation n’est pas leur exécution. Mettre à jour le rapport de validation après une modification significative.
