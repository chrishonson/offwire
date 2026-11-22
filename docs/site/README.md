# offwire.app

The site served at https://offwire.app. It is static: `index.html`, `styles.css`, `app.js`, `logo.svg`, the screenshots in `shots/` and the privacy policy in `privacy/`. There is no build step and no analytics. The only outside request is Google Fonts.

Preview locally:

```bash
python3 -m http.server -d docs/site 8000
```

It is hosted on Vercel in the project `offwire`, which is not connected to this repository. Deploy by uploading this folder: `vercel deploy docs/site` makes a private preview, and `vercel deploy docs/site --prod` updates the live site.

`privacy/privacy.md` is the source text of the privacy policy and `privacy/index.html` is the page.
