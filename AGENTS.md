# Maison OS : consignes du projet

- Application Android native Kotlin/Compose ; toute logique des premières versions doit fonctionner directement sur la tablette, sans serveur ou Home Assistant obligatoire.
- Le périmètre actuel est la V1.3 demandée après les essais utilisateur. L’utilisateur a déclaré le diagnostic fonctionnel sur son matériel et demandé explicitement de passer à l’application. Lire README.md, ARCHITECTURE.md, docs/V1-GUIDE.md, docs/LIMITATIONS-V1.md et docs/VALIDATION-1.3.0.md avant d’étendre le produit.
- Ne pas confondre tests JVM, émulateur et essais matériels réels. La seconde caméra est découverte en DVRIP/RTSP ; sa connexion et son firmware restent à vérifier ; elle ne bloque pas les fonctions indépendantes.
- Ne jamais inventer une compatibilité, un horaire de prière, une source religieuse ou une connexion réelle. Garder simulations et états non vérifiés explicites.
- Aucun secret, IP personnelle ou équipement personnel codé en dur. Les adresses de fixtures dans les tests sont des exemples, pas une configuration utilisateur.
- Préserver chiffrement Keystore, TLS, contrôle des destinations LAN, limites mémoire, arrêts liés au cycle de vie et STOP PTZ. Ne pas introduire de contournement des protections Android.
- Conserver les mentions MIT des composants iCSee adaptés et leur licence dans l’APK.
- Chaque tâche cloud est déjà isolée : utiliser le checkout existant ; ne pas créer de worktree sans demande explicite.
- Dans ce cloud, utiliser `python tools/cloud-gradle.py :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon`. Si les outils manquent, lancer `python tools/bootstrap-cloud.py`.
- Les tests instrumentés nécessitent un appareil ou un émulateur : leur compilation n’est pas leur exécution. Mettre à jour le rapport de validation après une modification significative.
- Pour les mises à jour de l’APK fournie, conserver applicationId et clé `.gradle/maison-debug.keystore`. Ne pas commiter la clé. Une APK de CI générée avec une autre clé ne met pas à jour cette installation.

- Interrupteurs : onglet retiré à la demande de l’utilisateur. Les anciens modèles ne servent qu’à lire les sauvegardes ; aucune commande n’est exposée. Les futures connectiques passeront par Home Assistant lorsque l’utilisateur l’aura installé sur son ancien PC. Ne pas le déployer maintenant.
- Météo de la ville distincte de la température de batterie : aujourd’hui + trois jours, sans extrapolation de données manquantes. Les indications vestimentaires sont des règles simples, pas un service météo supplémentaire.
- Coran : métadonnées sourcées et fichiers audio du fournisseur ou URL personnelle ; ne pas générer d’audio religieux ni inventer une disponibilité. Garder l’adhan prioritaire, un arrêt explicite et aucun démarrage au boot.
- Actualités : titres/source/date/lien à partir des flux RSS/Atom ; pas de titres fictifs présentés comme réels, pas de copie d’articles intégraux.
- Réseau extérieur explicite pour météo, actualités, lecteur réseau et mises à jour ; les fonctions locales gardent leur autonomie sans serveur. Distinguer le proxy cloud bloqué des conditions de la tablette.
- Publier les mises à jour avec tools/prepare-release.py : même certificat, versionCode augmenté et APK/manifeste cohérents. Android conserve la confirmation d’installation.

- Citrouille : essai local et expérimental, désactivé par défaut. Vrai rendu 3D transparent, suivi du visage frontal distinct du mouvement, caméra au premier plan uniquement. Ne pas décrire une caméra virtuelle comme preuve de suivi humain. Préserver arrêt arrière-plan/adhan/protection ressources et libération des images/rendu.
- Coran dashboard : navigation sur les sourates réellement annoncées ; à l’arrêt, modifier la sélection sans lancer le service. Timeline activée uniquement pour une piste déplaçable de durée connue.
