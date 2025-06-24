# Team 5 - OfflineEuro Module
During the available timeframe of the Blockchain Engineering course, team 5 worked on the OfflineEuro module. Our contributions are the following:

## 1 - Secured Variable Value Withdrawals
A variable amount of money can be withdrawn from the bank. Now, users are not limited anymore to only withdrawing a token with a value of €1. However, this also means that malicious users could now modify the actual value of the token.

To mitigate this, the amount is added to a new composite hash, alongside the coin's withdrawal timestamp and serial number. 
The hash makes forging a new amount value (e.g. by stealing one from a previous token) impossible. The hash is then signed by the issuing bank, and included in the token.

To ensure an attacker cannot forge all details by providing a fake bank public key, the token now includes a TTP's signature over the issuer's public key.

## 2 - EUDI-Based Secure User Registration
A more secure user registration process has been devised by connecting the registration to the EUDI wallet:
* The user first creates an EUDI account and inputs their necessary data to the app through the first option: `PID`;
* The user then begins the registration process in the home page and is prompted to verify their credentials;
* The TTP creates a request for the data it needs and sends it to the user (currently requests only the "legal name" of a user);
* The user consents to share a Verifiable Credential that currently only contains the legal name of the user;
* After the user shares their data, the TTP receives a confirmation and checks whether the requested data actually was shared and is valid. The user must go back manually to the superapp, as they will not be redirected automatically.
* If successful, the user is redirected to the user home page, where they can perform transactions.
* If not, the user is not redirected to the user home page.

## 3 - Improved User Frontend
The user frontend was improved, and additional functionality was added. A user may now select what token they want to spend, and upon doing that, have to pick a specific user from their peer list to send the token to. This simplifies the interface, while still allowing the user to perform all existing actions. The token can also be deposited to a specific bank, where the user will have to choose a bank from the available ones to deposit their token to. 

For debugging purposes, it is now easier to see what tokens you can doubly spend. Tokens that may be doubly spent are tokens that a user has received and spent once already. They are now separated from the tokens a user has that were not spent yet and reside in a section that can be toggled to show them or hide them back. Just as the normal tokens, they may be doubly spent to a specific user, or deposited to the bank. 

Lastly, the user interface is now scrollable. Since not every token is the same anymore, with just a couple of transactions the user's home page might fill up pretty quickly, and thus they need the ability to scroll through it.

## 4 - Transaction Fees and Depreciation

Since a doubly spent token can only be detected when it is deposited, it can cause losses to many users if kept in the economy for too long.

To discourage users from holding on to old, undeposited tokens, we have implemented two separate features:

- Transaction Fees: Transfering a coin more than 3 times incurs a flat transaction fee. This discourages both old tokens and penalizes tokens that have been rapidly transferred between multiple users in a short amount of time.
- Depreciation: Tokens linearly lose their value over time. This is done to ensure that depositing a token now is preferable to holding old, possibly double-spent, tokens.

In order to ensure that this mechanism cannot be forged, we have ensured that all properties that are needed to calculate a coin's present value have been made cryptographically secure:

- The transaction fee is based on the length of the Groth-Sahai proof chain.
- The initial timestamp is included in the serial hash and is signed by the bank at withdrawal.



### Requirements:
- A browser on which adding your user data to the EUDI wallet app works (we had problems with Quant). 
- The EUDI wallet apk (the 25.07 release, found [here](https://github.com/eu-digital-identity-wallet/eudi-app-android-wallet-ui/releases/tag/Wallet%2FDemo_Version%3D2025.05.27-Demo_Build%3D27))
