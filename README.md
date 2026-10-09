# ChromaSeal

A phone-based second opinion for field colour tests. Built for the Smart India Hackathon 2026 (problem statement SIH26231).

## The problem

Field drug-test kits work by colour. You add a reagent, watch it change, and compare the result to a chart. A person decides what they are looking at, and that decision depends on the light, on their eyes, and on how long they've been on shift. Nothing is recorded afterwards that proves the test happened or what it showed. Published reviews and audits report plenty of false positives from these kits. Misreading the colour is one cause among several (see `docs/references.md`).

Our idea is to leave the kit and its chemistry alone and add a second look. The officer photographs the strip next to a printed reference card. The software corrects for the lighting, measures the colour, and says Positive, Negative or Inconclusive. Then it saves a record that is hard to quietly change afterwards.

## What this is not

- It is **not a lab test**. Every result is presumptive. It cannot fix cross-reactivity, where a legal substance gives the same colour as an illegal one. That is a chemistry problem, not a reading problem.
- It is a **prototype**. We have not validated it on real casework.
- **No real drugs or reagents are used in this project, ever.** Marquis reagent is concentrated sulfuric acid, and testing real substances needs licences we don't have. Everything here is tested with printed and coloured mock swatches.

## How it works

1. The officer signs in, calibrates the card (more on that below), then photographs the strip sitting in the slot on the printed card.
2. A quality check looks for blur, glare and overexposure. A bad photo is rejected with a reason ("too blurry, retake") and is never guessed at.
3. The app finds the card by spotting its row of colour patches, and straightens the photo.
4. Six of the card's nine patches (three greys, plus red, green and blue) show what the light did to the colours. The app fits a correction that undoes it. If the fit is poor, the photo is rejected.
5. The corrected strip colour is compared with two references: the *unreacted* colour and the *target* (positive) colour. Close to the target means Positive. Close to the unreacted colour means Negative. Far from both, or too close to call, means Inconclusive.
6. The result is saved with the time, GPS (if the phone allows it), operator, a hash of the photo, and a hash chaining it to the previous record.

## Why we built it this way

**A reference card in the same photo, not a light box.** A light box is extra hardware that officers would have to carry and buy. A card is a piece of paper. Because it sits in the same picture as the strip, whatever light was there is in the photo and can be measured.

**Several known patches, not just one white.** A white patch tells you how bright and how tinted the light is. It doesn't tell you how one colour bleeds into another under coloured light. The red, green and blue patches are there for that. Reagent colours alone (pale, purple, near-black) don't cover enough of the colour range for the correction to be stable.

**Two reference colours, not one.** If we only asked "is it close to purple?", a strip that turned some completely different colour would be called Negative just because it isn't purple. Comparing against both the unreacted and the target colour lets the app say "this is neither" and answer Inconclusive.

**Inconclusive and Invalid Capture exist on purpose.** A confident wrong answer is worse than no answer. The app would rather ask for a retake than force a Positive or Negative.

**A hash chain for the records.** Each record includes the hash of the one before it, so editing or deleting an old record shows up when you verify. This makes tampering *detectable*, not impossible. Someone with full access to the database could rewrite everything after the change. Printing the record hash on the PDF report (so it lives outside the database) is our workaround for now.

**Flask, SQLite and plain HTML/JS.** It is small enough to read in an afternoon and runs on a laptop with no server to look after. That's the right size for a prototype.

**Individual logins, and officers see only their own records.** Evidence needs to be tied to a person. The operator on each record is whoever was signed in, and can't be typed in.

## The part we want to be upfront about: the printed card

The proper way to do this is with a **manufactured colour checker**. The X-Rite ColorChecker is the best-known one. Its patches are made and measured by the manufacturer, so the software knows exactly what each patch should look like. In a real deployment you'd use one of those. For the reagent colours you would use values measured from real reactions in a lab, with a spectrophotometer.

We print our own card on an ordinary printer instead. It costs almost nothing and anyone can reproduce it, which suits a hackathon. The price is reliability, and here is where it shows up:

- **Printers don't print the colour you ask for.** In our own tests a red patch came out pink and a black patch came out dark slate grey. So we don't actually know the card's true colours until we measure them. That's what calibration is for: each printed card is photographed in daylight, and the measured colours become that card's "true" values.
- **That measurement is only as good as the light it was taken in.** If the calibration photos have a blue cast, the cast becomes part of the card's "truth" and every later result inherits it.
- **The reagent references are stand-ins.** The "unreacted" and "target" colours come from patches 7 and 9 on the same printed card, so they are printed look-alikes, not the colour of a real reaction. A printed ink and a real chemical reaction can match under one light and drift apart under another (metamerism). We cannot calibrate against real reacted reagent because we are not using any. This is the biggest gap between this prototype and something you could trust in the field.

| | Factory colour checker | Our printed card |
|---|---|---|
| Patch colours | Measured by the manufacturer | Unknown until we calibrate each print |
| Cost | Real money | Close to nothing |
| Calibration step | Not needed | Needed for every printed card |
| Reagent reference colours | From real, lab-measured reactions | Printed approximations |
| Reliability | High | Lower, and depends on the printer, paper and calibration light |
| Good for | Deployment | Demos and prototyping |

In practice this means results are most trustworthy when the card was calibrated in good daylight, when the test strip is made from the same ink as patch 9, and when the thresholds have been tuned on real photos. Right now the thresholds have not been.

## Calibrating the card

Do this once per printed card, and the app asks again at every sign-in.

