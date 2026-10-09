# ChromaSeal

A phone-based second opinion for field colour drug tests, built for the Smart India Hackathon 2026 (problem statement SIH26231).

Field kits are read by eye, which makes the result depend on the light and on the person looking. ChromaSeal leaves the kit alone. The officer photographs the strip next to a printed reference card. The app corrects for the lighting, measures the colour, and answers Positive, Negative or Inconclusive. It also keeps a tamper-evident record of every test.

**Every result is presumptive. This is not a laboratory test, and it is a prototype. No real drugs or reagents are used anywhere in the project.**

The code, setup steps, the reasoning behind our design choices, and an honest account of its limits are in [`chromaseal/README.md`](chromaseal/README.md). Slide material and references for the SIH submission are in [`chromaseal/docs/`](chromaseal/docs/).

Quick start:

```
cd chromaseal
pip install -r requirements.txt
flask --app app create-user admin --role admin
flask --app app run
```
