# BookScanPrice

Android app that scans a book's ISBN barcode with your phone camera and estimates a resale price using free data sources, with one-tap links to real eBay sold prices.

## How it works

1. **Scan**: CameraX + Google ML Kit reads EAN-13/ISBN barcodes on-device (free, no API key).
2. **Lookup**: Fetches book metadata from Google Books and Open Library (both free, no key).
3. **Price estimate**:
   - If Google Books reports a retail price → estimate = ~40% of retail (typical used-book range).
   - If you later add an eBay API token → estimate = median active ask × 0.85.
4. **Real market check**: Buttons deep-link to eBay **sold listings** (the true "expected sell price" signal), eBay active listings, BookScouter buyback offers, and AbeBooks — all filtered to the scanned ISBN. No API keys needed for these.

## Build & install

1. Install [Android Studio](https://developer.android.com/studio) (Koala or newer).
2. Open this folder: **File → Open → BookScanPrice**.
3. Let Gradle sync (first sync downloads dependencies; needs internet).
4. Plug in your phone with USB debugging enabled (Settings → Developer options), or use an emulator with a camera.
5. Press **Run ▶**.

Min Android version: 7.0 (API 24).

## Optional: live eBay prices in-app

1. Create a free account at https://developer.ebay.com and an app key set.
2. Generate an OAuth **application token** (client-credentials grant, `https://api.ebay.com/oauth/api_scope`).
3. Paste it into `app/build.gradle.kts`:
   ```kotlin
   buildConfigField("String", "EBAY_APP_TOKEN", "\"YOUR_TOKEN_HERE\"")
   ```
4. Rebuild. The app will now show min/median of live eBay listings for each scan.

Note: eBay's Browse API returns **active** listings. True **sold** price history requires the Marketplace Insights API, which needs eBay's approval. Until then, the "eBay SOLD listings" button gives you the same data manually in one tap.

## Caveats

- Price estimates are heuristics — always check the sold-listings link before pricing a book.
- App tokens expire (~2 hours for client-credentials); for regular use you'd add a token-refresh call. For casual scanning, the no-key mode + sold-listings button works fine.
