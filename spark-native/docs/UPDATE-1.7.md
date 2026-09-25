# Spark 1.7

Profile redesign: large cover and overlapping circular avatar, centered name and real category, clickable follower/following/post counts, audience dashboard, friends row, grouped About details, photo highlights and post composer. Profile posts can load beyond the initial 40. Existing crop, media viewers and post reactions remain in use.

Name and Bio have independent full-screen editors, validation, save errors, discard confirmation and keyboard-aware layout. Each save patches exactly one profile column.

Connection rules requested for Spark: sending a pending friend request implies a one-way follower connection. Accepting creates mutual followers and friends. Accepted friends are excluded from displayed Following. Explicit follows are deduplicated and retained independently after cancelling a request or removing a friend. Blocking hides connections in either direction. Counts/lists derive from current records, including existing friendships; no backfill or duplicate follow writes.

New public RPCs return counts or paginated public profile cards. The private helper has a fixed search path, requires auth.uid(), filters viewer/target blocks and never returns raw request records. Existing friendship RLS is unchanged. Post counts respect post RLS. Connection lists are public to signed-in users except blocked users.

Highlights select one existing photo post each; the post's audience and deletion govern its highlight. Owners can add/remove highlights; removal does not delete the source post. A profile displays up to 30 latest highlights. Highlights aren't an implementation of all Facebook collection features, and Spark does not claim Facebook's proprietary ranking or verification system.

Apply backend/profile-connections-v1.7.sql and backend/profile-highlights-v1.7.sql after previous upgrades. Run rollback-only verify-profile-connections.sql and verify-profile-highlights.sql. APK versionCode 8 / versionName 1.7.0. Android UI and build verification recorded separately after CI completion.
