#!/usr/bin/env python3
"""
Calage du modèle de neige sur des relevés officiels de stations.
Télécharge une seule fois les données journalières des points de mesure, puis teste
des milliers de combinaisons de réglages et affiche les meilleures.
Utilisation : python3 tools/calibrate.py
"""
import itertools, math, os, sys
from datetime import date
sys.path.insert(0, os.path.dirname(__file__))
from snow_feed import API, PARAMS, fetch_daily, rows, season_start, step

# (nom, lat, lon, altitude, date du relevé, hauteur officielle en cm, source)
OBS = [
    ("Val Thorens station", 45.2979, 6.5800, 2300, "2026-11-25", 95, "valthorens.com"),
    ("Val Thorens haut", 45.2979, 6.5800, 3200, "2026-11-25", 165, "valthorens.com"),
    ("Val Thorens station", 45.2979, 6.5800, 2300, "2026-11-22", 55, "hauteurdeneige.com"),
    ("Val Thorens haut", 45.2979, 6.5800, 3200, "2026-11-22", 105, "hauteurdeneige.com"),
    ("Tignes Le Lac", 45.4683, 6.9056, 2100, "2026-11-25", 116, "app Tignes"),
    ("Tignes Grande Motte", 45.4683, 6.9056, 3300, "2026-11-25", 200, "app Tignes"),
    ("Les Arcs haut", 45.5725, 6.8283, 3226, "2026-11-24", 150, "skipass.com"),
    ("Les Arcs bas", 45.5725, 6.8283, 1200, "2026-11-24", 0, "skipass.com"),
    ("3 Vallées bas (Le Praz)", 45.4153, 6.6347, 1100, "2026-11-21", 0, "skiresort.fr"),
]

def main():
    today = date.fromisoformat(max(o[4] for o in OBS))
    start = season_start(today)
    pts = sorted({(o[1], o[2], o[3]) for o in OBS})
    past = (date.today() - start).days + 1
    daily = fetch_daily(pts, API, {"daily": "temperature_2m_max,temperature_2m_min,precipitation_sum",
                                   "past_days": min(92, past), "forecast_days": 1}, past)
    series = {p: [(date.fromisoformat(d), tx, tn, pr) for d, tx, tn, pr, _ in rows(dl)] for p, dl in zip(pts, daily)}

    def simulate(P):
        out = {}
        for p, ser in series.items():
            st = {"swe": 0.0, "depth": 0.0}
            by_day = {}
            for d, tx, tn, pr in ser:
                if d < start:
                    continue
                st = step(st, tx, tn, pr, P)
                by_day[d.isoformat()] = st["depth"] / 10.0
            out[p] = by_day
        return out

    def error(P):
        sim = simulate(P)
        e = 0.0
        for name, la, lo, alt, d, obs, _ in OBS:
            m = sim[(la, lo, alt)].get(d, 0.0)
            e += math.log((m + 15) / (obs + 15)) ** 2
        return e / len(OBS), sim

    grid = {
        "snow_below": [-0.5, 0.0, 0.5, 1.0],
        "rain_above": [2.0, 2.5, 3.0, 3.5],
        "ddf": [2.0, 3.0, 4.0],
        "new_density": [0.08, 0.10, 0.12],
        "settled": [0.30, 0.38, 0.45],
        "settle_rate": [0.05, 0.10, 0.15],
        "precip_factor": [0.6, 0.7, 0.8, 0.9, 1.0],
    }
    base_err, _ = error(PARAMS)
    results = []
    keys = list(grid)
    for combo in itertools.product(*grid.values()):
        P = dict(zip(keys, combo))
        if P["rain_above"] <= P["snow_below"]:
            continue
        results.append((error(P)[0], P))
    results.sort(key=lambda r: r[0])
    print(f"Erreur des réglages actuels : {base_err:.4f}")
    for e, P in results[:5]:
        print(f"Erreur {e:.4f} : {P}")
    best = results[0][1]
    _, sim = error(best)
    print("Comparaison avec les relevés (meilleurs réglages) :")
    for name, la, lo, alt, d, obs, src in OBS:
        print(f"  {name:26s} {alt:5d} m  {d}  officiel {obs:4d} cm  modèle {sim[(la, lo, alt)].get(d, 0):6.0f} cm  ({src})")
    print("MEILLEURS_REGLAGES =", best)

if __name__ == "__main__":
    main()
