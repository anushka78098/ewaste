# E-Waste Management System - full stack, pure Java

A client/server application for managing electronic waste: users hand in devices, the **admin** decides whether each
is **reused, repaired or recycled**, reusable devices are sold in a **market**, and users are kept informed with
notifications, reward points, dashboards and optional emails.

```
 Swing client  ─┐
                ├──HTTP + JSON──▶  Java REST backend  ──▶  SQLite or PostgreSQL
 JavaFX client ─┘  (bearer token)  (com.ewaste.server)      (or file-backed fallback)
                                          │
                                          └──SMTP──▶ email to users (optional)
```

* **Two desktop clients, same backend:** a **Swing** client (`src/.../client`, Java2D charts) and a **JavaFX** client (`src-fx`, built-in charts). Both offer every screen.
* **Updated workspace design:** role-aware blue side navigation, active-page feedback, live account indicators and colorful KPI cards across both clients.
* **Backend:** REST API on the JDK's built-in HTTP server, salted password hashes, token sessions, login lockout.
* **Database:** SQLite through JDBC by default, optional PostgreSQL through JDBC, or a file-backed store if no driver is installed.
* **Email:** optional notifications through any SMTP server, written in pure Java.
* **Marketplace demo:** on a fresh install, the server creates 100 reusable demo listings. Buyers and item donors have separate marketplace dashboards; donors can add listing photos and buyers can message donors with image/PDF attachments.
* **Private admin support:** users and recyclers can contact administrators through a private support inbox, separate from listing-based buyer/seller messages.
* **Circularity Passport:** every marketplace item has a public, shareable product passport with an explainable 0-100 circularity score, modeled CO2 impact, estimated material composition, and a privacy-safe lifecycle timeline.
* **Recycler reverse auctions:** admins can auction collected recycling-plan items to registered recyclers, compare bids and award a recycler; audit history, bid notifications and the existing recycling queue are integrated.
* **Explainable decision reports:** category/age/condition-based estimates compare reuse, repair-and-reuse, recycling and hazardous disposal, with transparent financial and environmental estimates and configurable category thresholds.
* No Maven, Gradle, Tomcat or Spring. Just a JDK (plus the JavaFX jars if you want the JavaFX client).

**Honest status:** the REST API, Swing client, and SQLite-backed API tests are covered by the automated suite. PostgreSQL support is implemented
but requires verification against a live PostgreSQL server before production use. The JavaFX client compiles with JavaFX 21 but has not had a
separate GUI smoke test. Docker and real-provider SMTP/TLS still need end-to-end verification; see section 8.

---------------------------------------------------------------------------------------------------

## 1. Requirements
| | Needed for |
|---|---|
| **JDK 11 or newer** (`java -version` and `javac -version` both work; JDK 21 recommended) | everything |
| Internet access once | SQLite/PostgreSQL JDBC driver (optional), JavaFX jars (JavaFX client only) |
| Docker (optional) | deploying the server in a container |

## 2. Run it (Swing client) - 4 commands
```bash
# Linux / macOS                       # Windows PowerShell (from the project folder)
./scripts/setup-sqlite.sh             .\scripts\setup-sqlite.bat    # once: installs the SQLite driver into lib/
bash ./scripts/setup-postgres.sh      .\scripts\setup-postgres.bat  # optional: installs the PostgreSQL driver into lib/
./build.sh                            .\build.bat
./run-server.sh                       .\run-server.bat              # terminal 1: backend on http://localhost:8080
./run-client.sh                       .\run-client.bat              # terminal 2: Swing desktop app (start several to log in as different users)
```
Choose **Admin**, **Seller**, or **Buyer** in the login mode selector. Admin mode requires the protected admin account; Seller and Buyer are modes for the same regular account and open the matching dashboard (both marketplace functions remain available). Recycler accounts can select **Recycler** to open recycler tools. Sign in as **admin / admin123** (you will be nagged on the dashboard to change it), or press *Create account...* to register a user.
*Track an item...* on the sign-in window needs no account.

