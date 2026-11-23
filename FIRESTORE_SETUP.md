# Firestore security rules

Atyaf stores each account's favorites and saved wallpapers under:

- `users/{uid}/favorites/{wallpaperId}`
- `users/{uid}/wallpapers/{wallpaperId}`

The rules in `firestore.rules` restrict every read and write to the authenticated account that owns the UID path. Writes must also include a matching `userId` field.

## Apply the rules

1. Open the Firebase Console for the Firebase project connected to Atyaf.
2. Open the Firestore database used by the app (its database ID is configured in `app/src/main/res/values/firebase_applet_config.xml`).
3. Open the **Rules** tab.
4. Replace the existing rules with the contents of `firestore.rules`.
5. Publish the rules.

Do not use open rules such as `allow read, write: if true`. The app's favorites require the user to be signed in.
