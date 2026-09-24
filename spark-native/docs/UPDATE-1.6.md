# Spark 1.6 — field-specific profile pickers

The generic category/title/detail popup has been replaced by a full-screen editor with a fixed Save action and controls suited to each field.

- Category, gender, relationship and AI creator: selection lists.
- Birthday: Android calendar with future dates disabled.
- Current city, hometown and travel: interactive map, pan, pinch or +/- zoom, tap to position the pin, place search and explicit confirmation.
- Work: employer search/list, job title, employment type, start/end calendars and current-job toggle.
- Education: institution search/list, qualification selection, field of study and graduation-year picker.
- Languages, hobbies, music, shows, films, games and sports: searchable suggestions; add a custom item if absent.
- Family: select a Spark profile through search and choose a relationship. Communities: select existing Spark groups/pages through search.
- Social links: platform selector plus handle/URL. Phone: country code plus phone keyboard. Email and website: purpose-specific inputs and validation.
- Every field retains its audience and pinned state; phone/email/birthday/family default to Only me. Existing text-only entries remain editable. Legacy detail text is kept when no replacement structured information is supplied.

Work/education lists include curated starting choices and explicit search of matching public Spark profile entries. They are not Facebook's company/school database. A missing employer, school or interest can be added by name; contact details, personal job titles and URLs necessarily remain typed fields.

Location uses Android's Geocoder for explicit user-triggered place search and reverse lookup. Availability and result quality depend on the device's installed geocoding service. If unavailable, users can still choose a map pin and save coordinates. Maps require network access. No device-location permission, background location tracking or GPS upload is added.

OpenStreetMap raster tiles are requested only for the visible viewport with a dedicated identifying User-Agent, 64 MiB HTTP cache honouring server cache/expiry headers, visible attribution and HTTPS. No bulk/offline-area download is provided. Automated gesture tests disable tile downloads and use injected place results to avoid automated map browsing. Provider availability is best-effort; change providers before scaling beyond community-service capacity.

References:
- https://operations.osmfoundation.org/policies/tiles/
- https://developer.android.com/reference/android/location/Geocoder

`backend/profile-pickers-v1.6.sql` adds a bounded JSON object for dates, structured work/education values and coordinates. Coordinates must be a complete numeric pair within map bounds and belong to city/hometown/travel rows. Existing row-level audiences also protect the metadata. Additive migration is already applied to the connected project. All 75 rollback-only database checks passed (43 core + 20 discovery + 12 structured metadata).
