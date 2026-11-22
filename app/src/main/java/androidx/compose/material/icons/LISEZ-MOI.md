# Icônes copiées de material-icons-extended

Ces fichiers viennent de `androidx.compose.material:material-icons-extended` (version dans
`VERSION.txt`, licence Apache 2.0, en-tête conservé). On ne garde que les icônes utilisées : la
bibliothèque entière pesait plusieurs Mo dans l'APK. Les paquets et les noms sont ceux de la
bibliothèque, donc les `import androidx.compose.material.icons...` du code n'ont pas changé.

Ajouter une icône : `scripts/extract-icons.sh` (réseau nécessaire) refait la copie de toutes les
icônes utilisées. Les icônes de base (Add, Close, Settings…) restent fournies par
`material-icons-core`, que Material 3 apporte déjà.
