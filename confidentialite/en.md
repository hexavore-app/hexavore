# Privacy policy

*Last updated: October 14, 2026*

Hexavore is a free and open-source food tracking app for Android. This page explains which data the app handles, where it goes, and how to erase it.

## In short

- **Hexavore has no server.** Nothing reaches the project on its own.
- **No account, no ads, no usage statistics, no automatic crash reports.**
- **Everything you enter stays on your phone.**
- Two outside services can be contacted, and only when you take the action that calls them: **Open Food Facts** and **the artificial intelligence provider you chose**.
- The project only receives what you write to it yourself, including the two emails the app can prepare: **a report** of a wrong suggestion and **a crash trace**.

## Who is responsible

**Charly Flu** develops Hexavore on a personal, non-commercial basis, and is the data controller under the GDPR. There is little to process: the project has no server, and only receives the emails you choose to send it.

The source code is public: [github.com/hexavore-app/hexavore](https://github.com/hexavore-app/hexavore).

For any question about this policy: [contact@hexavore.app](mailto:contact@hexavore.app).

## What the app keeps on your phone

- **Your profile**: date of birth, sex (or "prefer not to say"), height, activity level, unit system.
- **Your weigh-ins and your goals.**
- **Your food log**: dishes, foods, quantities, nutrition values, time of entry.
- **The photos of your dishes**, when you confirm a scanned or analyzed meal, or when you add one to a dish yourself. They stay on the phone and go nowhere. You can remove the one attached to a dish, stop keeping them, or erase them all: **Settings → Dish photos**.
- **Your custom foods, your favorites**, and the Open Food Facts products you have already looked up.
- **Your settings**: AI provider API keys, display preferences.

This is health information. It does not leave the phone, except in the cases described below. Android's automatic backup is turned off for Hexavore: your data does not reach your Google account without you knowing either.

## What leaves your phone

Only to the destinations below, and only when you take the matching action. Every connection is encrypted (HTTPS). As with any Internet connection, the service you reach sees your device's IP address.

### Open Food Facts

[Open Food Facts](https://world.openfoodfacts.org) is an open, collaborative database of food products.

- **When you scan a product the app does not know yet**: the barcode.
- **When you tap "Search Open Food Facts"**: the text you searched for.

These requests carry no identifier: Open Food Facts cannot link them to you. The app writes nothing to its database. [Open Food Facts' privacy policy](https://world.openfoodfacts.org/privacy) applies to what it receives.

### The artificial intelligence provider you chose

Only if you saved an API key, and each time you start an analysis:

- **the photo of your meal**, scaled down to 1,024 pixels, **and/or the sentence you wrote**;
- in **deep analysis**, excerpts of the app's food catalog: food names and nutrition values, nothing personal;
- **your API key**, which identifies your account with that provider.

The request goes straight from your phone to the provider: the project sees nothing of it. The provider bills these calls to your account, and its own privacy policy applies to what it receives. Depending on your choice, that is Google (Gemini) or Anthropic.

Before the first photo is sent, the app asks for your consent and names the provider. A photo taken from within Hexavore is written to a temporary app folder, read, then deleted right away: it never enters your gallery. An image picked from your gallery is read without being modified.

If you confirm the meal, a copy of the image is kept **on your phone**, with the dish. It goes nowhere, and the section above says how to erase it.

### The emails you choose to send to the project

The app never sends an email. It prepares one, addressed to [contact@hexavore.app](mailto:contact@hexavore.app), in the email app of your choice: you read it, you can change it, and you are the one who taps "Send". It then leaves through your email app, with your sender address.

- **Reporting a wrong suggestion**, on an AI suggestion not saved yet: the exchange with the provider (what it was asked and what it answered, without your key), the photo if there is one, and the lines as the screen showed them.
- **After a crash**, at the next launch, the app offers to send the trace it kept: the classes and lines of code involved with the error message, the app and Android versions, and the phone's make and model. Whether you accept or not, the trace is then erased from the phone.

These emails are used only to understand and fix the reported defect. They are passed on to no one, kept while that defect is being dealt with, then deleted. You can ask for their deletion at any time, at the same address.

### Nothing else

- **The barcode reader works entirely on the phone.** No camera image is ever sent. The frame the code was read on is kept on the phone if you confirm the dish, as above.
- **No advertising, analytics or tracking library.** No update check, no signal sent at startup.
- **The latest exchange with the AI provider** is kept in memory only, so that it can be attached if you report a wrong suggestion. Nothing is written, it is gone when the app closes, and it only leaves in the report email you send yourself.

## Your backups

"Export my data" produces an archive that you save wherever you decide. It contains your profile, goals, weigh-ins, food log, foods, favorites and the photos of your dishes, and **never** your API keys. If it then goes to a storage service or an email, it does so through the app you chose, under that app's own rules.

## Security

- Encrypted connections (HTTPS), without exception.
- API keys are encrypted by Android's secure key store (Keystore).
- The project holds none of the app's data: none can leak from it. It only keeps the emails you sent it, while dealing with them.

## Retention and deletion

- Your data stays on your phone until you erase it.
- **Settings → Dish photos** lets you stop keeping them, and erase the ones already there.
- **Settings → Backup → "Erase all my data"** erases the log, the photos, the profile, the API keys and the other settings. Files you already exported are not touched.
- **Uninstalling Hexavore** erases everything the app stored.
- What was sent to an outside service falls under that service's policy: what Open Food Facts or an AI provider receives is kept under their terms.

## Your rights

The controller only holds data about you in the emails you sent: you can ask for a copy, a correction or their deletion at [contact@hexavore.app](mailto:contact@hexavore.app). Everything else happens in the app. For what was sent to Open Food Facts or to your AI provider, contact them. You can also lodge a complaint with your data protection authority; in France, that is the [CNIL](https://www.cnil.fr).

## Audience

Hexavore is intended for people aged 16 and over: that is the age from which the app works out targets. It is not a medical device, and it does not replace the advice of a health professional.

## Changes

This policy changes when the app changes, and at the same time. [Its full history is public](https://github.com/hexavore-app/hexavore/commits/main/confidentialite/en.md).
