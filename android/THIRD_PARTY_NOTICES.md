# Third-party resources

* **Asta Sans** — [Google Fonts source](https://github.com/google/fonts/tree/main/ofl/astasans). SIL Open Font License 1.1; license bundled in `app/src/main/assets/licenses/AstaSans-OFL.txt`.
* Map roads and area geometry: **© OpenStreetMap contributors**, [ODbL attribution and license](https://www.openstreetmap.org/copyright). A simplified Seoul-area extract is bundled in `app/src/main/assets/seongsu-map.json`; demonstration routes were calculated over this geometry. UI attribution remains visible.
* Home photos from Unsplash: [forest](https://unsplash.com/photos/photo-1441974231531-c6227db76b6e), [café](https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb), [mountains](https://images.unsplash.com/photo-1464822759023-fed622ff2c3b). [Unsplash license](https://unsplash.com/license). These photographs are aesthetic backgrounds, not verification of any depicted POI.
* AndroidX / Jetpack Compose — Apache License 2.0; Kotlin — Apache License 2.0; Gson — Apache License 2.0. Gradle Wrapper is distributed under Apache License 2.0.
* Official Pleos artifacts are resolved from Hyundai's [public Maven repository](https://nexus-playground.pleos.ai/repository/maven-releases/) and remain subject to their respective terms. No SDK binaries, account credentials or signing secrets are checked in.

Official reference: [Pleos Connect emulator setup](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator), [development guide](https://appdevchallenge.auto-hmg.io/guide/development).

## Parts 2–3 additions

* Festival, food and beach sample images: [festival](https://images.unsplash.com/photo-1492684223066-81342ee5ff30), [food](https://images.unsplash.com/photo-1511690743698-d9d85f2fbf38), [beach](https://images.unsplash.com/photo-1507525428034-b723cf961d3e), under the Unsplash license above. These are illustrative images, not business photographs.
* Korea country geometry: [Natural Earth](https://www.naturalearthdata.com/about/terms-of-use/), public domain. Demo road geometry was computed from OpenStreetMap data using [OSRM](https://project-osrm.org/docs/v26.4.0/http). Requests happen during asset preparation; the app does not contact the public routing server.
* **해운대 오션카페** and other Picks venues are demonstration fixtures. Rating, visit count, recommendations, distance and photographs are sample UI, not live business data.
* Demo speech clips are synthetic speech produced locally for the supplied scenario, not recordings of real participants.
* The small ellipse/H badges are an illustrative Hyundai-style UI mark, not a claim of partnership, certification or access to actual Hyundai analytics.
* `connect_light_dock` is an illustrative Connect-S Light dock recreation for the signed demo chrome, not a system screenshot from a production vehicle.

## Detailed demonstration basemaps

The grayscale Seoul metropolitan and Busan maps in `app/src/main/assets/maps/` are our own cartographic renderings of OpenStreetMap geographic data, extracted through Overpass on 2026-11-15. They are offline demonstration maps, not Hyundai's proprietary map tiles or live traffic. Attribution remains visible in the app. The underlying derived geographic extracts are distributed in `map-data/*.json.gz` under [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/); see [OpenStreetMap copyright](https://www.openstreetmap.org/copyright). `scripts/render_demo_maps.py` regenerates the renderings from these extracts. No map API key or tile service is required at runtime. Route geometry is demonstration data prepared with OSRM and does not provide live navigation or traffic guarantees.