If you skip `setup-sqlite`, the server still runs on a file-backed store (`data/ewaste.snapshot`) and says so at startup. SQLite remains the
default when its driver is installed. To use PostgreSQL, install its driver, create a database/user, then set `EWASTE_STORE=postgres`,
`EWASTE_DB_URL=jdbc:postgresql://localhost:5432/ewaste`, `EWASTE_DB_USER`, and `EWASTE_DB_PASSWORD` before starting the server.
The app creates/updates tables but does not copy records from another store.

Server options: `--port=8080 --host=127.0.0.1 --data=data --store=auto|sqlite|postgres|file --mail=config/mail.properties`, or the environment variables
`EWASTE_PORT/HOST/DATA/STORE/DB_URL/DB_USER/DB_PASSWORD/MAIL_FILE` (command line wins). `EWASTE_ADMIN_PASSWORD` sets the first admin's password.
To let other computers connect, use `--host=0.0.0.0` and type `http://<server-ip>:8080` in the client's *Server* box (or pass it as the first argument).

## 3. Run it (JavaFX client)
Same server as above, plus:
```bash
# Linux / macOS                       # Windows PowerShell (from the project folder)
./scripts/setup-javafx.sh             .\scripts\setup-javafx.bat    # once: downloads JavaFX 21 for your OS into lib-fx/ (checksums verified)
./build-fx.sh                         .\build-fx.bat                # compiles src-fx into out-fx/
./run-client-fx.sh                    .\run-client-fx.bat           # start the JavaFX app (optional argument: server URL)
```
Prefer your own JavaFX? Download the SDK from https://openjfx.io, put its `lib/*.jar` files in `lib-fx/`, and skip the setup script.
If `build-fx` reports a compile error, it will name the file and line - the clients share all logic in `ApiClient`, so fixes are local to `src-fx/com/ewaste/fx/`.

## 4. What each role can do (suggested demo)
1. User: **Submit item** (e.g. *Laptop, 3 years, minor fault*) - you get a tracking code like `EW-2026-00012` and a suggested plan.
2. Admin: **Dashboard** shows "Needs attention" (click to jump to the tab). **Review submissions** → *Approve...* (plan + pickup date) or *Reject...*.
3. Admin: **Pickups & stock** → *Mark collected* (owner earns points) → *Start repair / Repair done* → *Publish to market*.
4. Admin: **Recycling** → *Queue all* → *Ship regular batch* / *Ship hazardous batch* (batteries only go to a certified partner).
5. Another user: **Market** → *Circularity passport...*, *Message seller...*, or *Buy*, redeeming points. The passport shows the item's score breakdown, material/CO2 estimates, and journey without revealing user details. **Buyer dashboard** tracks purchases; **Seller dashboard** summarizes donated items sold and supports listing photos. Messages support PNG/JPEG/PDF attachments. Use **Help & support** to message administrators privately; admins can read and reply from **Support inbox**. **My dashboard** shows tier progress, badges, charts, CO2 equivalents; *Save impact certificate...* writes a printable HTML file.
6. Admin: **Drives** → *New drive...* (goal in kg; every user is notified and sees progress). **Announcements & email** broadcasts messages.
7. Admin: **Users** → *Toggle recycler role* to grant a registered account recycler access. Admin: **Recycler auctions** → publish a collected recycling item with a minimum bid and closing time. Recycler: **Recycler auctions** → submit one bid with a proposed collection date and recycling method. Admins compare bids and award one; bidders receive the result in notifications.

