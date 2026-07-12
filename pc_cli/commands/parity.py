"""pc.py parity — renderer-vs-PowerPoint visual parity harness.

Subcommands:
  generate   (re)build corpus decks with python-pptx and export ground-truth
             PDFs via PowerPoint COM. Needs PowerPoint; run once per corpus
             change and commit the PPTX+PDF pairs.
  run        render every corpus deck with Excudo, rasterize the committed
             PDFs, score SSIM/histogram/IoU per slide, enforce floors +
             ratchet + strict xfails, and write the HTML dashboard.
"""

import os
from pathlib import Path

from pc_cli.parity import corpus as corpus_mod
from pc_cli.parity import baselines as bl
from pc_cli.parity.metrics import compare, diff_heatmap, load_rgb, align_pair
from pc_cli.parity.rasterize import rasterize_pdf
from pc_cli.parity.excudo_render import render_deck
from pc_cli.parity.report import write_report

from PIL import Image


def configure_parity_parser(subparsers):
    parity_parser = subparsers.add_parser(
        "parity", help="Renderer-vs-PowerPoint visual parity harness")
    parity_sub = parity_parser.add_subparsers(dest="parity_command",
                                              help="Parity operations")

    gen = parity_sub.add_parser(
        "generate", help="Generate corpus decks + ground-truth PDFs (needs PowerPoint)")
    gen.add_argument("--category", help="Only categories matching this glob")
    gen.add_argument("--skip-decks", action="store_true",
                     help="Keep existing deck.pptx files; only re-export PDFs")

    run = parity_sub.add_parser(
        "run", help="Render, compare, and gate against floors + baselines")
    run.add_argument("--category", help="Only categories matching this glob")
    run.add_argument("--update-baselines", action="store_true",
                     help="Raise per-slide baselines to current metrics (never lowers)")
    run.add_argument("--force-regress", action="store_true",
                     help="DANGER: allow --update-baselines to LOWER recorded baselines")
    run.add_argument("--open", dest="open_report", action="store_true",
                     help="Open the HTML report when done")
    return parity_parser


def handle_parity_command(args, env) -> int:
    if not getattr(args, "parity_command", None):
        print("Use: pc.py parity {generate|run} (see --help)")
        return 1
    if args.parity_command == "generate":
        return _generate(args, env)
    if args.parity_command == "run":
        return _run(args, env)
    return 1


def _generate(args, env) -> int:
    from pc_cli.parity.ground_truth import export_pdfs

    categories = corpus_mod.discover(env.project_root, args.category)
    if not args.skip_decks:
        print(f"Generating {len(categories)} corpus deck(s)...")
        corpus_mod.generate_decks(categories)
    corpus_mod.validate_fixtures(categories, require_pdf=False)
    print(f"Exporting ground truth for {len(categories)} deck(s) via PowerPoint...")
    export_pdfs(categories)
    print("Done. Commit the parity-corpus/ PPTX+PDF pairs, then run: pc.py parity run")
    return 0


def _run(args, env) -> int:
    corpus_dir = corpus_mod.corpus_root(env.project_root)
    categories = corpus_mod.discover(env.project_root, args.category)
    corpus_mod.validate_fixtures(categories, require_pdf=True)

    # Renders must reflect the current source, not a stale build.
    from pc_cli.builders.java_builder import PCBuilder
    if not PCBuilder(env).build(verbose=False, clean=False):
        print("Build failed - aborting parity run")
        return 1

    thresholds = bl.load_json(corpus_dir, bl.THRESHOLDS_FILE, {})
    baselines = bl.load_json(corpus_dir, bl.BASELINES_FILE, {})
    xfails = bl.load_json(corpus_dir, bl.XFAIL_FILE, {})

    out_root = env.project_root / "test-output" / "parity"
    results: list[bl.CategoryResult] = []

    for cat in categories:
        info = corpus_mod.deck_info(cat.pptx)
        print(f"  [{cat.name}] {info.slide_count} slide(s) @ "
              f"{info.width_px}x{info.height_px}")
        ours = render_deck(env, cat.pptx, out_root / "renders" / cat.name,
                           info.slide_count, info.width_px, info.height_px)
        truth = rasterize_pdf(cat.pdf, out_root / "truth" / cat.name,
                              info.width_px, info.height_px)
        if len(truth) != info.slide_count:
            print(f"    ERROR: PDF has {len(truth)} page(s), deck has "
                  f"{info.slide_count} slide(s) — stale ground truth? "
                  f"Re-run: pc.py parity generate --category {cat.name}")
            return 1

        slides = []
        heat_dir = out_root / "heatmaps" / cat.name
        heat_dir.mkdir(parents=True, exist_ok=True)
        for n, (o, t) in enumerate(zip(ours, truth), start=1):
            m = compare(o, t)
            a, b = align_pair(load_rgb(o), load_rgb(t))
            heat = heat_dir / f"slide-{n}.png"
            diff_heatmap(a, b).save(heat)
            slides.append(bl.SlideResult(cat.name, n, m, o, t, heat))

        result = bl.evaluate_category(cat.name, slides, thresholds, baselines, xfails)
        results.append(result)
        min_ssim = min(s.metrics.ssim for s in slides)
        print(f"    {result.status.value.upper():<11} floor={result.floor:.2f} "
              f"min-SSIM={min_ssim:.4f}"
              + (f"  ({result.reason})" if result.reason else ""))

    if args.update_baselines:
        if args.force_regress:
            print("\n" + "!" * 70)
            print("!! --force-regress: recorded baselines may be LOWERED.")
            print("!! Never use this to make a failing run pass in CI.")
            print("!" * 70)
        raised, lowered = bl.update_baselines(corpus_dir, results,
                                              force=args.force_regress)
        print(f"Baselines updated: {raised} raised, {lowered} lowered.")
        # Re-evaluate against the updated baselines so the verdict and exit
        # code reflect the state a fresh run would see.
        fresh = bl.load_json(corpus_dir, bl.BASELINES_FILE, {})
        results = [
            bl.evaluate_category(r.name, r.slides, thresholds, fresh, xfails)
            for r in results
        ]
    elif args.force_regress:
        print("--force-regress has no effect without --update-baselines")

    report = write_report(out_root, results)
    print(f"\nReport: {report}")

    failed = [r for r in results if r.status == bl.Status.FAIL]
    stale = [r for r in results if r.status == bl.Status.STALE_XFAIL]
    passed = sum(1 for r in results if r.status == bl.Status.PASS)
    xfailed = sum(1 for r in results if r.status == bl.Status.XFAIL)
    print(f"Categories: {passed} pass, {len(failed)} fail, {xfailed} xfail, "
          f"{len(stale)} stale-xfail")
    for r in stale:
        print(f"  STALE XFAIL: {r.name} — {r.reason}")
    for r in failed:
        print(f"  FAIL: {r.name} — {r.reason}")

    if args.open_report:
        os.startfile(report)  # noqa: S606 (Windows)

    return 0 if not failed and not stale else 1
