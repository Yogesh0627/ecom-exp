# 🛠️ EcoExpress — Local Setup Guide

This guide takes you from a fresh machine to a **running copy of EcoExpress** on your own computer. Follow it top to bottom — every step has the exact command and what you should expect to see.

You'll run **two programs**:

1. **The backend** (`server/`) — the "engine". Written in Java, it holds the data and business logic and serves a REST API on **port 8081**.
2. **The frontend** (`client/`) — the "website". Written in TypeScript/Next.js, it's what you see in the browser, on **port 3000**.

The frontend talks to the backend, and the backend talks to a **PostgreSQL database**.

```
Your browser  ──►  Frontend (:3000)  ──►  Backend (:8081)  ──►  PostgreSQL
```

> ⏱️ **Time needed:** ~20–30 minutes the first time (mostly installing tools).
> 💡 **Good news:** the AI, payment, and email features are all **optional**. You can run the entire app with *just a database* — those features simply show "unavailable" until you add keys.

---

## 1. Prerequisites — install these first

| Tool | Version | Why | Check it's installed |
|---|---|---|---|
| **Java (JDK)** | **21** (recommended) | Runs the backend | `java -version` |
| **Maven** | 3.9+ | Builds/runs the backend | `mvn -version` |
| **Node.js** | **20 LTS** (18.17+ minimum) | Runs the frontend | `node -version` |
| **PostgreSQL** | 15+ | The database | `psql --version` |
| **Git** | any recent | To get the code | `git --version` |

