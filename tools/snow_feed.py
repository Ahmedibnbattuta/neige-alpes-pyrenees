#!/usr/bin/env python3
"""
Calcule la hauteur de neige de chaque station, au bas, au milieu et au sommet du domaine,
et écrit web/snow.json (lu par l'application et par le site).

Pourquoi : la hauteur de neige « brute » des modèles météo est celle d'une maille de 1-2 km,
elle ne dépend pas vraiment de l'altitude demandée (on obtenait parfois moins de neige en haut qu'en bas).
Ici on simule le manteau neigeux à chaque altitude depuis le début de la saison :
  - précipitations journalières du modèle (Open-Meteo) ;
  - température ramenée à l'altitude exacte (Open-Meteo corrige la température selon l'altitude) ;
  - pluie ou neige selon la température, fonte par temps doux (degré-jour), tassement du manteau.
Plus il fait froid en altitude, plus la neige tient : le sommet a donc logiquement plus de neige.

État incrémental : on repart de l'état publié précédemment (dans snow.json) pour ne demander que les
derniers jours à Open-Meteo. Sans état, on recalcule toute la saison, par lots, en respectant la limite
de 600 appels par minute de l'API gratuite.

Utilisation : python3 tools/snow_feed.py <stations.json> <sortie snow.json> [url du snow.json publié]
Sans réseau (tests) : les fonctions du modèle sont importables.
"""
import json, sys, time, urllib.error, urllib.request, urllib.parse
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

API = "https://api.open-meteo.com/v1/forecast"
ARCHIVE = "https://archive-api.open-meteo.com/v1/archive"
LEVELS = ("base", "mid", "top")
FORECAST_DAYS = 7
MAX_BATCH_WEIGHT = 450      # appels « pondérés » par requête (limite : 600 / minute)
PAUSE_BETWEEN_BATCHES = 62  # secondes

# ---------------------------------------------------------------------------
# Modèle de manteau neigeux (pas journalier)
# ---------------------------------------------------------------------------
# Réglages du modèle (calibrés avec tools/calibrate.py sur des relevés officiels de stations).
PARAMS = {
    "snow_below": 0.0,     # °C (moyenne journalière) : en dessous, toute la précipitation tombe en neige
    "rain_above": 2.5,     # °C : au-dessus, tout tombe en pluie
    "ddf": 3.5,            # mm d'eau fondus par °C au-dessus de 0 et par jour (fonte degré-jour)
    "new_density": 0.10,   # densité de la neige fraîche (0,10 = 10 cm de neige pour 1 cm d'eau)
    "settled": 0.38,       # densité vers laquelle le manteau se tasse
    "settle_rate": 0.07,   # part de l'excès d'épaisseur résorbée chaque jour
    "precip_factor": 1.0,  # correction globale des précipitations du modèle (vent, sublimation…)
}
MODEL_VERSION = 2  # à incrémenter quand les réglages changent : force un recalcul complet


def season_start(today: date) -> date:
    """Début de saison : 1er octobre (1er septembre pour les mois d'été / début d'automne)."""
    y = today.year if today.month >= 10 else today.year - 1
    if 7 <= today.month <= 9:
        return date(today.year, 9, 1)
    return date(y, 10, 1)


def step(state, tmax, tmin, precip_mm, P=None):
    """Avance l'état d'un jour. state = {"swe": mm d'eau, "depth": mm de neige}."""
    P = P or PARAMS
    swe, depth = state["swe"], state["depth"]
    if tmax is None or tmin is None:
        return {"swe": swe, "depth": depth}
    tmean = (tmax + tmin) / 2.0
    p = max(0.0, precip_mm or 0.0) * P["precip_factor"]
    # Part de neige
    if tmean <= P["snow_below"]:
        frac = 1.0
    elif tmean >= P["rain_above"]:
        frac = 0.0
    else:
        frac = (P["rain_above"] - tmean) / (P["rain_above"] - P["snow_below"])
    snow_we = p * frac
    swe += snow_we
    depth += snow_we / P["new_density"]
    # Fonte (degré-jour)
    melt = min(P["ddf"] * max(0.0, tmean), swe)
    if swe > 0:
        depth *= (swe - melt) / swe
    swe -= melt
    # Tassement vers la densité d'un manteau ancien
    if swe > 0.01:
        settled = swe / P["settled"]
        if depth > settled:
            depth = settled + (depth - settled) * (1 - P["settle_rate"])
    else:
        swe, depth = 0.0, 0.0
    return {"swe": round(swe, 2), "depth": round(depth, 1)}


