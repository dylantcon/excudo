"""Ratchet + xfail-strict honesty guarantees:
- a slide below its recorded baseline fails even above the floor
- --update-baselines never lowers without force
- an expected-failure category that passes is an ERROR, not a quiet pass
"""

import json
import tempfile
import unittest
from pathlib import Path

from pc_cli.parity import baselines as bl
from pc_cli.parity.metrics import SlideMetrics


def slide(cat, n, ssim, hist=0.99, iou=0.9):
    return bl.SlideResult(cat, n, SlideMetrics(ssim, hist, iou),
                          Path("ours.png"), Path("truth.png"))


THRESHOLDS = {"default": {"ssim": 0.95}}


class EvaluateTest(unittest.TestCase):
    def test_all_above_floor_passes(self):
        r = bl.evaluate_category("cat", [slide("cat", 1, 0.98)], THRESHOLDS, {}, {})
        self.assertEqual(r.status, bl.Status.PASS)

    def test_below_floor_fails(self):
        r = bl.evaluate_category("cat", [slide("cat", 1, 0.80)], THRESHOLDS, {}, {})
        self.assertEqual(r.status, bl.Status.FAIL)

    def test_ratchet_fails_even_above_floor(self):
        base = {"cat/slide-1": {"ssim": 0.99, "histogram": 0.99, "iou": 0.9}}
        r = bl.evaluate_category("cat", [slide("cat", 1, 0.96)], THRESHOLDS, base, {})
        self.assertEqual(r.status, bl.Status.FAIL)
        self.assertIn("regressed", r.reason)

    def test_ssim_ratchet_is_serialization_strict(self):
        base = {"cat/slide-1": {"ssim": 0.99, "histogram": 0.99, "iou": 0.9}}
        r = bl.evaluate_category("cat", [slide("cat", 1, 0.99 - 5e-6)],
                                 THRESHOLDS, base, {})
        self.assertEqual(r.status, bl.Status.FAIL)

    def test_secondary_metrics_absorb_pixel_noise(self):
        # iou/histogram are threshold metrics: 1-2 AA edge pixels flip
        # their masks/bins under legitimate renderer changes (measured
        # <= 4.2e-6 iou during A5). Drops inside the 1e-5 noise floor do
        # not trip the ratchet; drops beyond it still do.
        base = {"cat/slide-1": {"ssim": 0.95, "histogram": 0.99, "iou": 0.9}}
        r = bl.evaluate_category(
            "cat", [slide("cat", 1, 0.96, hist=0.99 - 5e-6, iou=0.9 - 5e-6)],
            THRESHOLDS, base, {})
        self.assertEqual(r.status, bl.Status.PASS)

        r = bl.evaluate_category(
            "cat", [slide("cat", 1, 0.96, hist=0.99, iou=0.9 - 5e-5)],
            THRESHOLDS, base, {})
        self.assertEqual(r.status, bl.Status.FAIL)

    def test_xfail_category_reports_xfail(self):
        xf = {"cat": "pending-A2"}
        r = bl.evaluate_category("cat", [slide("cat", 1, 0.50)], THRESHOLDS, {}, xf)
        self.assertEqual(r.status, bl.Status.XFAIL)

    def test_stale_xfail_is_an_error(self):
        xf = {"cat": "pending-A2"}
        r = bl.evaluate_category("cat", [slide("cat", 1, 0.99)], THRESHOLDS, {}, xf)
        self.assertEqual(r.status, bl.Status.STALE_XFAIL)
        self.assertIn("graduate", r.reason)

    def test_xfail_still_enforces_ratchet(self):
        xf = {"cat": "pending-A2"}
        base = {"cat/slide-1": {"ssim": 0.80, "histogram": 0.99, "iou": 0.9}}
        r = bl.evaluate_category("cat", [slide("cat", 1, 0.60)], THRESHOLDS, base, xf)
        self.assertEqual(r.status, bl.Status.FAIL)

    def test_missing_floor_is_an_error(self):
        with self.assertRaises(bl.BaselineError):
            bl.evaluate_category("cat", [slide("cat", 1, 0.99)], {}, {}, {})


class UpdateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.corpus = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def _write_baselines(self, data):
        (self.corpus / bl.BASELINES_FILE).write_text(json.dumps(data))

    def _read_baselines(self):
        return json.loads((self.corpus / bl.BASELINES_FILE).read_text())

    def _category(self, ssim):
        s = slide("cat", 1, ssim)
        return [bl.CategoryResult("cat", [s], 0.95, bl.Status.PASS)]

    def test_raises_baseline(self):
        self._write_baselines({"cat/slide-1": {"ssim": 0.90, "histogram": 0.99,
                                               "iou": 0.9}})
        raised, lowered = bl.update_baselines(self.corpus, self._category(0.97))
        self.assertEqual((raised, lowered), (1, 0))
        self.assertAlmostEqual(self._read_baselines()["cat/slide-1"]["ssim"], 0.97)

    def test_never_lowers_without_force(self):
        self._write_baselines({"cat/slide-1": {"ssim": 0.99, "histogram": 0.99,
                                               "iou": 0.9}})
        raised, lowered = bl.update_baselines(self.corpus, self._category(0.90))
        self.assertEqual((raised, lowered), (0, 0))
        self.assertAlmostEqual(self._read_baselines()["cat/slide-1"]["ssim"], 0.99)

    def test_force_lowers_explicitly(self):
        self._write_baselines({"cat/slide-1": {"ssim": 0.99, "histogram": 0.99,
                                               "iou": 0.9}})
        raised, lowered = bl.update_baselines(self.corpus, self._category(0.90),
                                              force=True)
        self.assertEqual(lowered, 1)
        self.assertAlmostEqual(self._read_baselines()["cat/slide-1"]["ssim"], 0.90)

    def test_first_run_records(self):
        bl.update_baselines(self.corpus, self._category(0.88))
        self.assertAlmostEqual(self._read_baselines()["cat/slide-1"]["ssim"], 0.88)

    def test_recorded_value_never_exceeds_achieved(self):
        # round-half-up used to record 0.98796881 as 0.987969 -- a
        # baseline 1.9e-7 ABOVE anything the renderer ever produced.
        s = slide("cat", 1, 0.98796881)
        bl.update_baselines(self.corpus,
                            [bl.CategoryResult("cat", [s], 0.95, bl.Status.PASS)])
        recorded = self._read_baselines()["cat/slide-1"]["ssim"]
        self.assertLessEqual(recorded, 0.98796881)
        self.assertAlmostEqual(recorded, 0.987968)


if __name__ == "__main__":
    unittest.main()