**Where to get them:**
- Java 21 → [Adoptium Temurin 21](https://adoptium.net/temurin/releases/?version=21)
- Maven → [maven.apache.org/download](https://maven.apache.org/download.cgi) (or `brew install maven` / `choco install maven`)
- Node.js 20 → [nodejs.org](https://nodejs.org/) (LTS)
- PostgreSQL → [postgresql.org/download](https://www.postgresql.org/download/)

> ⚠️ **A note on the Java version.** The project targets Java 21. It also runs on newer JDKs (22, 23, 24) **but** the build's Lombok version must be bumped for JDK 22+ (in `server/pom.xml` set `<lombok.version>1.18.38</lombok.version>`). If you have a choice, **use JDK 21** and skip that.

> 🐘 **No local Postgres? Use a free cloud database instead.** Sign up at [neon.tech](https://neon.tech), create a project, and copy its connection string — then skip step 3 and use that URL in step 4.

---

## 2. Get the code

```bash
git clone <your-repo-url> ecom-exp
cd ecom-exp
```

You should see two main folders: `server/` and `client/`.

---

## 3. Create the database (skip if using Neon)

Create an empty PostgreSQL database and a user for the app. Open a terminal:

```bash
# Log in as the postgres superuser
psql -U postgres

# Inside psql, run these four lines:
CREATE DATABASE ecoexpress;
CREATE USER ecoexpress WITH PASSWORD 'ecoexpress';
GRANT ALL PRIVILEGES ON DATABASE ecoexpress TO ecoexpress;
\q
```

> You don't need to create any tables. The backend creates them automatically on first run (using Flyway migrations).

---

## 4. Configure the backend

The backend reads its settings from a file called `server/.env`. We ship an example — copy it and fill in the blanks.

```bash
cd server
cp .env.example .env       # Windows PowerShell: copy .env.example .env
```

Open `server/.env` and set **the only three values that are truly required** — the database connection:

```dotenv
# --- Database (REQUIRED) ---
DB_URL=jdbc:postgresql://localhost:5432/ecoexpress
DB_USERNAME=ecoexpress
DB_PASSWORD=ecoexpress

# --- Security (REQUIRED) — any long random string ---
JWT_SECRET=change-this-to-a-long-random-string-at-least-32-chars
```

> **Using Neon?** Your `DB_URL` looks like:
> `jdbc:postgresql://ep-xxxx.aws.neon.tech/neondb?sslmode=require`
> (note the `jdbc:` prefix — add it if Neon's copied string starts with `postgresql://`), with `DB_USERNAME` / `DB_PASSWORD` from Neon.

**Everything else in `.env` is optional.** Leave the keys blank and those features turn off cleanly:

| If you leave blank... | ...this turns off |
|---|---|
| `GEMINI_API_KEY` / `ecoexpress.ai.api-key` | AI features (Smart Fridge, Meal Planner, recipes) — they return a friendly "AI unavailable" |
| `ecoexpress.razorpay.*` | Online payments (you can still place orders; checkout just won't charge a card) |
| `RESEND_API_KEY` | Outgoing emails (receipts, alerts) — the app still works, it just doesn't send mail |
| `S3_*` (with `STORAGE_PROVIDER=local`) | Cloud file storage — uploads are saved to a local folder instead |

Want the optional bits? See **[Optional integrations](#8-optional-integrations)** at the end.

---

## 5. Run the backend

From the `server/` folder:

```bash
mvn spring-boot:run
```

The first run downloads dependencies and may take a couple of minutes. **You'll know it worked when you see:**

```
... Flyway ... Successfully applied N migrations to schema "public"
... Tomcat started on port 8081
... Started EcoExpressApplication in 18.xxx seconds
```

✅ **Test it** — in another terminal (or your browser):

```bash
curl http://localhost:8081/actuator/health
# expected:  {"status":"UP"}
```

Leave this terminal running.

> 💡 On first boot the app also creates a **demo admin account** (from the `ecoexpress.bootstrap.*` values in `.env`) and seeds starter data, so you have something to log in with immediately.

---

## 6. Configure & run the frontend

Open a **new** terminal. The frontend needs to know where the backend is.

```bash
cd client
cp .env.local.example .env.local   # Windows: copy .env.local.example .env.local
```

The default already points at your local backend, so you usually don't need to change anything:

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://localhost:8081/api/v1
```

Now install packages and start it:

```bash
npm install       # first time only — downloads frontend packages
npm run dev
```

**You'll know it worked when you see:**

```
▲ Next.js 14.x
- Local:  http://localhost:3000
✓ Ready in 5s
```

---

## 7. Open the app 🎉

Go to **http://localhost:3000**.

On the login page, use the **one-click demo buttons**, or these seeded accounts:

| Role | Email | Password |
|---|---|---|
| 👤 **Customer** | `demo@ecoexpress.in` | `Demo-User-2026` |
| 🛠️ **Admin** | `admin@ecoexpress.in` | `ChangeMe-EcoAdmin-2026` |

> The admin account can reach the **Admin console** (top-right menu) to manage products, stock, orders, coupons and more. The customer account is for the normal shopping experience.

That's it — you're running EcoExpress locally. 🌿

---

## 8. Optional integrations

Add these to `server/.env` only if you want the corresponding feature. **All are optional.**

<details>
<summary><b>🤖 AI features (Google Gemini) — free tier available</b></summary>

1. Get a free API key at [Google AI Studio](https://aistudio.google.com/app/apikey).
2. In `server/.env`:
   ```dotenv
   GEMINI_API_KEY=your-key-here
   ecoexpress.ai.api-key=${GEMINI_API_KEY}
   ecoexpress.ai.model=gemini-2.0-flash
   ```
3. Restart the backend.

> The free tier is limited (roughly 20 requests/day). When it's exhausted, the app detects it and shows a "AI is temporarily unavailable" banner instead of breaking.
</details>

<details>
<summary><b>💳 Payments (Razorpay test mode)</b></summary>

1. Create a free [Razorpay](https://razorpay.com/) account and switch the dashboard to **Test Mode**.
2. Copy your **Test** API keys and (from Settings → Webhooks) create a webhook secret.
3. In `server/.env`:
   ```dotenv
   ecoexpress.razorpay.key-id=rzp_test_xxxxx
   ecoexpress.razorpay.key-secret=your-test-secret
   ecoexpress.razorpay.webhook-secret=your-webhook-secret
   ```
4. Use the test card `4111 1111 1111 1111` (any future expiry, any CVV) at checkout.
</details>

<details>
<summary><b>📧 Email (Resend)</b></summary>

1. Get an API key at [resend.com](https://resend.com/).
2. In `server/.env`:
   ```dotenv
   RESEND_API_KEY=re_xxxxx
   EMAIL_FROM=EcoExpress <onboarding@resend.dev>
   ```
</details>

---

## 9. Troubleshooting

| Symptom | Cause & fix |
|---|---|
| `Port 8081 was already in use` | Another program (or an old run) is on 8081. Stop it, or change `SERVER_PORT` in `.env`. |
| Backend fails: **`FATAL: password authentication failed`** | Wrong `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`. Re-check step 3/4. |
| Backend fails: **`Connection refused`** to the DB | PostgreSQL isn't running, or the host/port in `DB_URL` is wrong. |
| Build error: **`java: TypeTag :: UNKNOWN` / Lombok** | You're on JDK 22+. Set `<lombok.version>1.18.38</lombok.version>` in `server/pom.xml`, or use JDK 21. |
| Frontend shows data but **actions fail / 401s** | `NEXT_PUBLIC_API_BASE_URL` is wrong, or the backend isn't running. It must be `http://localhost:8081/api/v1`. |
| `npm run build` prints **`fetch failed ECONNREFUSED`** | Harmless — the build tries to fetch live data from the backend for the sitemap/metadata. Start the backend first for a clean build, or ignore it (the build still succeeds). |
| `next build` fails on **`/opengraph-image` "Invalid URL"** | The social-image route needs `export const runtime = 'edge';` at the top of `client/src/app/opengraph-image.tsx`. |
| Changed `.env` but nothing happened | Env changes are read at startup — **restart** the affected app (backend or frontend). |

---

## 10. Quick reference

```bash
# ── Backend (server/) ──
mvn spring-boot:run            # run the API           → http://localhost:8081
mvn clean compile              # just compile / check
mvn flyway:info                # see database migration status

# ── Frontend (client/) ──
npm install                    # install packages (first time)
npm run dev                    # dev server            → http://localhost:3000
npm run build                  # production build
npm run lint                   # check code style
```

| Thing | Value |
|---|---|
| Frontend URL | http://localhost:3000 |
| Backend URL | http://localhost:8081 |
| API base path | http://localhost:8081/api/v1 |
| Health check | http://localhost:8081/actuator/health |
| API docs (Swagger) | http://localhost:8081/swagger-ui.html |

Happy hacking! For deploying to the cloud, see **[DEPLOY.md](DEPLOY.md)**.
