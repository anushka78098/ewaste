# REST API

Base URL `http://localhost:8080`. All bodies are JSON. Authenticated routes need `Authorization: Bearer <token>`
(from `POST /api/auth/login`). Errors: `{"error": "message"}` with status 400 (invalid input), 401 (not logged in),
403 (not allowed), 404, 405, 413 or 500.

## Public
| Method & path | Body | Result |
|---|---|---|
| GET `/api/health` | | `{status, store}` |
| GET `/api/meta` | | categories, conditions, decisions, statuses, `pointValue`, `maxPointDiscount` |
| POST `/api/auth/register` | `username, password, fullName, email, phone?, address?` | `{user}` |
| POST `/api/auth/login` | `username, password` | `{token, user}` - after 5 failed attempts the username is locked for 5 minutes (HTTP 429) |
| GET `/api/track/{code}` | | public item tracker for a code like `EW-2026-00012`: `{trackingCode, categoryLabel, statusLabel, plan, pickupDate, steps[{label, done}]}` - no personal data; 404 for unknown codes |
| GET `/api/passport/{code}` | | public Circularity Passport for listed/sold items: item facts, score/breakdown, estimated impact/materials, privacy-safe journey; 404 otherwise |

## Any signed-in user
| Method & path | Body / query | Result |
|---|---|---|
| POST `/api/auth/logout` | | `{ok}` |
| GET / PUT `/api/me` | PUT: `fullName?, email?, phone?, address?, emailOptOut?` | user (+ `unread` on GET) |
| POST `/api/me/password` | `oldPassword, newPassword` | `{ok}` |
| GET `/api/me/impact` | | `{points, pointsValue, kgDonated, co2Saved}` |
| GET `/api/leaderboard` | | top 10 donors |
| GET `/api/me/dashboard` | | `{points, pointsValue, kgDonated, co2Saved, itemCount, tier, badges[], pipeline, monthlyLabels[], monthlyKg[], equivalents, campaigns[], recent[]}` |
| GET `/api/me/market/dashboard` | | `{buyer:{orders,totalSpent,recent[]}, seller:{activeListings,soldItems,salesTotal,items[]}}` |
| GET `/api/me/certificate` | | printable HTML impact certificate (sent as an attachment) |
| GET `/api/me/support` | | the signed-in user's private support conversation; marks admin replies as read |
| POST `/api/me/support` | `text` (1–4000 characters) | send a private message to the administrators |
| GET `/api/campaigns` | | active collection drives with live progress |
| GET `/api/eco/challenges` | | challenges visible to the user, verified progress, earned achievements, and weekly/monthly challenge leaderboards |
| POST `/api/eco/challenges/{id}/enroll` | | enroll the signed-in user; server calculates progress and awards any earned bonus |
| GET `/api/eco/leaderboard` | `?period=WEEKLY\|MONTHLY` (default `WEEKLY`) | rankings by challenge bonus points earned in the current week/month |
| POST `/api/decision/report` | `category, ageYears, condition, weightKg?, estimatedResaleValue?, repairCost?, recyclingValue?, hazardous?` | explainable non-persisted decision report with action, financial comparison, reasons, alternatives, warnings, environmental factor estimate and input-completeness quality |
| POST `/api/items` | `name, brand?, category, ageYears, weightKg, condition, description?` | item (with `suggestedDecision`, `estimatedValue` and `decisionReport`) |
| GET `/api/items/mine` | | own items |
| GET `/api/items/{id}` | | item with `history` (owner or admin only) |
| GET `/api/market` | `?q=&category=&maxPrice=` | active listings (includes seller name and optional `photoUrl`) |
| POST `/api/market/{listingId}/buy` | `points?` | order |
| POST `/api/market/{listingId}/photo` | `attachmentName, attachmentType, attachmentBase64` | add/replace a photo; listing owner only; PNG/JPEG, max 4 MB |
| GET `/api/market/{listingId}/photo` | | photo bytes; active listing, owner, or buyer only |
| GET `/api/messages` | | conversations involving the signed-in user |
| GET `/api/market/{listingId}/messages` | `?with={otherUserId}` (required for seller inbox) | conversation messages |
| POST `/api/market/{listingId}/messages` | `text?, recipientId?, attachmentName?, attachmentType?, attachmentBase64?` | send a message; buyer can initiate, seller can reply; PNG/JPEG/PDF attachment up to 4 MB |
| GET `/api/market/{listingId}/messages/{messageId}/attachment` | | attachment bytes; sender or recipient only |
| GET `/api/orders/mine` | | orders |
| GET `/api/notifications` | | newest first |
| POST `/api/notifications/read` | | `{ok}` |