def depth_cm(state):
    return int(round(state["depth"] / 10.0))


# ---------------------------------------------------------------------------
# Accès Open-Meteo (par lots, en respectant la limite d'appels)
# ---------------------------------------------------------------------------
def weight(days):
    """Poids approximatif d'un point dans le décompte d'Open-Meteo (au-delà de 14 jours, ça compte plus)."""
    return max(1.0, days / 14.0)


def get_json(url, tries=4):
    for k in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "neige-alpes-pyrenees (github.com/Ahmedibnbattuta)"})
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.loads(r.read().decode())
        except urllib.error.HTTPError as e:
            if e.code == 429 and k < tries - 1:
                print("  429 : pause 65 s", flush=True)
                time.sleep(65)
                continue
            raise
        except Exception:
            if k < tries - 1:
                time.sleep(10)
                continue
            raise


def fetch_daily(points, base_url, params, days_per_point):
    """points = [(lat, lon, elev)] -> liste de dict 'daily' (un par point), par lots."""
    out = []
    per = weight(days_per_point)
    size = max(1, int(MAX_BATCH_WEIGHT // per))
    batches = [points[i:i + size] for i in range(0, len(points), size)]
    for n, b in enumerate(batches):
        if n:
            time.sleep(PAUSE_BETWEEN_BATCHES)
        q = dict(params)
        q["latitude"] = ",".join(f"{p[0]:.4f}" for p in b)
        q["longitude"] = ",".join(f"{p[1]:.4f}" for p in b)
        q["elevation"] = ",".join(str(p[2]) for p in b)
        q["timezone"] = "Europe/Paris"
        data = get_json(base_url + "?" + urllib.parse.urlencode(q))
        items = data if isinstance(data, list) else [data]
        out.extend(i.get("daily", {}) for i in items)
        print(f"  lot {n + 1}/{len(batches)} : {len(b)} points", flush=True)
    return out


def rows(daily):
    """daily Open-Meteo -> [(date, tmax, tmin, precip, snowfall_cm)]"""
    t = daily.get("time", [])
    g = lambda k: daily.get(k) or [None] * len(t)
    return list(zip(t, g("temperature_2m_max"), g("temperature_2m_min"), g("precipitation_sum"), g("snowfall_sum")))


# ---------------------------------------------------------------------------
# Programme principal
# ---------------------------------------------------------------------------
def main(stations_path, out_path, previous_url=None):
    stations = json.load(open(stations_path, encoding="utf-8"))
    today = datetime.now(ZoneInfo("Europe/Paris")).date()
    yesterday = today - timedelta(days=1)
    start = season_start(today)

    prev = {}
    if previous_url:
        try:
            prev = get_json(previous_url, tries=1)
            print("État précédent chargé :", prev.get("generated"))
        except Exception as e:
            print("Pas d'état précédent :", e)
    if prev.get("seasonStart") != start.isoformat() or prev.get("modelVersion") != MODEL_VERSION:
        prev = {}  # nouvelle saison ou nouveaux réglages : recalcul complet
    prev_state = prev.get("state", {})
    # Un point dont l'altitude a changé (liste des stations modifiée) repart de zéro
    for s_ in stations:
        for lv in LEVELS:
            k = f"{s_['id']}|{lv}"
            a = s_["baseAlt"] if lv == "base" else s_["topAlt"] if lv == "top" else (s_["baseAlt"] + s_["topAlt"]) // 2
            if k in prev_state and prev_state[k].get("alt") != a:
                del prev_state[k]

    alt = lambda s, lv: s["baseAlt"] if lv == "base" else s["topAlt"] if lv == "top" else (s["baseAlt"] + s["topAlt"]) // 2
    keys = [(s, lv) for s in stations for lv in LEVELS]

    # Jours observés à rejouer : depuis le dernier état connu (ou le début de saison) jusqu'à hier.
    def last_date(k):
        st = prev_state.get(k)
        return date.fromisoformat(st["date"]) if st else start - timedelta(days=1)

    oldest = min(last_date(f"{s['id']}|{lv}") for s, lv in keys)
    past_days = max(1, (today - oldest).days)
    print(f"Saison depuis {start}, jours passés à demander : {past_days}")

    daily_vars = "temperature_2m_max,temperature_2m_min,precipitation_sum,snowfall_sum"
    pts = [(s["lat"], s["lon"], alt(s, lv)) for s, lv in keys]
    if past_days <= 92:
        dailies = fetch_daily(pts, API, {"daily": daily_vars, "past_days": past_days, "forecast_days": FORECAST_DAYS},
                              past_days + FORECAST_DAYS)
    else:
        # Début de saison trop ancien pour l'API de prévision : archive + 10 derniers jours.
        arch_end = today - timedelta(days=10)
        a = fetch_daily(pts, ARCHIVE, {"daily": daily_vars, "start_date": (oldest + timedelta(days=1)).isoformat(),
                                       "end_date": arch_end.isoformat()}, (arch_end - oldest).days)
        time.sleep(PAUSE_BETWEEN_BATCHES)
        f = fetch_daily(pts, API, {"daily": daily_vars, "past_days": 9, "forecast_days": FORECAST_DAYS}, 9 + FORECAST_DAYS)
        dailies = []
        for da, df in zip(a, f):
            merged = {k: (da.get(k) or []) + (df.get(k) or []) for k in ("time",) + tuple(daily_vars.split(","))}
            dailies.append(merged)

    result, new_state = {}, {}
    for (s, lv), daily in zip(keys, dailies):
        k = f"{s['id']}|{lv}"
        st = prev_state.get(k)
        state = {"swe": st["swe"], "depth": st["depth"]} if st else {"swe": 0.0, "depth": 0.0}
        last = last_date(k)
        days_out = []
        for d, tmax, tmin, p, sf in rows(daily):
            dd = date.fromisoformat(d)
            if dd <= last:
                continue
            state = step(state, tmax, tmin, p)
            if dd == yesterday:
                new_state[k] = {"date": d, "alt": alt(s, lv), **state}
            if dd >= today:
                days_out.append({"d": d, "depth": depth_cm(state),
                                 "snowfall": None if sf is None else round(sf, 1),
                                 "tmax": tmax, "tmin": tmin})
        if k not in new_state and st:
            new_state[k] = st  # pas de nouvelle donnée observée : on garde l'ancien état
        r = result.setdefault(s["id"], {})
        r[lv] = {"alt": alt(s, lv), "now": days_out[0]["depth"] if days_out else None, "days": days_out}

    # Même date l'an dernier (bas et sommet), recalculée une fois par jour seulement.
    last_year = today.replace(year=today.year - 1) if not (today.month == 2 and today.day == 29) else today - timedelta(days=366)
    ly = prev.get("lastYear", {})
    if prev.get("lastYearDate") != last_year.isoformat() or not ly or prev.get("modelVersion") != MODEL_VERSION:
        print("Calcul de la même date l'an dernier…")
        ly_start = season_start(last_year)
        lpts = [(s["lat"], s["lon"], alt(s, lv)) for s in stations for lv in ("base", "top")]
        time.sleep(PAUSE_BETWEEN_BATCHES)
        ldaily = fetch_daily(lpts, ARCHIVE, {"daily": "temperature_2m_max,temperature_2m_min,precipitation_sum",
                                             "start_date": ly_start.isoformat(), "end_date": last_year.isoformat()},
                             (last_year - ly_start).days + 1)
        ly = {}
        for (s, lv), daily in zip([(s, lv) for s in stations for lv in ("base", "top")], ldaily):
            state = {"swe": 0.0, "depth": 0.0}
            for d, tmax, tmin, p, _ in rows(daily):
                state = step(state, tmax, tmin, p)
            ly.setdefault(s["id"], {})[lv] = depth_cm(state)

    out = {
        "generated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "today": today.isoformat(),
        "seasonStart": start.isoformat(),
        "modelVersion": MODEL_VERSION,
        "lastYearDate": last_year.isoformat(),
        "source": "Open-Meteo (CC BY 4.0) ; manteau neigeux simulé par altitude depuis le début de saison",
        "stations": result,
        "lastYear": ly,
        "state": new_state,
    }
    json.dump(out, open(out_path, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    # Contrôle : Tignes, comparé aux relevés officiels connus
    t = result.get("tignes")
    if t:
        print("Tignes :", {lv: (t[lv]["alt"], t[lv]["now"]) for lv in LEVELS})
    bad = [sid for sid, r in result.items() if all(r[lv]["now"] is not None for lv in LEVELS)
           and not (r["base"]["now"] <= r["mid"]["now"] + 5 and r["mid"]["now"] <= r["top"]["now"] + 5)]
    print(f"{len(result)} stations ; incohérences bas > sommet : {len(bad)} {bad[:10]}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