1. Print `printable_card.png` on matte paper (`python make_printable_card.py` regenerates it).
2. Photograph the **empty** card three or more times in even daylight: by a window, no direct sun, no flash, no glare, card flat. A flatbed scanner works even better.
3. Open **Calibration**, upload the photos, check the measured colours and press *Save and apply*.

The result is saved in `card_calibration.json` along with who saved it and when. `python calibrate_card.py photo1.jpg photo2.jpg ...` does the same from the command line.

Why every sign-in? Cards get scuffed or replaced, and a different print is a different card. It costs a minute and it's cheap insurance for a prototype. Set `CHROMASEAL_CAL_EACH_LOGIN=0` if you want to turn it off. One catch: the calibration is **shared by everyone using the app**, so if two officers are signed in, whoever calibrates last changes the colours for both.

Patch 8 (intermediate purple) isn't used for the correction or the decision. It's there as a visual reference for an in-between shade, and its brightness helps confirm the card was found correctly.

## Running it

```
pip install -r requirements.txt
flask --app app create-user admin --role admin       # asks for a password
flask --app app run --host 0.0.0.0
pytest
```

There's deliberately no default password. For a quick demo, `flask --app app seed-demo` creates an admin and an officer with random passwords, printed once. `python synth.py` writes a set of synthetic test photos into `demo_images/`.

**Using it from a phone:** the live camera guide and GPS only work over HTTPS (or on localhost). On plain `http://` the photo picker still works and GPS is saved as unavailable. For a demo, the easiest route is a tunnel such as `cloudflared tunnel --url http://localhost:5000`, which gives you a temporary `https://` address.

**Settings** (environment variables):

| Variable | What it does |
|---|---|
| `CHROMASEAL_DEMO=1` | Enables the tamper-demo button (admins only). Leave unset in real use. |
| `CHROMASEAL_SECRET` | Session secret. If unset, one is generated into `instance/secret_key`. |
| `CHROMASEAL_HTTPS=1` | Marks the sign-in cookie as Secure. Set it when served over HTTPS. |
| `CHROMASEAL_CAL_EACH_LOGIN=0` | Stops requiring calibration at every sign-in. |
| `CHROMASEAL_DB` | Path to the SQLite database (default `chromaseal.db`). |
| `CHROMASEAL_CARD_CAL` | Path to the calibration file (default `card_calibration.json`). |

**Accounts and security:** passwords are stored hashed (PBKDF2-SHA256) with a 10-character minimum. Five failed sign-ins lock that ID for 10 minutes. Sessions expire after 30 minutes idle. Every form carries a CSRF token. New accounts and admin resets force a password change at next sign-in. Admins manage accounts at `/admin/users`. Records made before accounts existed keep their old free-text operator and are visible to admins only.

**Records, log and reports:** every test is saved in `chromaseal.db` (SQLite), including rejected photos. The photos themselves are files in `captures/`. The log at `/log` filters by operator, outcome and date. Each record has a verify page and a PDF report with the GPS location, distances, both images, the hashes and the verification result.

## What has actually been tested

- The automated tests (`pytest`) use synthetic photos. They check that the same strip gets the same result under five different lights, that the card is found when tilted, rotated, on a dark table or a bright wooden one, and that the hash chain catches edits and deletions.
- We've also tried card detection, calibration and a few mock strips on real phone photos of a home-printed card. That showed up several problems (the missing black border on real prints, the white patch clipping in bright photos) which are now fixed.
- That is a long way from a validation study. **No accuracy figure has been measured, and none is claimed.**
- The decision thresholds are placeholders we picked: 20 ΔE maximum distance, 5 ΔE minimum margin, 5 ΔE fit error, blur limit 100. They live in `kit_profiles.py` and `colour_pipeline.py`, and they need setting from real test photos.

## Known limitations

- Cross-reactivity isn't fixed (see above).
- The reagent reference colours are printed approximations, and borderline cases go to Inconclusive for that reason.
- The lighting correction is a linear approximation. Extreme lighting is rejected, not corrected.
- A clipped (overexposed) calibration patch is left out of the fit. Two or more clipped patches, or a clipped strip, means retake.
- Calibration is shared between users, and records don't yet store which calibration was active when they were made.
- The hash chain shows tampering, it doesn't prevent it.
- It runs on Flask's development server. Real deployment needs a production server, HTTPS and proper backups.
- No two-factor sign-in and no email password reset. An admin resets passwords.

## What we'd do next

1. Use a manufactured colour checker for the calibration patches.
2. Take the unreacted and positive colours from real reactions, measured by a lab, instead of from printed patches.
3. Tune the thresholds on real photos across many lights and phones.
4. Store a calibration version in every record, and cover it with the hash.
5. Anchor the latest hash outside the database.
6. Deploy behind HTTPS on a proper server.

## What's in the repo

| File | Purpose |
|---|---|
| `app.py` | The web app and its routes |
| `auth.py` | Sign-in, roles, CSRF, lockout |
| `colour_pipeline.py` | Quality check, card finding, lighting correction, decision |
| `reference_card.py` | The card layout and its patch colours |
| `kit_profiles.py` | Target and unreacted colours and thresholds for a kit |
| `calibrate_card.py` | Measures a printed card |
| `hashing.py`, `db.py` | The hash chain and SQLite storage |
| `report.py` | PDF reports |
| `make_printable_card.py`, `synth.py` | The printable card and synthetic test photos |
| `templates/`, `static/` | Pages and browser code |
| `test_*.py`, `conftest.py` | Automated tests |
| `docs/` | Slide material for the SIH submission: technical approach, references |
