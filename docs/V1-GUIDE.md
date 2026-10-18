# Utiliser Maison OS 1.3

## Mise à jour

Installer `downloads/maison-os-1.3.0.apk` par-dessus le diagnostic. Ne pas désinstaller l’ancienne application : sa clé Keystore et sa configuration sont dans le stockage privé. La mise à jour reprend les caméras enregistrées et leurs identifiants ; les caméras qui n’avaient jamais été enregistrées dans le diagnostic doivent être ajoutées.

## Dashboard

Le bouton **Personnaliser** déverrouille les cartes. Touchez une carte ou `⋯`, puis utilisez les flèches et les boutons Largeur/Hauteur. Enregistrez pour appliquer. Vous pouvez aussi maintenir une carte puis la déplacer sur la grille ; les autres cartes sont repositionnées pour éviter les chevauchements. Tirer la poignée `↘` pour redimensionner. `⋯` permet la transparence, l’affectation d’une caméra, le changement de page et la suppression. **Verrouiller** protège ensuite la disposition des gestes accidentels.

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

## Thèmes, miniatures et douas

Paramètres propose Océan nocturne, Forêt, Prune et cuivre, Graphite, Sable et olive, Glacier. Les couleurs du texte suivent toute l’interface. Les textes des invocations s’affichent en arabe, sans traduction.

Les deux premières caméras sont placées en tête de la première page après migration. Un toucher ouvre le plein écran ; les lecteurs de l’accueil s’arrêtent pendant cet agrandissement. Paramètres → Caméras et consommation permet de replacer les deux cartes, de désactiver le direct ou de limiter à un flux. Chaque caméra doit avoir sa propre URL RTSP fonctionnelle.

Si une caméra apparaît en DVRIP/RTSP mais sans ONVIF dans le scan /24, ce résultat n’est pas un échec ONVIF : ce scan ne teste pas ce protocole. Enregistrer l’adresse et les identifiants, tester les ports, puis DVRIP et RTSP séparément. Le bouton Préparer les chemins iCSee/Xiongmai propose un format de firmware différent et le sous-flux 1 ; il ne teste aucun mot de passe par défaut. ONVIF n’est pas requis pour lire RTSP.

## Météo de votre ville

Dans Météo, utiliser la position actuelle (autorisation de localisation approximative ponctuelle), rechercher une ville ou reprendre le lieu de salat. La ville, la température extérieure et la date des données sont affichées dans la carte Météo de l’accueil. Le vent est en km/h. Le fonctionnement nécessite Internet ; les dernières données de cette même ville restent visibles et marquées en cache si elles sont anciennes ou hors ligne.

## Connectiques reportées

L’onglet et les widgets Interrupteurs sont retirés. Les futures connectiques seront ajoutées quand Home Assistant sera prêt sur le PC de l’utilisateur. Maison OS continue de fonctionner seul pour ses fonctions actuelles.

Événements conserve ses scènes avec message, son ou vidéo : importer un média dans Paramètres, créer une règle, choisir un déclencheur, puis Tester en simulation.

## Mises à jour intégrées

Cette APK ajoute le mécanisme : Paramètres → Mises à jour de Maison OS → Rechercher une mise à jour. La vérification automatique à l’ouverture peut être désactivée. Une nouvelle version donne accès aux notes, au téléchargement et à Installer ; l’empreinte et la signature sont vérifiées avant d’ouvrir Android. Si Android demande Autoriser cette source, accepter pour Maison OS, revenir et toucher Installer. Ne pas désinstaller l’ancienne application : la mise à jour garde les données.

Le manifeste par défaut est `https://raw.githubusercontent.com/oualidakouri/codex/main/downloads/update.json`. Le champ URL permet de changer de publication HTTPS. La version est recherchée à chaque nouveau lancement ; les retours au premier plan sont espacés d’au moins une minute pour éviter les requêtes rapprochées. Le bouton manuel force la vérification. La proposition peut être reportée et les fonctions locales restent disponibles sans Internet.

## Prévisions et tenue du lendemain

Le widget météo affiche aujourd’hui et les trois dates futures disponibles. Ouvrir Météo pour mini/maxi, pluie et indications vestimentaires. « Demain » est calculé dans le fuseau de la ville ; un cache ancien conserve ses dates et n’est pas transformé en nouvelles prévisions.

