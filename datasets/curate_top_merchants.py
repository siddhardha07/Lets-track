#!/usr/bin/env python3
"""
Curates a small, high-quality merchant seed list from the full OSM-derived
merchant_database.json (486K rows) -- for the app's SmartCategorizer seed
table, quantity is actively harmful (10k+ fake/uniform-confidence rows
already proved that), so this picks a few hundred to ~1000 merchants that
are actually likely to show up in real transaction SMS: ones that appear
at many distinct OSM locations, since a name repeated across many places is
much more likely to be a recognizable chain/brand than a one-off local shop.

The main deduplication pass in extract.py already collapsed every physical
location sharing a canonical name down to one merged record, which is
correct for a lookup table -- but it discarded the "how many locations had
this exact name" count in the process. duplicate_report.csv recorded every
individual collapse event, so occurrence_count for a given canonical_name
is reconstructed as (rows in duplicate_report.csv for that name) + 1.

Output: common_merchants_curated.json, in the exact schema
CommonMerchantsLoader.kt already expects:
  {"merchants": {"NAME": {"category": ..., "confidence": ...}}}
"""

import csv
import json
import re
from collections import Counter
from pathlib import Path

OUTPUT_DIR = Path(__file__).parent / "output"
MERCHANT_DB = OUTPUT_DIR / "merchant_database.json"
DUPLICATE_REPORT = OUTPUT_DIR / "duplicate_report.csv"
CURATED_OUTPUT = OUTPUT_DIR / "common_merchants_curated.json"

TARGET_COUNT = 1000

# extract.py's taxonomy doesn't line up 1:1 with the app's own category set
# (DefaultCategories.kt) -- OSM captures physical points of interest, which
# is a different thing from bill-payment/subscription "merchants" like
# Airtel or Netflix that were in the old fake seed list and have no
# corresponding OSM POI at all. This mapping only covers what OSM data can
# actually speak to; it deliberately doesn't invent Bills & Utilities /
# Financial Services coverage that isn't backed by real location data here.
CATEGORY_MAP = {
    ("Food", "Groceries"): "Groceries",
    ("Food", "Supermarket"): "Groceries",
    "Food": "Food",
    "Healthcare": "Healthcare",
    "Transportation": "Transportation",
    "Shopping": "Shopping",
    "Travel": "Transportation",  # Travel and Transportation were merged app-side, see DefaultCategories.kt
    "Entertainment": "Entertainment",
    "Personal Services": "Personal Care",
    "Financial Services": "Other",  # Bank/ATM/Insurance don't cleanly fit any existing app category
    "Education": "Education",
    "Government": "Other",
}

# Reject names that are junk-prefixed or too generic to safely identify one
# specific merchant (a bare "SHOP" or "MEDICAL" matching would mis-tag
# unrelated transactions).
JUNK_PREFIX_RE = re.compile(r"^[^A-Za-z0-9]")
GENERIC_NAMES = {
    "SHOP", "STORE", "MEDICAL", "MEDICALS", "CLINIC", "HOSPITAL", "HOTEL",
    "RESTAURANT", "BAKERY", "PHARMACY", "SALON", "SCHOOL", "COLLEGE",
    "BANK", "ATM", "SUPERMARKET", "GENERAL STORE", "TEA STALL", "CAFE",
    "ABC", "AMPHITHEATER", "AMPHITHEATRE", "AUDITORIUM", "STADIUM",
    "PLAYGROUND", "GROUND", "PARK", "CLUB", "COLONY", "COMMERCIAL",
    "COTTAGE", "COURTYARD", "COMMUNITY CENTER", "COMMUNITY CENTRE",
    "CITY CENTER", "CITY CENTRE", "CITY MALL", "CITY PALACE",
}
# Bare academic-department names ("Department of Chemistry") -- always
# generic, never a payable merchant, regardless of which subject follows.
DEPARTMENT_PREFIX = "DEPARTMENT OF "

