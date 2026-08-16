#!/usr/bin/env python3
"""
Merges hand-picked, well-known real merchants into the curated seed list
produced by curate_top_merchants.py -- the OSM-derived data is great for
physical retail/food/healthcare locations, but structurally can't cover
non-physical "merchants" (apps, subscriptions, e-commerce, banks) since
those have no OSM point of interest at all. This fills that gap with
merchants everyone actually recognizes, hand-verified rather than
frequency-ranked.

Categories are remapped onto the app's real category set (DefaultCategories.kt)
-- CategoryResolver.resolveCategoryId only exact-matches a merchant's stored
category against the user's actual categories, then falls back to
MlCategoryMapper's fixed 8-label mapping. Neither knows about ad-hoc labels
like "Fashion"/"Coffee"/"Dining"/"Utilities"/"Travel"/"Fuel"/"Pharmacy"/
"Transfer"/"Investment"/"Insurance" -- inserting those as-is would silently
resolve to "Other" through an undocumented path instead of an honest one.
"""

import json
from pathlib import Path

CURATED_PATH = Path(__file__).parent / "output" / "common_merchants_curated.json"
ASSET_PATH = Path(__file__).parent.parent / "app" / "src" / "main" / "assets" / "common_merchants.json"

# From the user, with categories remapped onto the app's real category set:
#   Fashion -> Shopping, Coffee/Dining -> Food, Utilities -> Bills & Utilities,
#   Travel/Fuel -> Transportation, Pharmacy -> Healthcare,
#   Transfer/Investment/Insurance -> Other (no dedicated category exists for these)
USER_PROVIDED = {
    "SWIGGY": ("Food", 0.91),
    "ZOMATO": ("Food", 0.93),
    "UBER": ("Transportation", 0.90),
    "OLA": ("Transportation", 0.91),
    "RAPIDO": ("Transportation", 0.95),
    "NETFLIX": ("Entertainment", 0.96),
    "AMAZON": ("Shopping", 0.97),
    "FLIPKART": ("Shopping", 0.90),
    "MYNTRA": ("Shopping", 0.99),
    "AJIO": ("Shopping", 0.96),
    "BIGBASKET": ("Groceries", 0.91),
    "BLINKIT": ("Groceries", 0.94),
    "ZEPTO": ("Groceries", 0.99),
    "DMART": ("Groceries", 0.97),
    "RELIANCE FRESH": ("Groceries", 0.97),
    "STARBUCKS": ("Food", 0.92),
    "DOMINOS": ("Food", 0.97),
    "KFC": ("Food", 0.95),
    "MCDONALDS": ("Food", 0.98),
    "BOOKMYSHOW": ("Entertainment", 0.98),
    "SPOTIFY": ("Entertainment", 0.95),
    "AIRTEL": ("Bills & Utilities", 0.95),
    "JIO": ("Bills & Utilities", 0.96),
    "IRCTC": ("Transportation", 0.92),
    "HPCL": ("Transportation", 0.99),
    "BPCL": ("Transportation", 0.93),
    "IOCL": ("Transportation", 0.95),
    "APOLLO PHARMACY": ("Healthcare", 0.96),
    "PAYTM": ("Other", 0.91),
    "PHONEPE": ("Other", 0.94),
    "GOOGLE PAY": ("Other", 0.93),
    "ZERODHA": ("Other", 0.95),
    "UPSTOX": ("Other", 0.93),
    "LIC": ("Other", 0.95),
}

