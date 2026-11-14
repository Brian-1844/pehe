# Pehe – lecteur de musique du fenua 🌺

Lecteur MP3 pour Android, au thème polynésien, avec Mamahu intégré.

## Musique
- Lecture / pause, suivant / précédent, ±10 s, barre de progression, volume, minuterie de sommeil
- Lecture aléatoire, répétition (tout / un morceau), reprise là où tu t'étais arrêté
- **Ajout automatique** des chansons enregistrées sur le téléphone
- **Bibliothèque** : Toutes, Favoris, Lues en dernier, Plus écoutées, Mes enregistrements, et tes **playlists** (créer, renommer, supprimer, réordonner)
- **Partager** une chanson ou un enregistrement (WhatsApp, Messenger, Bluetooth…)
- **Scratch** : fais tourner le disque au doigt (avant, arrière, arrêt), touche-le pour lecture/pause
- Titre, artiste, album et pochette lus dans les tags ID3

## Son
- Remix : vitesse, voix normale ou non, réverbe, écho, basses, son 8D ; préréglages Nightcore, Slowed + réverbe, 8D, Écho…
- Égaliseur 10 bandes avec préréglages (dont Ukulélé et Doux pour la nuit)
- Nuances : coupe-grave, adoucir, présence de la voix, largeur stéréo
- **Mon profil d'écoute** : 4 questions A/B pour régler le son à ton goût
- Encoche acouphène (musique encochée de Mamahu) sur toute ta musique
- **Pad d'effets** : 18 effets (batterie, tō'ere, pahu, ukulélé, pū, klaxon, sirène, laser, scratch, vague, oiseau…) + tes propres effets (fichier ou enregistrement)
- **Boucle rythmique** : Tō'ere (ori), Fenua, Boom-bap, Reggae, Disco, tempo réglable et « taper le tempo »

## Micro
- Enregistrer sa voix, un son extérieur (micro brut) ou sa musique remixée ; les enregistrements sont gardés dans l'app

## Mamahu
- Sons apaisants, battements binauraux, anti-bourdon, outils acouphènes, respiration guidée

## Réglages
- Skins : Tahiti, Forêt, Surf, Ville, et **tes propres fonds d'écran** (photo de ta galerie, couleurs assorties automatiquement)
- Appareils audio : casques et enceintes Bluetooth détectés, pause à la déconnexion, accès à l'association Bluetooth
- Installation et mises à jour depuis GitHub

## Télécharger l'APK
À chaque modification sur `main`, GitHub construit l'APK tout seul : onglet **Releases**, fichier `Pehe.apk`.
Lien direct vers la dernière version : `https://github.com/Brian-1844/pehe/releases/latest/download/Pehe.apk`

## Signature
L'APK est toujours signé avec la même clé (`pehe.keystore`), pour que les mises à jour s'installent par-dessus.
Pour plus de sécurité, mets le dépôt en privé ou crée le secret `KEYSTORE_PASSWORD`. Attention : avec un dépôt privé, le bouton « Installer » et la vérification des mises à jour ne peuvent plus lire les Releases.

## Modifier l'app
Le lecteur est dans `www/index.html`, le module Android dans `native/`.