# Caught by spot-checking the first run: things like "ADMIN BLOCK" or
# "A BLOCK" ranked high on raw occurrence count not because they're one
# real, recognizable merchant, but because many *unrelated* schools/
# hospitals/campuses across India each happen to have an internal building
# literally named that -- coincidental frequency, not brand recognition.
GENERIC_WORD_RE = re.compile(
    r"\b(BLOCK|BUILDING|WING|ANNEXE|ANNEX|CAMPUS|HOSTEL|QUARTERS|"
    r"ENTRANCE|EXIT|GATE|PARKING|RECEPTION|OFFICE|ADMIN|ADMINISTRATIVE|"
    r"ACADEMIC)\b"
)


def map_category(category: str, subcategory: str) -> str | None:
    key = (category, subcategory)
    if key in CATEGORY_MAP:
        return CATEGORY_MAP[key]
    return CATEGORY_MAP.get(category)


def is_quality_name(canonical: str) -> bool:
    if len(canonical) < 4:
        return False
    if JUNK_PREFIX_RE.match(canonical):
        return False
    if canonical in GENERIC_NAMES:
        return False
    if canonical.startswith(DEPARTMENT_PREFIX):
        return False
    if GENERIC_WORD_RE.search(canonical):
        return False
    return True


def confidence_for_rank(rank: int, total: int) -> float:
    # Tiered, not a flat number for every row (that uniform-0.95 pattern is
    # exactly what made the old seed list look/act fake) -- higher-frequency
    # merchants get a higher confidence since more independent OSM locations
    # sharing a name is itself real evidence, not just an assumption.
    if rank < 100:
        return 0.92
    if rank < 400:
        return 0.87
    return 0.80


def main():
    print(f"[LOAD] {MERCHANT_DB}")
    with open(MERCHANT_DB, encoding="utf-8") as f:
        records = json.load(f)
    print(f"[LOAD] {len(records):,} merchant records")

    print(f"[LOAD] {DUPLICATE_REPORT}")
    occurrence_extra = Counter()
    with open(DUPLICATE_REPORT, encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        for row in reader:
            occurrence_extra[row["canonical_name"]] += 1
    print(f"[LOAD] {sum(occurrence_extra.values()):,} duplicate-collapse events")

    candidates = []
    for record in records:
        canonical = record.get("canonical_name", "")
        category = record.get("category", "")
        subcategory = record.get("subcategory", "")

        if not category:
            continue
        if not is_quality_name(canonical):
            continue

        mapped_category = map_category(category, subcategory)
        if mapped_category is None:
            continue

        occurrence_count = occurrence_extra.get(canonical, 0) + 1
        candidates.append((occurrence_count, canonical, mapped_category, subcategory))

    print(f"[FILTER] {len(candidates):,} candidates with a usable category/name")

    # Highest occurrence first -- names repeated across the most distinct
    # OSM locations are the ones most likely to actually appear in someone's
    # real transaction SMS.
    candidates.sort(key=lambda c: c[0], reverse=True)

    # One row per canonical name (candidates is already unique per name from
    # merchant_database.json, but guard against it anyway).
    seen = set()
    curated = {}
    for rank, (occurrence_count, canonical, mapped_category, subcategory) in enumerate(candidates):
        if canonical in seen:
            continue
        seen.add(canonical)
        curated[canonical] = {
            "category": mapped_category,
            "confidence": confidence_for_rank(len(curated), len(candidates)),
        }
        if len(curated) >= TARGET_COUNT:
            break

    print(f"[CURATE] {len(curated):,} merchants selected")

    category_counts = Counter(v["category"] for v in curated.values())
    print("[CURATE] By category:")
    for category, count in category_counts.most_common():
        print(f"  {category}: {count}")

    output = {"merchants": curated}
    CURATED_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with open(CURATED_OUTPUT, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2, sort_keys=True)

    print(f"[WRITE] {CURATED_OUTPUT}")


if __name__ == "__main__":
    main()