## Registered recyclers
| Method & path | Body / query | Result |
|---|---|---|
| GET `/api/auctions` | | auctions open for bidding plus auctions in which this recycler participated; includes closing date, status, winning amount (when awarded), and the signed-in recycler's bid |
| POST `/api/auctions/{auctionId}/bids` | `amount, proposedCollectionDate (yyyy-mm-dd), recyclingMethod` | place one bid per auction; amount must meet the published minimum |

## Admin only
| Method & path | Body / query | Purpose |
|---|---|---|
| GET `/api/admin/items` | `?status=&q=` | search all items |
| POST `/api/admin/items/{id}/approve` | `decision, pickupDate (yyyy-mm-dd), note?` | approve + schedule pickup |
| POST `/api/admin/items/{id}/reject` | `reason` | reject |
| POST `/api/admin/items/{id}/collect` | | mark collected, award points |
| POST `/api/admin/items/{id}/decision` | `decision` | change plan (collected items) |
| POST `/api/admin/items/{id}/refurbish/start` | `repairCost` | start repair |
| POST `/api/admin/items/{id}/refurbish/complete` | | finish repair, ready to list |
| POST `/api/admin/listings` | `itemId, price` | publish to market (notifies users) |
| PUT `/api/admin/listings/{id}` | `price` | reprice (a drop notifies users) |
| DELETE `/api/admin/listings/{id}` | | remove listing |
| GET `/api/admin/auctions/eligible` | | collected items with a RECYCLE / HAZARDOUS_DISPOSAL plan that are not already reserved |
| GET `/api/admin/auctions` | | auctions with status, closing time, bid count and winning bid |
| POST `/api/admin/auctions` | `itemId, minimumBid, closesAt (ISO local date-time)` | publish an eligible recycling item to recyclers |
| GET `/api/admin/auctions/{auctionId}/bids` | | compare bids, including recycler, amount, proposed collection date, method, timestamp and status |
| POST `/api/admin/auctions/{auctionId}/award` | `bidId` | select the winning bid; competing bids are rejected and both winning and unsuccessful recyclers are notified |
| POST `/api/admin/auctions/{auctionId}/cancel` | | cancel an open or closed auction and reject/notify its pending bidders |
| GET `/api/admin/auctions/{auctionId}/history` | | append-only auction activity audit history |
| GET `/api/admin/recycling` | | queue counts + batch history |
| POST `/api/admin/recycling/queue-all` | | queue all recycle-planned items |
| POST `/api/admin/recycling/batches` | `partner, hazardous (bool)` | ship a batch |
| GET `/api/admin/users` | | users with item counts |
| POST `/api/admin/users/{id}/active` | `active (bool)` | (de)activate |
| POST `/api/admin/users/{id}/role` | `role` (`USER` or `RECYCLER`) | grant or remove recycler access; admin accounts cannot be changed |
| POST `/api/admin/users/{id}/points` | `points` | bonus points |
| GET `/api/admin/support` | | private support inbox with latest message and per-user unread count |
| GET `/api/admin/support/{userId}` | | view or start a user's thread; marks user messages as read |
| POST `/api/admin/support/{userId}` | `text` (1–4000 characters) | send a private reply or initiate a conversation |
| POST `/api/admin/announce` | `message` | notify all users (in-app + email) |
| GET `/api/admin/mail` | | email status: `enabled, host, port, security, from, sent, failed, queued, lastError` |
| POST `/api/admin/mail/test` | `to` | send a test email now (400 with the SMTP error if it fails) |
| GET `/api/admin/stats` | | all-time dashboard numbers |
| GET `/api/admin/analytics` | `?days=7..180` | trends and breakdowns for the dashboard: `kpis[{key,label,unit,value,previous}]`, `labels[]`, `submitted[]`, `kgIn[]`, `revenue[]`, `recycledKg[]`, `byCategory`, `outcomes`, `funnel`, `materials`, `topDonors`, `alerts[{level,text,tab}]` |
| GET `/api/admin/campaigns` | | all drives (active, finished, ended) with progress |
| POST `/api/admin/campaigns` | `name, description?, goalKg, endDate (yyyy-mm-dd)` | start a collection drive (notifies all users) |
| POST `/api/admin/campaigns/{id}/end` | | end a drive |
| GET `/api/admin/eco/challenges` | | list challenges with enrollment and completion counts |
| POST `/api/admin/eco/challenges` | `title, description?, type, period, targetValue, rewardPoints` | create a 7-day weekly challenge or a monthly challenge running through the current month's last day |
| POST `/api/admin/eco/challenges/{id}/end` | | end a challenge early |
| GET `/api/admin/export` | | CSV of all items |