# Additional well-known real Indian merchants -- hand-picked for genuine
# recognizability, not frequency-ranked like the OSM batch. Kept deliberately
# short rather than exhaustive: the whole point of this cleanup was fewer,
# higher-confidence entries instead of a huge uncertain pile.
ADDITIONAL_KNOWN = {
    # Food delivery / chains
    "PIZZA HUT": ("Food", 0.95),
    "BURGER KING": ("Food", 0.93),
    "SUBWAY": ("Food", 0.92),
    "HALDIRAM": ("Food", 0.90),
    "BARBEQUE NATION": ("Food", 0.90),
    "CAFE COFFEE DAY": ("Food", 0.88),
    "WOW MOMO": ("Food", 0.85),
    # Groceries / quick commerce
    "SPENCERS": ("Groceries", 0.88),
    "MORE SUPERMARKET": ("Groceries", 0.86),
    "JIOMART": ("Groceries", 0.90),
    "NATURES BASKET": ("Groceries", 0.85),
    # Shopping
    "CROMA": ("Shopping", 0.92),
    "RELIANCE DIGITAL": ("Shopping", 0.92),
    "VIJAY SALES": ("Shopping", 0.88),
    "NYKAA": ("Shopping", 0.93),
    "LENSKART": ("Shopping", 0.92),
    "DECATHLON": ("Shopping", 0.90),
    "IKEA": ("Shopping", 0.90),
    "TATA CLIQ": ("Shopping", 0.86),
    "SHOPPERS STOP": ("Shopping", 0.88),
    "PANTALOONS": ("Shopping", 0.88),
    "WESTSIDE": ("Shopping", 0.85),
    "LIFESTYLE": ("Shopping", 0.83),
    # Travel
    "MAKEMYTRIP": ("Transportation", 0.94),
    "GOIBIBO": ("Transportation", 0.90),
    "YATRA": ("Transportation", 0.88),
    "OYO": ("Transportation", 0.90),
    "INDIGO": ("Transportation", 0.90),
    "AIR INDIA": ("Transportation", 0.90),
    "SPICEJET": ("Transportation", 0.88),
    "VISTARA": ("Transportation", 0.88),
    "REDBUS": ("Transportation", 0.90),
    # Bills & Utilities
    "TATA POWER": ("Bills & Utilities", 0.92),
    "BSNL": ("Bills & Utilities", 0.90),
    "VODAFONE IDEA": ("Bills & Utilities", 0.90),
    "ACT FIBERNET": ("Bills & Utilities", 0.90),
    "TATA PLAY": ("Bills & Utilities", 0.88),
    "ADANI ELECTRICITY": ("Bills & Utilities", 0.90),
    # Entertainment / subscriptions
    "AMAZON PRIME": ("Entertainment", 0.93),
    "HOTSTAR": ("Entertainment", 0.92),
    "SONY LIV": ("Entertainment", 0.85),
    "JIOCINEMA": ("Entertainment", 0.85),
    "PVR": ("Entertainment", 0.92),
    "INOX": ("Entertainment", 0.90),
    # Healthcare
    "PRACTO": ("Healthcare", 0.88),
    "1MG": ("Healthcare", 0.90),
    "NETMEDS": ("Healthcare", 0.88),
    "PHARMEASY": ("Healthcare", 0.88),
    "FORTIS HOSPITAL": ("Healthcare", 0.90),
    "MAX HOSPITAL": ("Healthcare", 0.88),
    "MANIPAL HOSPITAL": ("Healthcare", 0.88),
    "APOLLO HOSPITAL": ("Healthcare", 0.92),
    # Banks / financial (no dedicated category -- Other, same rationale as LIC/Zerodha above)
    "HDFC BANK": ("Other", 0.93),
    "ICICI BANK": ("Other", 0.93),
    "STATE BANK OF INDIA": ("Other", 0.93),
    "AXIS BANK": ("Other", 0.92),
    "KOTAK MAHINDRA BANK": ("Other", 0.90),
    "YES BANK": ("Other", 0.85),
    "PUNJAB NATIONAL BANK": ("Other", 0.88),
    "BANK OF BARODA": ("Other", 0.88),
    "GROWW": ("Other", 0.90),
    "CRED": ("Other", 0.88),
}


def main():
    with open(CURATED_PATH, encoding="utf-8") as f:
        data = json.load(f)
    merchants = data["merchants"]

    before = len(merchants)
    added, overwritten = 0, 0

    for source_name, entries in [("user-provided", USER_PROVIDED), ("hand-picked", ADDITIONAL_KNOWN)]:
        for name, (category, confidence) in entries.items():
            if name in merchants:
                overwritten += 1
            else:
                added += 1
            merchants[name] = {"category": category, "confidence": confidence}

    print(f"[MERGE] {before} -> {len(merchants)} merchants ({added} new, {overwritten} overwrote an existing entry)")

    output = {"merchants": merchants}
    with open(CURATED_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2, sort_keys=True)
    with open(ASSET_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2, sort_keys=True)

    print(f"[WRITE] {CURATED_PATH}")
    print(f"[WRITE] {ASSET_PATH}")


if __name__ == "__main__":
    main()
