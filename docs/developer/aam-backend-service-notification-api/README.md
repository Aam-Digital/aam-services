## aam-services with notification backend

Run this docker-compose file to start the notification backend service locally

### Setup

1. Download the `firebase-credentials.json` from the firebase interface.
2. Encode it as base64
  - `base64 -i firebase-credentials.json` will print the encoded file to the console
3. Copy the output
4. Create a new file, based on the `secrets.env.example`
  - `cp secrets.env.example secrets.env`
5. Edit `secrets.env` with an editor of your choice and replace the placeholder with the base-64 output you just copied
  - ```
      NOTIFICATIONFIREBASECONFIGURATION_CREDENTIALFILEBASE64=<base-64-encoded-firebase-credential-file>
      ``` 
6. Run `docker compose up -d`
