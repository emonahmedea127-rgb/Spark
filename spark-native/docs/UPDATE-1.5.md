# Spark 1.5 — profile editing and discovery

The previous profile editor exposed only name and bio. It now has a dedicated, scrollable screen with cover/avatar controls and collapsible sections for intro, category, personal details, links, communities, offers, work, education, hobbies, interests, travel and contact information. Each structured entry supports editing, removal, an independent audience and pinning to the intro. Multiple work/education entries are supported. Name and bio remain public. Phone, email, birthday and family default to Only me.

Profile photographs reuse the existing drag/pinch cropper and atomic photo-update feed announcements. Existing reaction people lists and keyboard-aware messaging are preserved.

People You May Know appears as horizontally scrolling portrait cards in the feed. Friends → Suggestions opens a paged list. Cards show real profile names/photos and a mutual count or public matching reason. Add friend persists a request; Remove persists a dismissal. Both actions remove the card after the server confirms success. Failed requests retain the card for retry.

The server ranks candidates using 100 points per mutually connected friend, 8 points per matching public detail category (maximum 88), and 2 points for a profile photograph. Case and outer spaces are normalised. Ties are ordered by profile ID. Self, existing friends, pending requests in either direction, bidirectional blocks and dismissals are excluded. Blocked mutual friends are not counted. No private or friends-only detail is used by either side of a public match.

This is a transparent Spark heuristic, not Facebook's proprietary or machine-learned recommendation system. Large-scale performance has not been load-tested. Ranking can shift between pages when other users change their profiles or relationships. External communities, offers and media kits are text profile entries; they do not create external service integrations. Earned badges are not fabricated.

Backend deployment: additive `backend/profile-discovery-v1.5.sql`, already applied to the connected project. Per-entry audiences are enforced by PostgreSQL RLS. A private, fixed-search-path helper is bound to the authenticated caller, returns aggregate mutual counts, and is accessible through a security-invoker RPC. Other people's raw friendship rows remain protected by their original policies.

Rollback-only database validation: 43 existing checks plus 20 new privacy/discovery checks passed. No synthetic test data is retained. Android UI tests cover camera routes, section field selection, private phone defaults/pinning, suggestion profile navigation and request/removal callbacks. They use fixtures instead of sending live friend requests.
