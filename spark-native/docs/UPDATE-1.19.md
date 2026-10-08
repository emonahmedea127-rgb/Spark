# Spark 1.19

- Reel ranking now combines freshness, engagement, average watched proportion and completion. Each viewer contributes one average to quality; repeated identical plays do not increase its weight. Small samples are discounted. Raw viewing records remain private to the creator.
- Existing post privacy, blocks and friends-only Home feed rules remain enforced.
- Admin Menu now has Reports & moderation, with pending/reviewed lists, reason, reporter and post preview.
- Admin can dismiss reports, hide reported posts/reels/stories and restore hidden content. Changes record moderator and time. Hide is reversible, not deletion.
- Profile, listing and message reports are reviewable/dismissible; account suspensions and their respective takedown actions are not implemented.
- Automated backend tests cover ranking order, repeat-play weighting, raw-view privacy, admin-only moderation and restore.

Remaining production dependencies: push notification service, authenticated TURN, real-device checks and load tests. Google/phone login, account suspension, appeals and a complete moderation audit history remain future work. Monetization is excluded.
