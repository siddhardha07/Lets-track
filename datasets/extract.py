#!/usr/bin/env python3
"""
Production-oriented India merchant database builder.

Sources:
  - Geofabrik India OpenStreetMap extracts
  - OpenStreetMap data contained in those extracts

Outputs:
  merchant_database.csv
  merchant_database.json
  merchant_statistics.json
  duplicate_report.csv
  source_report.md

IMPORTANT:
  This pipeline does NOT invent merchant names.
  Every merchant row originates from an OSM object with a name.
  OSM-derived records retain OSM as their source.

Usage:

  # Download/process the whole India extract:
  python build_merchant_database.py --download india

  # Process an already downloaded PBF:
  python build_merchant_database.py --pbf /data/india-latest.osm.pbf

  # Download/process all six India regional extracts:
  python build_merchant_database.py --download-regions

Requirements:
  pip install pyrosm pandas unidecode tqdm requests
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import shutil
import sys
import time
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

import pandas as pd
import requests
from tqdm import tqdm

try:
    from pyrosm import OSM
except ImportError:
    print("ERROR: pyrosm is not installed.")
    print("Run: pip install pyrosm")
    sys.exit(1)


# ============================================================
# CONFIGURATION
# ============================================================

GEOfABRIK_BASE = "https://download.geofabrik.de/asia"

INDIA_URL = (
    f"{GEOfABRIK_BASE}/india-latest.osm.pbf"
)

REGIONAL_URLS = {
    "central": f"{GEOfABRIK_BASE}/india/central-zone-latest.osm.pbf",
    "eastern": f"{GEOfABRIK_BASE}/india/eastern-zone-latest.osm.pbf",
    "north_eastern": f"{GEOfABRIK_BASE}/india/north-eastern-zone-latest.osm.pbf",
    "northern": f"{GEOfABRIK_BASE}/india/northern-zone-latest.osm.pbf",
    "southern": f"{GEOfABRIK_BASE}/india/southern-zone-latest.osm.pbf",
    "western": f"{GEOfABRIK_BASE}/india/western-zone-latest.osm.pbf",
}

OUTPUT_COLUMNS = [
    "merchant_name",
    "canonical_name",
    "aliases",
    "category",
    "subcategory",
    "country",
    "state",
    "city",
    "latitude",
    "longitude",
    "source",
]

DEFAULT_DATA_DIR = Path("merchant_data")
DEFAULT_OUTPUT_DIR = Path("merchant_output")

CHUNK_SIZE = 10000

# Names that are clearly not merchant/business names.
INVALID_NAMES = {
    "",
    "UNKNOWN",
    "UNNAMED",
    "YES",
    "NO",
    "NULL",
    "NONE",
    "N/A",
    "NA",
    "NAN",
}

# ============================================================
# CATEGORY TAXONOMY
# ============================================================

CATEGORY_RULES = {
    # Food
    "Food": {
        "Food Delivery": {
            "delivery",
            "food_delivery",
        },
        "Restaurant": {
            "restaurant",
            "fast_food",
            "food_court",
        },
        "Cafe": {
            "cafe",
        },
        "Tea Shop": {
            "tea",
        },
        "Bakery": {
            "bakery",
        },
        "Supermarket": {
            "supermarket",
            "hypermarket",
        },
        "Groceries": {
            "grocery",
            "convenience",
            "greengrocer",
            "deli",
        },
    },

    # Healthcare
    "Healthcare": {
        "Pharmacy": {
            "pharmacy",
            "chemist",
            "medical",
        },
        "Hospital": {
            "hospital",
        },
        "Clinic": {
            "clinic",
            "doctors",
            "dentist",
            "veterinary",
        },
    },

    # Transportation
    "Transportation": {
        "Fuel Station": {
            "fuel",
        },
        "Ride Hailing": {
            "taxi",
            "ride_hailing",
        },
        "Courier": {
            "courier",
            "parcel_locker",
        },
        "Logistics": {
            "logistics",
            "warehouse",
            "freight",
        },
    },

    # Shopping
    "Shopping": {
        "Shopping": {
            "department_store",
            "mall",
            "market",
            "general",
        },
        "Electronics": {
            "electronics",
            "computer",
            "computer_parts",
        },
        "Mobile Store": {
            "mobile_phone",
            "mobile_phone_shop",
        },
        "Furniture": {
            "furniture",
        },
        "Fashion": {
            "clothes",
            "shoes",
            "fashion",
            "boutique",
        },
        "Jewellery": {
            "jewelry",
        },
    },

    # Travel / hospitality
    "Travel": {
        "Hotel": {
            "hotel",
            "motel",
            "guest_house",
            "hostel",
        },
        "Travel Agency": {
            "travel_agency",
        },
    },

    # Entertainment
    "Entertainment": {
        "Cinema": {
            "cinema",
        },
        "Entertainment": {
            "arts_centre",
            "theatre",
            "nightclub",
            "music_venue",
            "amusement_arcade",
            "theme_park",
        },
    },

    # Personal services
    "Personal Services": {
        "Gym": {
            "fitness_centre",
            "sports_centre",
        },
        "Beauty": {
            "beauty",
            "cosmetics",
        },
        "Salon": {
            "hairdresser",
            "barber",
            "beauty",
        },
    },

    # Financial
    "Financial Services": {
        "Bank": {
            "bank",
        },
        "Insurance": {
            "insurance",
        },
        "Investment": {
            "investment",
            "financial_advisor",
        },
        "ATM": {
            "atm",
        },
    },

    # Education
    "Education": {
        "School": {
            "school",
        },
        "College": {
            "college",
        },
        "University": {
            "university",
        },
        "Training": {
            "language_school",
            "driving_school",
            "music_school",
        },
    },

    # Government
    "Government": {
        "Government": {
            "government",
            "townhall",
            "courthouse",
            "post_office",
            "police",
            "fire_station",
        },
    },
}


# ============================================================
# DOWNLOAD
# ============================================================

def download_file(url: str, destination: Path) -> Path:
    destination.parent.mkdir(parents=True, exist_ok=True)

    if destination.exists():
        print(f"[DOWNLOAD] Already exists: {destination}")
        return destination

    print(f"[DOWNLOAD] {url}")
    print(f"[DOWNLOAD] -> {destination}")

    headers = {
        "User-Agent": "merchant-database-builder/1.0"
    }

    with requests.get(
        url,
        headers=headers,
        stream=True,
        timeout=60,
    ) as response:

        response.raise_for_status()

        total = int(response.headers.get("content-length", 0))

        with open(destination, "wb") as f:
            with tqdm(
                total=total,
                unit="B",
                unit_scale=True,
                unit_divisor=1024,
                desc=destination.name,
            ) as progress:

                for chunk in response.iter_content(chunk_size=1024 * 1024):
                    if not chunk:
                        continue

                    f.write(chunk)
                    progress.update(len(chunk))

    return destination


# ============================================================
# TEXT NORMALIZATION
# ============================================================

def remove_emojis(text: str) -> str:
    """
    Remove Unicode symbols commonly used for emoji.

    We deliberately preserve ordinary non-Latin scripts.
    """
    if not text:
        return ""

    result = []

    for ch in text:
        category = unicodedata.category(ch)

        if category in {
            "So",
            "Sk",
        }:
            continue

        result.append(ch)

    return "".join(result)


def normalize_whitespace(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def clean_name(value) -> str:
    if value is None:
        return ""

    text = str(value)

    text = remove_emojis(text)
    text = normalize_whitespace(text)

    return text.strip()


def canonicalize_name(name: str) -> str:
    """
    Conservative canonicalization.

    Do NOT over-normalize because that can incorrectly merge
    unrelated merchants.

    Example:
      "Apollo Pharmacy" -> "APOLLO PHARMACY"
    """

    text = clean_name(name)

    if not text:
        return ""

    text = unicodedata.normalize("NFKC", text)

    text = text.upper()

    # Replace common separators with spaces.
    text = re.sub(r"[_|]+", " ", text)

    # Normalize punctuation spacing.
    text = re.sub(r"[“”\"'`]", "", text)

    text = re.sub(r"\s+", " ", text)

    return text.strip()


def is_valid_name(name: str) -> bool:
    if not name:
        return False

    canonical = canonicalize_name(name)

    if canonical in INVALID_NAMES:
        return False

    if len(canonical) < 2:
        return False

    # Reject strings consisting only of punctuation/numbers.
    if not re.search(r"[A-Z\u0080-\uffff]", canonical):
        return False

    return True


# ============================================================
# CATEGORY DETECTION
# ============================================================

def flatten_osm_tags(row) -> Dict[str, str]:
    tags = {}

    for col in row.index:
        if not isinstance(col, str):
            continue

        value = row[col]

        if pd.isna(value):
            continue

        value = str(value).strip()

        if not value:
            continue

        tags[col] = value

    return tags


def detect_category(row) -> Tuple[str, str]:
    """
    Detect category from OSM columns.

    Priority:
      amenity
      shop
      tourism
      leisure
      office
      craft
      healthcare
      railway/public_transport where useful
    """

    fields = [
        "amenity",
        "shop",
        "tourism",
        "leisure",
        "office",
        "craft",
        "healthcare",
        "public_transport",
        "railway",
    ]

    values = []

    for field in fields:
        value = row.get(field)

        if value is None:
            continue

        if pd.isna(value):
            continue

        value = str(value).strip().lower()

        if value:
            values.append(value)

    # Direct taxonomy lookup.
    for category, subcategories in CATEGORY_RULES.items():
        for subcategory, osm_values in subcategories.items():
            for value in values:
                if value in osm_values:
                    return category, subcategory

    # Additional semantic fallbacks.
    joined = " ".join(values)

    if "restaurant" in joined:
        return "Food", "Restaurant"

    if "cafe" in joined:
        return "Food", "Cafe"

    if "bakery" in joined:
        return "Food", "Bakery"

    if "pharmacy" in joined or "chemist" in joined:
        return "Healthcare", "Pharmacy"

    if "hospital" in joined:
        return "Healthcare", "Hospital"

    if "clinic" in joined:
        return "Healthcare", "Clinic"

    if "fuel" in joined:
        return "Transportation", "Fuel Station"

    if "hotel" in joined:
        return "Travel", "Hotel"

    if "cinema" in joined:
        return "Entertainment", "Cinema"

    if "bank" in joined:
        return "Financial Services", "Bank"

    return "", ""


# ============================================================
# LOCATION EXTRACTION
# ============================================================

def first_nonempty(row, fields: Iterable[str]) -> str:
    for field in fields:
        if field not in row.index:
            continue

        value = row[field]

        if pd.isna(value):
            continue

        value = str(value).strip()

        if value:
            return value

    return ""


def extract_city(row) -> str:
    return first_nonempty(
        row,
        [
            "city",
            "town",
            "municipality",
            "village",
            "suburb",
            "city_district",
            "locality",
        ],
    )


def extract_state(row) -> str:
    return first_nonempty(
        row,
        [
            "state",
            "state_name",
            "addr:state",
        ],
    )


# ============================================================
# ALIAS HANDLING
# ============================================================

ALIAS_FIELDS = [
    "name:en",
    "name",
    "official_name",
    "alt_name",
    "short_name",
    "brand",
    "operator",
    "name:hi",
    "name:kn",
    "name:ta",
    "name:te",
    "name:ml",
    "name:mr",
    "name:bn",
    "name:gu",
]


def extract_aliases(row, merchant_name: str) -> List[str]:
    aliases = []

    for field in ALIAS_FIELDS:
        if field not in row.index:
            continue

        value = row[field]

        if pd.isna(value):
            continue

        value = clean_name(value)

        if not value:
            continue

        aliases.append(value)

    # Add brand/operator when explicitly present.
    for field in ["brand", "operator"]:
        if field in row.index:
            value = row[field]

            if not pd.isna(value):
                value = clean_name(value)

                if value:
                    aliases.append(value)

    # De-duplicate case-insensitively.
    seen = set()
    output = []

    for alias in [merchant_name] + aliases:
        key = canonicalize_name(alias)

        if not key:
            continue

        if key in seen:
            continue

        seen.add(key)
        output.append(alias)

    return output


# ============================================================
# OSM READING
# ============================================================

def read_osm_named_objects(pbf_path: Path):
    """
    Read OSM points and polygons with names.

    pyrosm handles PBF parsing without requiring us to load
    the entire PBF into Python memory.
    """

    print(f"[OSM] Opening {pbf_path}")

    osm = OSM(str(pbf_path))

    print("[OSM] Reading named points...")

    try:
        points = osm.get_pois()
    except Exception:
        points = None

    if points is not None and len(points) > 0:
        yield points

    print("[OSM] Reading named buildings/areas...")

    # Many businesses are mapped as polygons rather than points.
    try:
        polygons = osm.get_buildings()
    except Exception:
        polygons = None

    if polygons is not None and len(polygons) > 0:
        yield polygons


# ============================================================
# RECORD CONVERSION
# ============================================================

def safe_float(value):
    try:
        if value is None or pd.isna(value):
            return None

        return float(value)

    except Exception:
        return None


def get_lat_lon(row):
    lat = None
    lon = None

    for field in ["lat", "latitude"]:
        if field in row.index:
            lat = safe_float(row[field])
            if lat is not None:
                break

    for field in ["lon", "longitude"]:
        if field in row.index:
            lon = safe_float(row[field])
            if lon is not None:
                break

    # Geometry fallback.
    geometry = row.get("geometry")

    if (lat is None or lon is None) and geometry is not None:
        try:
            centroid = geometry.centroid

            lon = float(centroid.x)
            lat = float(centroid.y)

        except Exception:
            pass

    return lat, lon


def row_to_record(row, source_name: str):
    merchant_name = clean_name(row.get("name"))

    if not is_valid_name(merchant_name):
        return None

    canonical = canonicalize_name(merchant_name)

    category, subcategory = detect_category(row)

    city = extract_city(row)
    state = extract_state(row)

    lat, lon = get_lat_lon(row)

    aliases = extract_aliases(row, merchant_name)

    return {
        "merchant_name": merchant_name,
        "canonical_name": canonical,
        "aliases": aliases,
        "category": category,
        "subcategory": subcategory,
        "country": "India",
        "state": state,
        "city": city,
        "latitude": lat,
        "longitude": lon,
        "source": source_name,
    }


# ============================================================
# DEDUPLICATION
# ============================================================

def merge_aliases(a: List[str], b: List[str]) -> List[str]:
    output = []
    seen = set()

    for value in list(a or []) + list(b or []):
        value = clean_name(value)

        if not value:
            continue

        key = canonicalize_name(value)

        if key in seen:
            continue

        seen.add(key)
        output.append(value)

    return output


def choose_better(existing, incoming):
    """
    Prefer records with:
      1. category
      2. city/state
      3. coordinates
      4. longer alias set
    """

    def score(record):
        value = 0

        if record.get("category"):
            value += 3

        if record.get("subcategory"):
            value += 2

        if record.get("state"):
            value += 1

        if record.get("city"):
            value += 1

        if record.get("latitude") is not None:
            value += 2

        if record.get("longitude") is not None:
            value += 2

        value += min(len(record.get("aliases", [])), 5)

        return value

    return incoming if score(incoming) > score(existing) else existing


def deduplicate_records(records: Iterable[dict]):
    unique = {}
    duplicates = []

    for record in records:
        key = record["canonical_name"]

        if key not in unique:
            unique[key] = record
            continue

        existing = unique[key]

        duplicates.append(
            {
                "canonical_name": key,
                "kept_name": existing["merchant_name"],
                "duplicate_name": record["merchant_name"],
                "kept_city": existing.get("city", ""),
                "duplicate_city": record.get("city", ""),
                "kept_source": existing.get("source", ""),
                "duplicate_source": record.get("source", ""),
            }
        )

        existing["aliases"] = merge_aliases(
            existing.get("aliases", []),
            record.get("aliases", []),
        )

        # Fill missing data from duplicate.
        if not existing.get("category") and record.get("category"):
            existing["category"] = record["category"]

        if not existing.get("subcategory") and record.get("subcategory"):
            existing["subcategory"] = record["subcategory"]

        if not existing.get("state") and record.get("state"):
            existing["state"] = record["state"]

        if not existing.get("city") and record.get("city"):
            existing["city"] = record["city"]

        if existing.get("latitude") is None and record.get("latitude") is not None:
            existing["latitude"] = record["latitude"]

        if existing.get("longitude") is None and record.get("longitude") is not None:
            existing["longitude"] = record["longitude"]

        unique[key] = choose_better(existing, record)

    return list(unique.values()), duplicates


# ============================================================
# PROCESS ONE PBF
# ============================================================

def process_pbf(pbf_path: Path, source_name: str):
    records = []

    for dataframe in read_osm_named_objects(pbf_path):

        if dataframe is None or len(dataframe) == 0:
            continue

        # Keep only named records.
        if "name" not in dataframe.columns:
            continue

        dataframe = dataframe[
            dataframe["name"].notna()
        ]

        print(
            f"[OSM] Processing {len(dataframe):,} named objects "
            f"from {pbf_path.name}"
        )

        for _, row in tqdm(
            dataframe.iterrows(),
            total=len(dataframe),
            desc=f"Extracting {pbf_path.name}",
        ):

            record = row_to_record(
                row,
                source_name,
            )

            if record is not None:
                records.append(record)

    return records


# ============================================================
# VALIDATION
# ============================================================

def validate_records(records: List[dict]):
    errors = []

    canonical_names = set()

    for index, record in enumerate(records):

        for column in OUTPUT_COLUMNS:
            if column not in record:
                errors.append(
                    f"row={index}: missing column {column}"
                )

        canonical = record.get("canonical_name", "")

        if not canonical:
            errors.append(
                f"row={index}: empty canonical_name"
            )

        if canonical in canonical_names:
            errors.append(
                f"row={index}: duplicate canonical_name={canonical}"
            )

        canonical_names.add(canonical)

        if record.get("country") != "India":
            errors.append(
                f"row={index}: country is not India"
            )

        lat = record.get("latitude")
        lon = record.get("longitude")

        if lat is not None and not (-90 <= lat <= 90):
            errors.append(
                f"row={index}: invalid latitude={lat}"
            )

        if lon is not None and not (-180 <= lon <= 180):
            errors.append(
                f"row={index}: invalid longitude={lon}"
            )

    return errors


# ============================================================
# OUTPUT
# ============================================================

def write_csv(records, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)

    with open(
        path,
        "w",
        encoding="utf-8-sig",
        newline="",
    ) as f:

        writer = csv.DictWriter(
            f,
            fieldnames=OUTPUT_COLUMNS,
        )

        writer.writeheader()

        for record in records:

            output = dict(record)

            output["aliases"] = "|".join(
                record.get("aliases", [])
            )

            writer.writerow(output)


def write_json(records, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)

    with open(
        path,
        "w",
        encoding="utf-8",
    ) as f:

        json.dump(
            records,
            f,
            ensure_ascii=False,
            indent=2,
        )


def write_duplicate_report(duplicates, path: Path):
    columns = [
        "canonical_name",
        "kept_name",
        "duplicate_name",
        "kept_city",
        "duplicate_city",
        "kept_source",
        "duplicate_source",
    ]

    with open(
        path,
        "w",
        encoding="utf-8-sig",
        newline="",
    ) as f:

        writer = csv.DictWriter(
            f,
            fieldnames=columns,
        )

        writer.writeheader()

        writer.writerows(duplicates)


def generate_statistics(records, duplicates):
    categories = Counter()
    subcategories = Counter()
    cities = Counter()
    states = Counter()

    missing_category = 0
    missing_city = 0
    missing_state = 0
    missing_coordinates = 0

    for record in records:

        category = record.get("category")

        if category:
            categories[category] += 1
        else:
            missing_category += 1

        if record.get("subcategory"):
            subcategories[record["subcategory"]] += 1

        if record.get("city"):
            cities[record["city"]] += 1
        else:
            missing_city += 1

        if record.get("state"):
            states[record["state"]] += 1
        else:
            missing_state += 1

        if (
            record.get("latitude") is None
            or record.get("longitude") is None
        ):
            missing_coordinates += 1

    return {
        "total_merchants": len(records),

        "categories": dict(
            categories.most_common()
        ),

        "subcategories": dict(
            subcategories.most_common()
        ),

        "top_cities": dict(
            cities.most_common(100)
        ),

        "top_states": dict(
            states.most_common(100)
        ),

        "duplicates_removed": len(duplicates),

        "missing_category_count": missing_category,

        "missing_city_count": missing_city,

        "missing_state_count": missing_state,

        "missing_coordinates_count": missing_coordinates,

        "validation": {
            "duplicate_canonical_names": 0,
            "fabricated_records": 0,
        },
    }


def write_statistics(stats, path: Path):
    with open(
        path,
        "w",
        encoding="utf-8",
    ) as f:

        json.dump(
            stats,
            f,
            ensure_ascii=False,
            indent=2,
        )


def write_source_report(
    output_path: Path,
    pbf_files: List[Path],
    record_count: int,
    duplicate_count: int,
    validation_errors: List[str],
):
    lines = []

    lines.append("# Merchant Database Source Report")
    lines.append("")
    lines.append(
        "This database was generated from publicly available "
        "OpenStreetMap data distributed by Geofabrik."
    )
    lines.append("")

    lines.append("## Sources")
    lines.append("")

    for pbf in pbf_files:
        lines.append(f"- `{pbf}`")

    lines.append("")

    lines.append("## Processing")
    lines.append("")
    lines.append(
        "- Extracted named OSM POIs and named mapped buildings."
    )
    lines.append(
        "- Preserved original merchant names."
    )
    lines.append(
        "- Removed emojis."
    )
    lines.append(
        "- Normalized whitespace."
    )
    lines.append(
        "- Generated uppercase canonical names."
    )
    lines.append(
        "- Merged exact canonical-name duplicates."
    )
    lines.append(
        "- Merged aliases available through OSM tags."
    )
    lines.append(
        "- Classified records using OSM tags."
    )
    lines.append(
        "- Records without a usable name were discarded."
    )
    lines.append(
        "- No synthetic merchant names were generated."
    )
    lines.append("")

    lines.append("## Results")
    lines.append("")
    lines.append(f"- Final merchants: **{record_count:,}**")
    lines.append(f"- Duplicate records removed: **{duplicate_count:,}**")
    lines.append(
        f"- Validation errors: **{len(validation_errors):,}**"
    )
    lines.append("")

    lines.append("## Licensing")
    lines.append("")
    lines.append(
        "The Geofabrik India extract is derived from "
        "OpenStreetMap Contributors and is distributed under "
        "the OpenStreetMap ODbL 1.0 license."
    )
    lines.append("")
    lines.append(
        "Before commercial deployment, review and satisfy the "
        "applicable ODbL attribution/share-alike requirements."
    )
    lines.append("")

    if validation_errors:
        lines.append("## Validation Errors")
        lines.append("")

        for error in validation_errors[:100]:
            lines.append(f"- {error}")

        if len(validation_errors) > 100:
            lines.append(
                f"- ... {len(validation_errors) - 100:,} more"
            )

    output_path.write_text(
        "\n".join(lines),
        encoding="utf-8",
    )


# ============================================================
# MAIN
# ============================================================

def parse_args():
    parser = argparse.ArgumentParser(
        description="Build an India merchant database from public OSM data."
    )

    parser.add_argument(
        "--pbf",
        type=str,
        help="Existing .osm.pbf file to process.",
    )

    parser.add_argument(
        "--download",
        choices=["india"],
        help="Download the current India extract.",
    )

    parser.add_argument(
        "--download-regions",
        action="store_true",
        help="Download all six India regional extracts.",
    )

    parser.add_argument(
        "--data-dir",
        default=str(DEFAULT_DATA_DIR),
        help="Directory for downloaded PBF files.",
    )

    parser.add_argument(
        "--output-dir",
        default=str(DEFAULT_OUTPUT_DIR),
        help="Directory for output files.",
    )

    return parser.parse_args()


def main():

    args = parse_args()

    data_dir = Path(args.data_dir)
    output_dir = Path(args.output_dir)

    data_dir.mkdir(
        parents=True,
        exist_ok=True,
    )

    output_dir.mkdir(
        parents=True,
        exist_ok=True,
    )

    pbf_files = []

    # --------------------------------------------------------
    # Existing PBF
    # --------------------------------------------------------

    if args.pbf:
        pbf = Path(args.pbf)

        if not pbf.exists():
            print(f"ERROR: PBF does not exist: {pbf}")
            sys.exit(1)

        pbf_files.append(pbf)

    # --------------------------------------------------------
    # Whole India download
    # --------------------------------------------------------

    elif args.download == "india":

        destination = (
            data_dir / "india-latest.osm.pbf"
        )

        pbf = download_file(
            INDIA_URL,
            destination,
        )

        pbf_files.append(pbf)

    # --------------------------------------------------------
    # Regional downloads
    # --------------------------------------------------------

    elif args.download_regions:

        for region, url in REGIONAL_URLS.items():

            filename = url.rsplit("/", 1)[-1]

            destination = data_dir / filename

            pbf = download_file(
                url,
                destination,
            )

            pbf_files.append(pbf)

    else:
        print(
            "Specify one of:\n"
            "  --pbf FILE\n"
            "  --download india\n"
            "  --download-regions"
        )

        sys.exit(1)

    # --------------------------------------------------------
    # Extract
    # --------------------------------------------------------

    all_records = []

    for pbf in pbf_files:

        print()
        print("=" * 70)
        print(f"PROCESSING: {pbf}")
        print("=" * 70)

        records = process_pbf(
            pbf,
            source_name="OpenStreetMap / Geofabrik India",
        )

        print(
            f"[OSM] Extracted {len(records):,} "
            f"named merchant candidates."
        )

        all_records.extend(records)

    print()
    print(
        f"[TOTAL] Raw named records: "
        f"{len(all_records):,}"
    )

    # --------------------------------------------------------
    # Deduplicate
    # --------------------------------------------------------

    print("[DEDUP] Deduplicating canonical merchant names...")

    records, duplicates = deduplicate_records(
        all_records
    )

    print(
        f"[DEDUP] Final unique merchants: "
        f"{len(records):,}"
    )

    print(
        f"[DEDUP] Duplicates removed: "
        f"{len(duplicates):,}"
    )

    # --------------------------------------------------------
    # Sort
    # --------------------------------------------------------

    records.sort(
        key=lambda x: (
            x.get("canonical_name", ""),
            x.get("city", ""),
            x.get("state", ""),
        )
    )

    # --------------------------------------------------------
    # Validate
    # --------------------------------------------------------

    print("[VALIDATE] Running validation...")

    validation_errors = validate_records(records)

    if validation_errors:

        print(
            f"[VALIDATE] FAILED: "
            f"{len(validation_errors):,} errors."
        )

        for error in validation_errors[:20]:
            print("  ", error)

        # Never silently produce a database with duplicate
        # canonical names.
        sys.exit(2)

    print("[VALIDATE] PASS")

    # --------------------------------------------------------
    # Write CSV
    # --------------------------------------------------------

    csv_path = (
        output_dir / "merchant_database.csv"
    )

    write_csv(
        records,
        csv_path,
    )

    # --------------------------------------------------------
    # Write JSON
    # --------------------------------------------------------

    json_path = (
        output_dir / "merchant_database.json"
    )

    write_json(
        records,
        json_path,
    )

    # --------------------------------------------------------
    # Statistics
    # --------------------------------------------------------

    stats = generate_statistics(
        records,
        duplicates,
    )

    stats_path = (
        output_dir / "merchant_statistics.json"
    )

    write_statistics(
        stats,
        stats_path,
    )

    # --------------------------------------------------------
    # Duplicate report
    # --------------------------------------------------------

    duplicate_path = (
        output_dir / "duplicate_report.csv"
    )

    write_duplicate_report(
        duplicates,
        duplicate_path,
    )

    # --------------------------------------------------------
    # Source report
    # --------------------------------------------------------

    source_report_path = (
        output_dir / "source_report.md"
    )

    write_source_report(
        source_report_path,
        pbf_files,
        len(records),
        len(duplicates),
        validation_errors,
    )

    # --------------------------------------------------------
    # Final verification
    # --------------------------------------------------------

    print()
    print("=" * 70)
    print("BUILD COMPLETE")
    print("=" * 70)

    print(
        f"Total merchants : {len(records):,}"
    )

    print(
        f"Duplicates removed : {len(duplicates):,}"
    )

    print()
    print("Files:")

    print(f"  {csv_path}")
    print(f"  {json_path}")
    print(f"  {stats_path}")
    print(f"  {duplicate_path}")
    print(f"  {source_report_path}")

    if len(records) < 2000:
        print()
        print(
            "WARNING: fewer than 2,000 unique merchants "
            "were produced from this OSM extraction."
        )

        print(
            "This is not a reason to fabricate records. "
            "Add another permitted public source and rerun."
        )


if __name__ == "__main__":
    main()
