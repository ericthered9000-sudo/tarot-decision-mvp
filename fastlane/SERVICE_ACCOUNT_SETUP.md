# Fastlane Service Account Access — Setup Guide

## When you need to grant upload access for a new app:

1. **Copy the service account email:**
   `fastlane-play-store@keystone-play-store-492616.iam.gserviceaccount.com`

2. **Play Console → Users and permissions → Invite new users**
   Paste the email above.

3. **Grant app permissions:**
   - Add app → select the target app
   - Check at minimum:
     - View app information and download bulk reports
     - Create, edit, and delete draft releases
     - Release to production, exclude devices, and use Play App Signing

4. **API access page** — Verify Google Play Developer API is on, service account shows Active

5. **First upload:** For a brand new app, manually upload the first AAB through the web UI once before Fastlane can take over.

6. **Permissions latency:** Google can take 5-10 minutes to propagate. Wait and retry.

## Deploy command:
```bash
ssh hermes "cd ~/projects/decision-tarot && KEYSTORE_PASSWORD='tar0tK3yst0n3!2026' fastlane android upload"
```