Enums: categories `MOBILE TABLET LAPTOP DESKTOP TELEVISION APPLIANCE PRINTER BATTERY ACCESSORY OTHER`;
conditions `WORKING MINOR_FAULT MAJOR_FAULT DEAD`; decisions `REUSE REPAIR_REUSE RECYCLE HAZARDOUS_DISPOSAL`.
Roles are `ADMIN`, `USER` and `RECYCLER`. Registration always creates a `USER`; an admin must grant recycler access.
Recycler auctions have status `OPEN`, `CLOSED`, `AWARDED` or `CANCELLED`. The closing date-time is interpreted in the server's local time.
An open auction is transitioned to `CLOSED` when the next API request arrives at or after its closing time. Each recycler may bid once per auction;
the amount must be positive and no less than the published minimum, and the proposed collection date cannot be in the past.
Open, closed and awarded auctions reserve their item from the regular recycling queue and batch workflow. Cancelling an auction releases the item.
Item lifecycle: `SUBMITTED → PICKUP_SCHEDULED → COLLECTED → (REFURBISHING →) LISTED → SOLD`, or `COLLECTED → QUEUED_RECYCLE → RECYCLED`; `SUBMITTED → REJECTED`.

Eco challenge types are `DONATE_KG`, `DONATE_REUSABLE`, `VERIFIED_RECYCLING` and `COLLECTION_DRIVE`. Challenge progress is computed on the server from received item states, listed/sold reuse items, recycling batches and existing collection drives. Challenge bonus points are written to the regular user point balance and an append-only reward history; users cannot submit progress or award records. A reward is issued at most once per user/challenge. Milestones, enrollment, completion and newly earned achievements generate in-app notifications. Weekly and monthly leaderboards rank challenge bonus points only, not regular marketplace rewards.

The decision report is rule-based, not an ML probability. `estimatedResaleValue` is the functional resale estimate (before repair); `resaleValueAsIs` applies the condition factor and `repairNetValue` subtracts repair cost. Omitted financial values are estimated from category/condition/age, category material fractions and the configured rates. `inputCompletenessPercent` and `qualityIndicator` describe known-input completeness only. `environmentalImpactEstimateKgCo2` uses `weight × Category.co2PerKg` once as an indicative category-factor estimate; it is not an avoided-emissions claim and is never added to realized analytics.

Category-specific useful life, minimum remaining life, maximum repair-cost ratio, estimated repair-cost ratio and recycling value per kg default from the engine policy. To override defaults, copy `config/decision.properties.example` to `config/decision.properties` and restart the server; `EWASTE_DECISION_FILE` selects another properties file. Keys use `decision.<CATEGORY>.<threshold>`. Ratios are fractions from 0 to 1.

The Circularity Passport score is deterministic and explainable (condition /35, remaining category-guideline useful life /25, reuse pathway /30, recorded refurbishment /10). CO2 and material values are estimates derived from the existing category factors, not measurements or guarantees. The public passport intentionally omits donor/buyer identities and private item-history notes.

Uploads are sent as base64 JSON fields, validated by the server, stored in the application's database, and served with `X-Content-Type-Options: nosniff`. Listing photos must decode as PNG/JPEG images; chat attachments accept PNG/JPEG/PDF. Files are limited to 4 MB and image dimensions to 6000 x 6000 pixels.