| Area | User / Recycler | Admin |
|---|---|---|
| Accounts | register, sign in, profile, change password, email opt-out; recycler access is granted by an admin | activate / deactivate users, assign recycler role, bonus points |
| E-waste | submit, track status + timeline, **public tracking code** | review queue, approve with plan + pickup date, reject, collect, change plan |
| Reuse / repair | - | refurbishment with repair cost, publish, change price, withdraw |
| Market | search, public Circularity Passport, message sellers with file attachments, buy, pay with points (max 30% off), orders, buyer dashboard | seller dashboard, listing photos, price-drop and new-listing alerts |
| Recycling | - | queue, ship regular or certified-hazardous batches, materials + CO2 per batch |
| **Recycler auctions** | submit bids, view own result and auction closing/status | publish eligible items, compare bids, award/cancel auctions, view audit history and winning bid |
| **Support** | private conversation with administrators | inbox, unread counts, and private replies |
| **Dashboards** | tier (Bronze...Platinum), 8 badges, pipeline, monthly kg, CO2 equivalents, drives, certificate | alerts, KPIs vs previous period (7/30/90/180 days), trend + breakdown charts, funnel, top donors |
| **Drives** | see active drives with progress | create / end collection drives with a kg goal |
| Updates | notification inbox, optional emails | announcements (in-app + email), email status + test |
| Data | leaderboard | search all items, CSV export |

**Decision engine** (`DecisionEngine.java`): item submission and admin review screens show an explainable, rule-based report. It compares functional resale, as-is resale, repair cost and estimated material recovery against category-specific life and repair thresholds. Hazard classification takes precedence over financial value. Missing financial inputs are estimated and flagged; input completeness is not an ML probability. The environmental estimate applies the existing category CO2 factor once and is not included in analytics until an existing lifecycle event records realized reuse/recycling. Threshold defaults can be overridden in `config/decision.properties` (copy `config/decision.properties.example`); set `EWASTE_DECISION_FILE` to select another properties file.

Marketplace sellers are the users who donated the listed items; administrators continue to review and publish those listings. A buyer can contact
the donor before or after ordering. The owner can reply from **Messages** and add a PNG/JPEG photo from **Seller dashboard**.

Screenshots: `docs/screenshots/`.

## 5. Email notifications (optional)
Every in-app notification can also be emailed. Email is **off** until configured.
1. Copy `config/mail.properties.example` to `config/mail.properties` and set `mail.enabled=true` plus your SMTP details
   (Gmail: `smtp.gmail.com`, port 587, `STARTTLS`, your address, and an **App password**). Or use the `EWASTE_MAIL_*` environment variables (incl. `EWASTE_MAIL_PASSWORD`).
2. Restart the server - it prints `Email notifications: ON via ...`.
3. Admin app → **Announcements & email** → *Send test email...* proves delivery; the screen also shows sent / failed counts and the last error.

Mail is queued and sent by a background thread only after the request succeeded, never blocks the app and is not retried. The client refuses to
send credentials unencrypted when STARTTLS is chosen and checks the server certificate name.

## 6. Deploying it
Short version (full guide with HTTPS, backups, updates, firewall and troubleshooting: **[DEPLOY.md](DEPLOY.md)**):

**Server on a VPS with Docker + automatic HTTPS**
```bash
git clone <your repo> ewaste && cd ewaste        # or upload this folder
cp deploy/.env.example .env                      # set EWASTE_DOMAIN and EWASTE_ADMIN_PASSWORD
docker compose up -d --build                     # app + Caddy (free HTTPS certificate)
```
Then point the DNS A record of your domain at the server, open ports 80/443, and give users `https://your-domain` as the *Server* address.

**Server without Docker**: `./scripts/package.sh` → copy `dist/` to the server → `java -jar ewaste-server.jar` (systemd unit in `deploy/ewaste.service`, nginx sample in `deploy/nginx.conf`).

**Clients**: `./scripts/package.sh` gives `ewaste-client.jar` (double-click / `java -jar`); `./scripts/make-installer.sh` (or `.bat` on Windows) builds a native
installer with `jpackage` for the OS you run it on. The clients are desktop apps, so each user installs one and points it at your server URL.

New to GitHub or deployment? Follow the [beginner Windows guide](docs/GITHUB_AND_DEPLOY.md) for copy-paste steps to test, push changes, deploy the server, and connect your desktop client.

