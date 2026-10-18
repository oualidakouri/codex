# Feuille de route et portes de validation

## 0 — Preuve tablette/caméras, retour utilisateur reçu

- [x] Initialiser le projet natif et une APK de diagnostic compilable.
- [x] Tests JVM des primitives, échanges DVRIP et ONVIF sur serveurs locaux.
- [x] Diagnostic matériel, mémoire et thermique ; lecture et découverte à déclencher sur tablette.
- [x] Identifiants chiffrés, export/restauration sans secrets, simulation explicite.
- [ ] Exécuter la matrice sur la tablette réelle et au moins une caméra possédée par l’utilisateur.
- [ ] Confirmer vidéo, PTZ/STOP, reprise Wi-Fi, mouvement/veille et fonctionnement WAN coupé.
- [ ] Obtenir un essai prolongé stable et corriger les défauts constatés.

L’utilisateur a déclaré le diagnostic fonctionnel et confirmé vidéo et contrôle d’une caméra, puis demandé explicitement la V1. Ce retour autorise la suite ; il ne fournit pas de mesure de stabilité 24 h ni de résultat pour la seconde caméra absente du scan. Ces points restent séparés des fonctions indépendantes. La livraison actuelle est décrite dans README.md et docs/VALIDATION-V1.md.

## 1 — Architecture produit et dashboard

Dispositions persistantes, navigation de production, widgets réellement déplaçables/redimensionnables, profils de mise en page, thème, ressources de traduction, migration Room/DataStore si justifiée.

## 2 — Salat, adhan et douas

Évaluer Adhan Kotlin et sa licence, calculs hors ligne testés (méthodes, madhab, fuseaux/DST, ajustements), import audio, AlarmManager et permissions précises, reprogrammation au redémarrage, contenu de douas sourcé et vérifié. Aucun contenu religieux généré ou attribué artificiellement.

## 3 — Caméras et PTZ produit

Intégrer les connecteurs validés, sélection profils/sous-flux, mosaïque bornée selon ressources, reconnexion, arrêts liés à la visibilité, fonctions avancées uniquement selon capacités réellement confirmées.

## 4 — Veille intelligente

Intégrer les stratégies validées dans l’interface murale, plages horaires, capteurs disponibles, faux positifs et repli au toucher. Le mode physique dépend des APIs Android et du matériel autorisé.

## 5 — Capteurs et objets locaux

Connecteurs HTTP/API embarqués, stockage d’équipements/pièces, erreurs hors ligne, mesures horodatées ; aucun protocole inventé et aucun serveur intermédiaire obligatoire.

## 6 — Automatisations et médias

Règles, conditions, délais, cooldowns, priorités, test simulé, import et lecture de médias locaux, journal borné et sauvegardes versionnées.

## 7 — Extensions

Tesla sur consentement via services officiels, consommation maîtrisée, modules familiaux et énergie. Aucun de ces modules ne retarde les fonctions locales de base. Home Assistant n’est pas requis ni prévu pour les premières versions.
