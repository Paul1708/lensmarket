"""Wertet k6-JSON-Output (--out json=results.json) aus und erzeugt ein
Latenz/VU-Zeitreihendiagramm. Unterstuetzt einen oder mehrere Laeufe zum
direkten Vergleich (z.B. Baseline vs. skaliert).

Nutzung:
    python analyze.py 1x=results-1x.json 3x=results-3x.json
    python analyze.py 1x=results-1x.json -o 1x_latency.png

Voraussetzungen: siehe requirements.txt (pip install -r requirements.txt)
"""

import argparse
import json
import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd


def load_run(path: str, every_nth: int = 1) -> pd.DataFrame:
    durations, vus = [], []
    seen = 0
    kept = 0
    with open(path, encoding="utf-8") as f:
        for i, line in enumerate(f):
            line = line.strip()
            if not line:
                continue
            # Guenstiger String-Check vor dem teuren json.loads(): ca. 13
            # verschiedene Metriken pro Request, wir brauchen nur zwei davon.
            # Bei grossen Dateien (z.B. breakpointtest.js mit vielen tausend
            # VUs) spart das den Grossteil der Parse-Zeit.
            if '"metric":"http_req_duration"' not in line and '"metric":"vus"' not in line:
                continue
            seen += 1
            if every_nth > 1 and seen % every_nth != 0:
                continue
            kept += 1
            if kept % 200_000 == 0:
                print(f"  ... {kept} Punkte gelesen ({i + 1} Zeilen durchsucht)", file=sys.stderr)

            d = json.loads(line)
            if d.get("type") != "Point":
                continue
            metric = d["metric"]
            data = d["data"]
            t = pd.to_datetime(data["time"])
            if metric == "http_req_duration":
                tags = data.get("tags", {})
                durations.append(
                    {
                        "time": t,
                        "value": data["value"],
                        "name": tags.get("name", "unknown"),
                        "status": tags.get("status"),
                    }
                )
            elif metric == "vus":
                vus.append({"time": t, "value": data["value"]})

    df_dur = pd.DataFrame(durations).set_index("time").sort_index()
    df_vus = pd.DataFrame(vus).set_index("time").sort_index()

    dur_5s = df_dur["value"].resample("5s").agg(
        avg_ms="mean", p95_ms=lambda s: s.quantile(0.95), req_count="count"
    )
    vus_5s = df_vus["value"].resample("5s").mean().rename("vus")
    combined = dur_5s.join(vus_5s, how="left")
    # Relative Zeit ab Laufstart, damit sich Laeufe mit unterschiedlichem
    # Startzeitpunkt uebereinanderlegen lassen.
    combined["t_rel_s"] = (combined.index - combined.index.min()).total_seconds()

    print(f"\n=== {path} ===")
    print(f"Requests total: {len(df_dur)}")
    print("Latenz nach Endpunkt-Typ (name-Tag):")
    print(
        df_dur.groupby("name")["value"]
        .agg(n="count", avg="mean", p50=lambda s: s.quantile(0.5), p95=lambda s: s.quantile(0.95), max="max")
        .round(1)
    )
    fail_rate = (df_dur["status"] != "200").mean() * 100
    print(f"Anteil Requests mit Status != 200: {fail_rate:.2f}%")

    return combined


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "runs",
        nargs="+",
        help="Ein oder mehrere Läufe als label=pfad, z.B. 1x=results-1x.json 3x=results-3x.json",
    )
    parser.add_argument("-o", "--output", default="comparison.png", help="Ausgabedatei fuer das Diagramm")
    parser.add_argument(
        "--every-nth",
        type=int,
        default=1,
        help="Nur jeden n-ten relevanten Messpunkt einlesen (Downsampling fuer sehr grosse Dateien, z.B. --every-nth 5)",
    )
    args = parser.parse_args()

    fig, ax1 = plt.subplots(figsize=(10, 5))
    ax2 = ax1.twinx()
    colors = plt.rcParams["axes.prop_cycle"].by_key()["color"]

    for i, run in enumerate(args.runs):
        if "=" not in run:
            print(f"Ungültiges Argument '{run}', erwartet label=pfad", file=sys.stderr)
            sys.exit(1)
        label, path = run.split("=", 1)
        df = load_run(path, every_nth=args.every_nth)
        color = colors[i % len(colors)]
        ax1.plot(df["t_rel_s"], df["p95_ms"], color=color, label=f"{label} p95 Latenz (ms)")
        ax2.plot(df["t_rel_s"], df["vus"], color=color, alpha=0.3, linestyle="--", label=f"{label} VUs")

    ax1.set_xlabel("Zeit seit Laufstart (s)")
    ax1.set_ylabel("p95 Latenz (ms)")
    ax2.set_ylabel("Virtuelle Nutzer (VUs)")
    ax1.legend(loc="upper left")
    ax2.legend(loc="upper right")
    plt.title("k6 Lasttest: Latenz vs. VU-Ramp")
    fig.tight_layout()
    plt.savefig(args.output, dpi=150)
    print(f"\nDiagramm gespeichert unter: {args.output}")


if __name__ == "__main__":
    main()
