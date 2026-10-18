# Utiliser Maison OS 1.0

## Mise à jour

Installer `downloads/maison-os-1.0.0.apk` par-dessus le diagnostic. Ne pas désinstaller l’ancienne application : sa clé Keystore et sa configuration sont dans le stockage privé. La V1 reprend la caméra sélectionnée ; les caméras qui n’avaient jamais été enregistrées dans le diagnostic doivent être ajoutées.

## Dashboard

Le bouton **Personnaliser** déverrouille les cartes. Maintenir une carte puis la déplacer sur la grille ; les autres cartes sont repositionnées pour éviter les chevauchements. Tirer la poignée `↘` pour redimensionner. `⋯` permet la transparence, l’affectation d’une caméra, le changement de page et la suppression. **Verrouiller** protège ensuite la disposition des gestes accidentels.

Ajouter des pages depuis cette barre. Créer/changer les profils dans Paramètres. Le portrait possède sa propre disposition pour conserver une interface lisible ; les widgets ajoutés existent dans les deux orientations.

## Salat et adhan

Choisir la ville volontairement ou saisir latitude, longitude et fuseau IANA. Choisir la méthode et l’Asr standard/hanafite. Les corrections par prière sont en minutes. Le calcul est local avec les règles du fuseau pour l’heure d’été. Une configuration incohérente est refusée.

Importer un fichier audio personnel, choisir la récitation commune et éventuellement Fajr. Activer individuellement les cinq prières. L’audio est local et reste après redémarrage. Vérifier le volume système des alarmes, l’autorisation d’alarmes exactes et les notifications. L’écran épuré de lecture revient automatiquement à l’application après la récitation ; le bouton **Arrêter** est également disponible dans la notification.

Le test immédiat vérifie la lecture. Le test dans 10 secondes passe réellement par AlarmManager et le receiver ; il reste à essayer sur la tablette Android 16 avec ses restrictions constructeur. Une plage silencieuse peut traverser minuit. Pour Umm al-Qura pendant Ramadan, ajuster Isha selon le calendrier retenu.

Les dates hégiriennes sont calculées par Android/Java et peuvent être corrigées de ±2 jours ; elles ne remplacent pas une annonce locale d’observation lunaire.

## Plusieurs caméras

Le catalogue conserve plusieurs équipements avec noms, pièces, ports et secrets. Les deux ports d’une même adresse peuvent être ONVIF et DVRIP : le scan les regroupe sur un seul hôte. La limite de vidéos ne limite jamais la découverte.

Dans la fiche caméra, **Afficher** à droite du champ **Mot de passe** révèle localement le secret enregistré ; **Masquer** le cache de nouveau. Aucun identifiant de la tablette n’est accessible depuis le cloud. Pour VLC, utiliser l’URL RTSP de cette caméra et les identifiants de son compte local ; le compte iCSee peut être différent.

La recherche habituelle répète les sondes pendant cinq secondes. Si une caméra ne répond pas, essayer la **recherche approfondie du segment Wi-Fi /24** : elle vérifie une réponse DVRIP de négociation ou RTSP OPTIONS sur les ports habituels, sans essayer de mot de passe. Elle n’invente pas de caméra à partir d’un port simplement ouvert. Une autre configuration de ports nécessite l’ajout manuel.

L’adresse doit être une IPv4 privée valide ; un octet supérieur à 255 est refusé. Aucun QR cloud n’est transformé artificiellement en IP locale. L’isolation Wi-Fi, les VLAN et les fonctions locales désactivées dans le firmware peuvent empêcher la découverte.

Enregistrer avant de tester. Le champ RTSP sert au sous-flux de widget ; le champ principal facultatif sert au plein écran. Les identifiants restent dans les champs dédiés. Le PTZ ONVIF s’affiche après annonce de capacité ; le PTZ DVRIP demande l’autorisation de test sur la caméra motorisée. Vérifier toujours l’arrêt visuellement.

## Veille, médias et événements

Le mode sombre est logiciel. L’analyse frontale fonctionne uniquement au premier plan, sans conserver/transmettre d’images. Si activée dans Paramètres et déjà autorisée, elle reprend au retour de l’application. Une extinction physique ou le passage en arrière-plan empêche le réveil caméra.

Importer des fichiers audio, vidéos MP4/WebM ou images PNG/JPEG/WebP dans Paramètres. Les règles Événements peuvent afficher un message ou un média, avec délai, durée et repos entre déclenchements. Les tests manuels et le courrier sont indiqués comme simulations ; une règle de présence peut utiliser le mouvement frontal réel.

## Sauvegarde

Exporter un ZIP depuis Paramètres avant un changement important. L’archive inclut dispositions, préférences, règles, favoris et médias. Les mots de passe et les URL RTSP complètes sont exclus pour éviter d’exporter des secrets dans les URL. Après restauration, renseigner à nouveau ces champs. La restauration refuse les chemins sortant de l’archive, les fichiers incohérents et les archives trop volumineuses.
