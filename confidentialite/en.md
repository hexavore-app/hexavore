# Privacy policy

*Last updated: September 21, 2026*

Hexavore is a free and open-source food tracking app for Android. This page explains which data the app handles, where it goes, and how to erase it.

## In short

- **Hexavore has no server.** The project does not receive, store or look at any of your data.
- **No account, no ads, no usage statistics, no automatic crash reports.**
- **Everything you enter stays on your phone.**
- Two outside services can be contacted, and only when you take the action that calls them: **Open Food Facts** and **the artificial intelligence provider you chose**.

## Who is responsible

Hexavore is an open-source project, developed on a personal, non-commercial basis. Its source code is public: [github.com/hexavore-app/hexavore](https://github.com/hexavore-app/hexavore).

For any question about this policy: [contact@hexavore.app](mailto:contact@hexavore.app).

## What the app keeps on your phone

- **Your profile**: date of birth, sex (or "prefer not to say"), height, activity level, unit system.
- **Your weigh-ins and your goals.**
- **Your food log**: dishes, foods, quantities, nutrition values, time of entry.
- **Your custom foods, your favorites**, and the Open Food Facts products you have already looked up.
- **Your settings**: AI provider API keys, the username and password of your Open Food Facts account if you entered one, display preferences.

This is health information. It does not leave the phone, except in the cases described below. Android's automatic backup is turned off for Hexavore: your data does not reach your Google account without you knowing either.

## What leaves your phone

Only to the destinations below, and only when you take the matching action. Every connection is encrypted (HTTPS). As with any Internet connection, the service you reach sees your device's IP address.

### Open Food Facts

[Open Food Facts](https://world.openfoodfacts.org) is an open, collaborative database of food products.

- **When you scan a product the app does not know yet**: the barcode.
- **When you tap "Search Open Food Facts"**: the text you searched for.
- **When you choose to contribute a product you created**: its name, brand, barcode, serving size and nutrition values, together with the username and password of **your** Open Food Facts account. The product becomes public, under the ODbL license. Nothing is sent without your explicit consent.

The first two requests carry no identifier: Open Food Facts cannot link them to you. [Open Food Facts' privacy policy](https://world.openfoodfacts.org/privacy) applies to what it receives.

### The artificial intelligence provider you chose

Only if you saved an API key, and each time you start an analysis:

- **the photo of your meal**, scaled down to 1,024 pixels, **and/or the sentence you wrote**;
- in **deep analysis**, excerpts of the app's food catalog — food names and nutrition values, nothing personal;
- **your API key**, which identifies your account with that provider.

The request goes straight from your phone to the provider: the project sees nothing of it. The provider bills these calls to your account, and its own privacy policy applies to what it receives. Depending on your choice, that is Anthropic, Google (Gemini), OpenAI, DeepSeek, Mistral AI, or the service whose address you entered yourself.

Before the first photo is sent, the app asks for your consent and names the provider. A photo taken from within Hexavore is written to a temporary app folder, read, then deleted right away: it never enters your gallery. An image picked from your gallery is read without being modified.

### Nothing else

- **The barcode reader works entirely on the phone.** Camera images are neither stored nor sent.
- **No advertising, analytics or tracking library.** No update check, no signal sent at startup.
- **Debug mode**, in the AI settings, keeps the latest exchanges with the provider in memory only. Nothing is written, and everything is gone when the app closes.

## Your backups

"Export my data" produces a file that you save wherever you decide. It contains your profile, goals, weigh-ins, food log, foods and favorites, and **never** your API keys or your Open Food Facts account. If it then goes to a storage service or an email, it does so through the app you chose, under that app's own rules.

## Security

- Encrypted connections (HTTPS), without exception.
- API keys and the Open Food Facts password are encrypted by Android's secure key store (Keystore).
- The project holds no data: none can leak from it.

## Retention and deletion

- Your data stays on your phone until you erase it.
- **Settings → Backup → "Erase all my data"** erases the log, the profile, the API keys, the Open Food Facts account and the other settings. Files you already exported are not touched.
- **Uninstalling Hexavore** erases everything the app stored.
- What was sent to an outside service falls under that service's policy: a product contributed to Open Food Facts stays public, and what an AI provider receives is kept under its terms.

## Your rights

The project holds no data about you: there is nothing to access, correct or delete on its side. Everything happens in the app. For what was sent to Open Food Facts or to your AI provider, contact them. You can also lodge a complaint with your data protection authority — in France, the [CNIL](https://www.cnil.fr).

## Audience

Hexavore is intended for adults. It is not a medical device, and it does not replace the advice of a health professional.

## Changes

This policy changes when the app changes, and at the same time. [Its full history is public](https://github.com/hexavore-app/hexavore/commits/main/confidentialite/en.md).
