#!/usr/bin/env python3
"""Project the shared pattern+mission embedding corpus into a 2D scatter.

Reads the merged MiniLM corpus from `resources/notions/minilm_corpus_embeddings.json`
and emits:

  - `corpus_projection_2d.json`  : point-wise coords + metadata
  - `corpus_projection_2d.png`   : large scatter image

Patterns are colored by pattern family (`<family>/<pattern-id>`); missions are
rendered as dark `x` markers so they can be seen against the pattern field.
The projection is deterministic PCA via NumPy SVD so it remains available
without a scikit-learn dependency.
"""

from __future__ import annotations

import argparse
import colorsys
import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Dict, Iterable, List, Tuple

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import colors as mcolors
import numpy as np


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Project the shared embedding corpus into 2D.")
    parser.add_argument(
        "--corpus",
        default="resources/notions/minilm_corpus_embeddings.json",
        help="Merged corpus embeddings JSON (default: resources/notions/minilm_corpus_embeddings.json).",
    )
    parser.add_argument(
        "--out-json",
        default="resources/notions/corpus_projection_2d.json",
        help="Output point JSON path.",
    )
    parser.add_argument(
        "--out-png",
        default="resources/notions/corpus_projection_2d.png",
        help="Output scatter PNG path.",
    )
    parser.add_argument(
        "--pattern-size",
        type=float,
        default=10.0,
        help="Matplotlib marker size for pattern dots.",
    )
    parser.add_argument(
        "--mission-size",
        type=float,
        default=28.0,
        help="Matplotlib marker size for mission crosses.",
    )
    return parser.parse_args()


def load_corpus(path: Path) -> List[Dict[str, object]]:
    payload = json.loads(path.read_text())
    if not isinstance(payload, list):
        raise SystemExit(f"Expected list payload in {path}")
    return payload


def family_for(entry: Dict[str, object]) -> str:
    entry_type = entry.get("type")
    if entry_type == "pattern":
        ident = str(entry.get("id", "unknown/unknown"))
        return ident.split("/", 1)[0]
    if entry_type == "mission":
        return f"mission@{entry.get('home_repo', 'unknown')}"
    return str(entry_type or "unknown")


def deterministic_color(label: str) -> str:
    digest = hashlib.sha1(label.encode("utf-8")).digest()
    hue = int.from_bytes(digest[:2], "big") / 65535.0
    sat = 0.55 + (digest[2] / 255.0) * 0.25
    val = 0.70 + (digest[3] / 255.0) * 0.20
    r, g, b = colorsys.hsv_to_rgb(hue, sat, val)
    return "#{:02x}{:02x}{:02x}".format(int(r * 255), int(g * 255), int(b * 255))


def palette_for_families(families: List[str], family_counts: Counter[str]) -> Dict[str, str]:
    ranked = [family for family, _count in family_counts.most_common()]
    tab20 = list(plt.get_cmap("tab20").colors)
    set3 = list(plt.get_cmap("Set3").colors)
    base = tab20 + set3
    palette: Dict[str, str] = {}
    for idx, family in enumerate(ranked):
        if idx < len(base):
            palette[family] = mcolors.to_hex(base[idx])
        else:
            palette[family] = deterministic_color(family)
    for family in families:
        palette.setdefault(family, deterministic_color(family))
    return palette


def pca_2d(vectors: np.ndarray) -> np.ndarray:
    centered = vectors - vectors.mean(axis=0, keepdims=True)
    # deterministic PCA without sklearn
    _, _, vt = np.linalg.svd(centered, full_matrices=False)
    coords = centered @ vt[:2].T
    coords = coords - coords.mean(axis=0, keepdims=True)
    max_abs = float(np.abs(coords).max()) or 1.0
    return coords / max_abs


def round_point(xs: Iterable[float]) -> List[float]:
    return [round(float(x), 6) for x in xs]


def prepare_points(entries: List[Dict[str, object]], coords: np.ndarray) -> Tuple[List[Dict[str, object]], Dict[str, str]]:
    families = sorted({family_for(entry) for entry in entries})
    family_counts = Counter(family_for(entry) for entry in entries if entry.get("type") == "pattern")
    palette = palette_for_families(families, family_counts)
    points = []
    for entry, coord in zip(entries, coords):
        entry_type = str(entry.get("type", "unknown"))
        family = family_for(entry)
        point = {
            "id": entry.get("id"),
            "title": entry.get("title"),
            "type": entry_type,
            "family": family,
            "color": palette[family],
            "coord": round_point(coord),
        }
        if entry_type == "mission":
            point["home_repo"] = entry.get("home_repo")
            point["phase"] = entry.get("phase")
            point["status"] = entry.get("status")
        else:
            point["source"] = entry.get("source")
        points.append(point)
    return points, palette


