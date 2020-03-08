# pics-android

A picture gallery app for Android.

## Login

Log in with your Google account. 

The desire is to implement Google login via Amazon Cognito, but since it [appears](https://stackoverflow.com/a/59580105)
Cognito does not support switching Google accounts, Cognito is not used for now.

### SHA1 certificates

If you get a code 10 DEVELOPER_ERROR when logging in, make sure the SHA1 hash of your debug and
release certificates are added to an Android OAuth 2.0 Client ID in the 
[Google Developer Console](https://console.developers.google.com/apis/credentials). The Android
app should nevertheless use the Web OAuth 2.0 Client ID, not the Android one.