## Lecteur de Coran

Le widget Lecteur de Coran est ajouté à la première page (faire défiler si nécessaire). Dans Coran, choisir un récitant/lecture dans la liste, puis Choisir la sourate. Les sept récitants initiaux sont Alafasy, Sudais, Shuraim, Husary, Abdulbasit, Minshawi et Maher Al Meaqli. Le catalogue peut être actualisé via MP3Quran ; les enregistrements sont diffusés en réseau.

Pour une radio ou un serveur personnel, Paramètres → Lecteur de Coran → URL réseau, puis Enregistrer mon flux. Utiliser une URL audio directe HTTP(S), MP3/AAC ou HLS `.m3u8`, pas une page Web. Une radio continue ne permet pas de choisir sa sourate ; passer au catalogue pour cette fonction. Lecture/Arrêter sont disponibles dans le dashboard et l’écran Coran ; la notification offre Arrêter. Le volume de l’application complète le volume multimédia Android.

La récitation continue en veille et en arrière-plan, s’arrête à la fin d’une sourate ou sur Arrêter, et est interrompue par l’adhan. Elle ne reprend pas automatiquement après ce dernier ni après redémarrage. Les URL personnelles sont exclues des sauvegardes : les ressaisir après restauration.

## Actualités France et ville

Le widget Actualités est ajouté à la première page. Il affiche des titres nationaux et locaux avec sources et liens. Toucher un titre ouvre le navigateur ; Lire/configurer ouvre la liste complète. Actualisation automatique toutes les quinze minutes au premier plan, et bouton Actualiser manuel.

Dans Actualités → Configurer, laisser Ville vide pour reprendre la ville météo (sinon salat), ou saisir votre commune. La source nationale est Franceinfo ; un bouton propose Google Actualités France. Le local utilise une recherche Google Actualités sur le nom de la ville. Un flux RSS/Atom HTTPS personnel peut remplacer chaque source. En cas de panne Internet, les derniers titres restent datés ; aucun titre d’exemple n’est présenté comme réel.

## Commandes du Coran dans le dashboard

La carte contient Lecture/Arrêter, Son −/+ par pas de 10 %, ◀/▶ Sourate et ▲/▼ Récitant. Les listes bouclent ; si la sourate n’existe pas chez le nouveau récitant, sa première sourate disponible est choisie. En lecture, le changement relance la sélection après enregistrement ; à l’arrêt, il prépare simplement le prochain choix. Le volume système Android reste indépendant.

La barre affiche le temps écoulé et la durée connue. Glissez-la puis relâchez pour avancer/reculer. Elle reste désactivée avant le chargement et sur une radio en direct non déplaçable. La carte est agrandie à la mise à jour ; elle peut défiler si vous la réduisez. « Choisir » ouvre le catalogue complet. Le flux personnel reste dans Coran → Flux personnel · facultatif.

## Essayer la citrouille

Dans Paramètres → Laboratoire · Citrouille 3D, **Démonstration sans caméra** montre un regard animé, explicitement simulé. **Tester le suivi avec la caméra** demande la permission Android puis cherche le visage frontal le plus grand. Placez-vous de face dans une pièce éclairée, bougez lentement à gauche/droite et en hauteur. Le message distingue ouverture, visage détecté, absence de visage et indisponibilité. Aucune image n’est enregistrée. Un visage incliné, masqué, trop petit ou dans l’obscurité peut ne pas être détecté.

Pour l’apparition automatique, activez l’interrupteur, choisissez le délai après inactivité (5 s, 30 s, 1 min ou 5 min) et la durée d’affichage (15 s, 1 min, 2 min ou 5 min). Ce mode remplace la veille sombre tant qu’il est actif. Une interaction tactile remet le délai à zéro. Toucher l’écran, Fermer ou Retour retire la citrouille ; elle disparaît aussi à la fin de la durée, à la sortie de Maison OS, pendant l’adhan ou en protection thermique/batterie. Aucun fonctionnement caméra lorsque l’écran est physiquement éteint ou l’application en arrière-plan.

Le rendu et le suivi restent expérimentaux sur la tablette : confirmer le sens des yeux, la fluidité et l’échauffement avant de laisser l’apparition automatique activée longtemps.
