"""Metric sanity: identity scores perfectly, known corruptions degrade
predictably, incomparable inputs hard-fail."""

import unittest

import numpy as np

from pc_cli.parity import metrics


def canvas(w=200, h=120, color=(255, 255, 255)):
    img = np.zeros((h, w, 3), dtype=np.uint8)
    img[:] = color
    return img


def with_rect(img, x0, y0, x1, y1, color):
    out = img.copy()
    out[y0:y1, x0:x1] = color
    return out


class SsimTest(unittest.TestCase):
    def test_identical_images_score_one(self):
        img = with_rect(canvas(), 40, 30, 120, 80, (200, 30, 30))
        self.assertAlmostEqual(metrics.ssim_gray(img, img), 1.0, places=9)

    def test_shifted_content_degrades(self):
        a = with_rect(canvas(), 40, 30, 120, 80, (200, 30, 30))
        b = with_rect(canvas(), 80, 50, 160, 100, (200, 30, 30))
        self.assertLess(metrics.ssim_gray(a, b), 0.9)

    def test_missing_shape_degrades(self):
        a = with_rect(canvas(), 40, 30, 120, 80, (200, 30, 30))
        self.assertLess(metrics.ssim_gray(a, canvas()), 0.95)


class HistogramTest(unittest.TestCase):
    def test_identical_histograms_correlate_one(self):
        img = with_rect(canvas(), 40, 30, 120, 80, (30, 90, 200))
        self.assertAlmostEqual(metrics.histogram_correlation(img, img), 1.0, places=9)

    def test_recolor_degrades_histogram_but_not_structure(self):
        a = with_rect(canvas(), 40, 30, 120, 80, (200, 30, 30))
        b = with_rect(canvas(), 40, 30, 120, 80, (30, 200, 30))
        # structure identical in grayscale terms is NOT guaranteed, but the
        # histogram must clearly notice the recolor
        self.assertLess(metrics.histogram_correlation(a, b), 0.9)


class IouTest(unittest.TestCase):
    def test_same_footprint_full_overlap(self):
        a = with_rect(canvas(), 40, 30, 120, 80, (200, 30, 30))
        b = with_rect(canvas(), 40, 30, 120, 80, (30, 90, 200))
        self.assertAlmostEqual(metrics.foreground_iou(a, b), 1.0, places=6)

    def test_disjoint_footprints_zero(self):
        a = with_rect(canvas(), 10, 10, 50, 50, (200, 30, 30))
        b = with_rect(canvas(), 120, 60, 190, 110, (200, 30, 30))
        self.assertAlmostEqual(metrics.foreground_iou(a, b), 0.0, places=6)

    def test_two_blank_slides_agree(self):
        self.assertEqual(metrics.foreground_iou(canvas(), canvas()), 1.0)

    def test_half_overlap(self):
        a = with_rect(canvas(), 40, 30, 120, 80, (0, 0, 0))
        b = with_rect(canvas(), 80, 30, 160, 80, (0, 0, 0))
        iou = metrics.foreground_iou(a, b)
        self.assertGreater(iou, 0.25)
        self.assertLess(iou, 0.45)  # 40/120 = 0.333 exact


class AlignTest(unittest.TestCase):
    def test_rounding_slack_resizes(self):
        a = canvas(200, 120)
        b = canvas(201, 121)
        aa, bb = metrics.align_pair(a, b)
        self.assertEqual(aa.shape, bb.shape)

    def test_gross_mismatch_is_an_error(self):
        with self.assertRaises(metrics.MetricError):
            metrics.align_pair(canvas(200, 120), canvas(400, 240))


if __name__ == "__main__":
    unittest.main()
