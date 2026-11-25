# Publier sur le Google Play Store

Tout ce dont la Play Console a besoin est dans ce dossier.

| Élément | Fichier |
|---|---|
| Fichier à envoyer (Android App Bundle) | `NeigeAlpesPyrenees.aab`, page *Releases* du dépôt |
| Titre, description courte et longue (FR / EN) | `listing/fr-FR/`, `listing/en-US/` |
| Politique de confidentialité | `PRIVACY.md` (doit être publiée à une adresse web publique) |
| Formulaire « Sécurité des données » | ci-dessous |

## Étapes

1. **Clé de signature définitive** : ajouter les 4 secrets GitHub (voir plus bas). À partir de là, l'APK et l'AAB sont signés avec cette clé.
   Il faut alors désinstaller une dernière fois l'app installée avec l'ancienne clé de test.
2. **Compte développeur** : https://play.google.com/console, frais d'inscription uniques de 25 $, vérification d'identité.
3. **Créer l'application** dans la Play Console, langue par défaut français, application gratuite.
4. **Signature d'application Play** : accepter que Google gère la clé de signature ; la clé ajoutée en secret GitHub devient la *clé d'importation*.
5. **Test fermé obligatoire** (compte personnel créé après le 13/11/2023) : au moins **12 testeurs inscrits pendant 14 jours d'affilée** avant de pouvoir demander l'accès à la production.
6. Remplir la fiche (textes de `listing/`, icône 512 × 512, image de présentation 1024 × 500, au moins 2 captures d'écran), la classification du contenu, le public cible, la politique de confidentialité et la sécurité des données.
7. Envoyer l'AAB dans le canal de test fermé, inviter les testeurs, attendre 14 jours, puis demander l'accès à la production.

À chaque nouvelle version envoyée sur le Play Store, `versionCode` (dans `app/build.gradle.kts`) doit augmenter.

## Secrets GitHub (clé définitive)

Dépôt GitHub > **Settings** > **Secrets and variables** > **Actions** > **New repository secret**, quatre fois :

| Nom | Valeur |
|---|---|
| `NEIGE_KEYSTORE_BASE64` | le fichier de clé encodé en base64 |
| `NEIGE_KEYSTORE_PASSWORD` | mot de passe du fichier de clé |
| `NEIGE_KEY_ALIAS` | `neige-upload` |
| `NEIGE_KEY_PASSWORD` | mot de passe de la clé |

Gardez aussi une copie du fichier de clé et des mots de passe dans un gestionnaire de mots de passe.

## Formulaire « Sécurité des données »

- L'application collecte-t-elle ou partage-t-elle des données utilisateur ? **Non.**
- Les données sont-elles chiffrées en transit ? **Oui** (HTTPS uniquement).
- Les utilisateurs peuvent-ils demander la suppression de leurs données ? Sans objet : rien n'est collecté ; tout est supprimé à la désinstallation.
- Autorisations : Internet ; notifications (facultatives, alertes neige fraîche).
- Publicité : **aucune**. Public cible : 13 ans et plus. Application gratuite.
