"""Tests du modèle de manteau neigeux (sans réseau). Lancer : python3 -m unittest tools/test_snow_feed.py"""
import os, sys, unittest
from datetime import date
sys.path.insert(0, os.path.dirname(__file__))
from snow_feed import step, depth_cm, season_start


def run(days):
    st = {"swe": 0.0, "depth": 0.0}
    for tmax, tmin, p in days:
        st = step(st, tmax, tmin, p)
    return st


class SnowModelTest(unittest.TestCase):
    def test_cold_precipitation_accumulates(self):
        st = run([(-5, -10, 10)])           # 10 mm d'eau à -7,5 °C
        self.assertEqual(depth_cm(st), 9)   # ≈ 10 cm de neige fraîche, déjà un peu tassée

    def test_rain_does_not_accumulate(self):
        self.assertEqual(depth_cm(run([(8, 4, 20)])), 0)

    def test_higher_is_snowier(self):
        """Même précipitation, température plus basse en altitude : plus de neige au sommet."""
        weather = [(3, -2, 15), (6, 1, 0), (2, -4, 25), (8, 2, 0), (1, -6, 10)] * 6
        base = run([(tx + 6, tn + 6, p) for tx, tn, p in weather])   # ~1000 m plus bas
        top = run(weather)
        self.assertGreater(depth_cm(top), depth_cm(base))

    def test_settling_reduces_depth_not_water(self):
        st = run([(-5, -10, 30)])
        later = run([(-5, -10, 30)] + [(-5, -10, 0)] * 20)
        self.assertLess(later["depth"], st["depth"])
        self.assertAlmostEqual(later["swe"], st["swe"], places=1)

    def test_warm_spell_melts(self):
        st = run([(-5, -10, 40)] + [(10, 4, 0)] * 15)
        self.assertEqual(depth_cm(st), 0)

    def test_season_start(self):
        self.assertEqual(season_start(date(2026, 11, 25)), date(2026, 10, 1))
        self.assertEqual(season_start(date(2027, 2, 10)), date(2026, 10, 1))
        self.assertEqual(season_start(date(2026, 8, 1)), date(2026, 9, 1))


if __name__ == "__main__":
    unittest.main()