def render(points: List[Dict[str, object]], out_path: Path, pattern_size: float, mission_size: float) -> None:
    pattern_points = [p for p in points if p["type"] == "pattern"]
    mission_points = [p for p in points if p["type"] == "mission"]
    fig, ax = plt.subplots(figsize=(20, 16), dpi=220)
    ax.set_facecolor("#fbfbf8")
    fig.patch.set_facecolor("#fbfbf8")

    # Render one family at a time so colors stay coherent without a huge legend.
    for family in sorted({p["family"] for p in pattern_points}):
        fam_points = [p for p in pattern_points if p["family"] == family]
        xs = [p["coord"][0] for p in fam_points]
        ys = [p["coord"][1] for p in fam_points]
        ax.scatter(
            xs,
            ys,
            s=pattern_size,
            c=[fam_points[0]["color"]],
            alpha=0.58,
            linewidths=0.0,
            marker="o",
            rasterized=True,
        )

    if mission_points:
        ax.scatter(
            [p["coord"][0] for p in mission_points],
            [p["coord"][1] for p in mission_points],
            s=mission_size,
            c="#1f1f1f",
            alpha=0.88,
            linewidths=0.45,
            marker="x",
        )

    ax.set_title("Shared Corpus 2D Projection: Patterns by Family, Missions as Crosses", fontsize=18, pad=20)
    ax.set_xlabel("PC1")
    ax.set_ylabel("PC2")
    ax.grid(True, color="#d8d8cf", linewidth=0.45, alpha=0.45)

    # Annotate a few large pattern families plus the mission center-of-mass.
    family_counts = Counter(p["family"] for p in pattern_points)
    for family, _count in family_counts.most_common(14):
        fam_points = [p for p in pattern_points if p["family"] == family]
        cx = float(np.mean([p["coord"][0] for p in fam_points]))
        cy = float(np.mean([p["coord"][1] for p in fam_points]))
        medoid = min(
            fam_points,
            key=lambda p: (p["coord"][0] - cx) ** 2 + (p["coord"][1] - cy) ** 2,
        )
        mx, my = medoid["coord"]
        ax.scatter(
            [mx],
            [my],
            s=44,
            c=[fam_points[0]["color"]],
            alpha=0.95,
            linewidths=0.75,
            edgecolors="#111111",
            marker="h",
            zorder=5,
        )
        ax.text(
            mx,
            my,
            family,
            fontsize=7,
            color="#202020",
            ha="left",
            va="bottom",
            bbox={"facecolor": "#ffffff", "alpha": 0.72, "linewidth": 0.0, "pad": 1.2},
            zorder=6,
        )

    if mission_points:
        mx = float(np.mean([p["coord"][0] for p in mission_points]))
        my = float(np.mean([p["coord"][1] for p in mission_points]))
        ax.text(
            mx,
            my,
            "missions",
            fontsize=8,
            color="#111111",
            ha="center",
            va="center",
            bbox={"facecolor": "#fff2b6", "alpha": 0.7, "linewidth": 0.0, "pad": 1.5},
        )

    legend_fams = family_counts.most_common(12)
    legend_lines = ["Top pattern families:"]
    for family, count in legend_fams:
        legend_lines.append(f"{family} ({count})")
    ax.text(
        0.012,
        0.988,
        "\n".join(legend_lines),
        transform=ax.transAxes,
        fontsize=8,
        color="#111111",
        ha="left",
        va="top",
        bbox={"facecolor": "#ffffff", "alpha": 0.82, "linewidth": 0.0, "pad": 3.0},
    )

    fig.tight_layout()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out_path)
    plt.close(fig)


def main() -> None:
    args = parse_args()
    corpus_path = Path(args.corpus)
    out_json = Path(args.out_json)
    out_png = Path(args.out_png)

    entries = load_corpus(corpus_path)
    vectors = np.asarray([entry["vector"] for entry in entries], dtype=np.float64)
    coords = pca_2d(vectors)
    points, palette = prepare_points(entries, coords)

    family_counts = Counter(point["family"] for point in points if point["type"] == "pattern")
    payload = {
        "source": str(corpus_path),
        "n_points": len(points),
        "n_patterns": sum(1 for point in points if point["type"] == "pattern"),
        "n_missions": sum(1 for point in points if point["type"] == "mission"),
        "projection": "pca-svd",
        "family_palette": palette,
        "family_counts": dict(sorted(family_counts.items())),
        "points": points,
    }

    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(json.dumps(payload, indent=2))
    render(points, out_png, args.pattern_size, args.mission_size)

    print(f"Wrote {out_json}")
    print(f"Wrote {out_png}")
    print(f"points={payload['n_points']} patterns={payload['n_patterns']} missions={payload['n_missions']}")
    print("largest families:")
    for family, count in family_counts.most_common(10):
        print(f"  {family}: {count}")


if __name__ == "__main__":
    main()