## 7. Project layout
```
src/com/ewaste/
  model/       entities and enums (User, EWasteItem, Category, Decision, Campaign, ...)
  common/      Json (reader/writer), Hash (salted SHA-256), Money
  server/      ServerMain, Api (routes), Dto, Sessions, LoginThrottle, HealthCheck
    service/   Auth, Item, Market, Recycling, Notification, Report, DecisionEngine, Tracking, CampaignService, AnalyticsService
    mail/      Mailer, SmtpMailer (pure-Java SMTP client), MailConfig
    repo/      Repository + JDBC, SQLite and PostgreSQL repositories + MemoryRepository (file-backed fallback / tests)
  client/      Swing: ApiClient, LoginFrame, MainFrame, one navigable Page per screen; charts/ = Java2D bar, line, donut, horizontal bar, meter
src-fx/com/ewaste/fx/   JavaFX: FxMain (login + tracker), MainView, UserPages, AdminPages, FxDashboards, Charts, Fx helpers
test/        ApiTest, DashboardTest, MailTest (+FakeSmtpServer), GuiSmoke (opens every Swing window), ThrottleTest
deploy/      Caddyfile, nginx.conf, ewaste.service, .env.example      Dockerfile, docker-compose.yml
scripts/     setup-sqlite.*, setup-postgres.*, setup-javafx.*, package.sh, make-installer.*, check_sql.py
config/      mail.properties.example        docs/  API.md + screenshots
```
Database tables (created and migrated automatically): `users, items, item_history, listings, orders, market_messages, notifications, batches, batch_items, campaigns`.
REST endpoints: [docs/API.md](docs/API.md).

## 8. Tests - and what they do and do not prove
```bash
./test.sh        # API, mail, dashboards, and throttle tests; repeats API tests on SQLite when the driver is installed
# Windows PowerShell: .\test.bat
# PostgreSQL integration: also set EWASTE_TEST_POSTGRES_USER/PASSWORD and two separate disposable DB URLs
python3 scripts/check_sql.py                       # runs every SQL statement against a real SQLite engine (also the schema migration)
xvfb-run java -cp "out:out-test:lib/*" com.ewaste.GuiSmoke shots   # Linux headless: opens every Swing window, saves screenshots
```
| Part | Status |
|---|---|
| REST API, business rules, dashboards, drives, tracking, throttle | automated tests pass (in-memory store, real HTTP) |
| Swing client, all tabs incl. dashboards | opened with real data under a virtual display, screenshots inspected |
| SQLite repository | API tests also run against SQLite when the JDBC driver is installed; schema SQL can be validated with Python's `sqlite3` |
| PostgreSQL repository | Implemented with a separate PostgreSQL schema; run against a test database before production use |
| Email | tested against a fake SMTP server (plain + AUTH); **STARTTLS/SSL not run against a real provider** - use the test button first |
| JavaFX client | compiled with JavaFX 21; no separate GUI smoke test |
| Dockerfile / compose / Caddy | the build steps and start-up were replayed by hand (same compile, env vars, health check); **no Docker daemon was available**, so the image itself was never built |
| Packaging | `package.sh` jars run; `jpackage` app-image builds and starts |

## 9. Customising
* Currency symbol: `common/Money.java` and `client/Ui.java`. Categories, prices, reuse ages, reward rates, CO2: `model/Category.java`.
* Reuse/recycle rules: `DecisionEngine.java`. Point value / cap: `MarketService`. Reward tiers and badges: `AnalyticsService`.
* Default admin password: set `EWASTE_ADMIN_PASSWORD` before the first start (applies only while the database has no admin).

## 10. Limits
* The API is plain HTTP and sessions live in server memory (a restart signs everyone out): use the HTTPS proxy in section 6 for anything beyond a local network.
* Services load whole tables and filter in Java: fine for a school / community scale, not millions of rows. One server instance only.
* "Payment" is pay-on-delivery; no payment gateway, no SMS. Emails are plain text and not retried.
* The public tracking endpoint is not rate limited; codes reveal only status, never personal data.
