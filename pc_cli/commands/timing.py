"""
Timing command module

Handles standalone timing dump operations and animation analysis integration.
Provides the bridge between the CLI interface and the animation analysis framework.
"""

import subprocess
from pathlib import Path
from typing import Optional
from ..analyzers.animations.fly_analyzer import FlyAnimationAnalyzer


def handle_timing_command(args, env):
    """Handle timing commands - standalone timing dump without REPL"""
    
    if args.timing_command == "dump":
        return handle_timing_dump(args, env)
    elif args.timing_command == "compare":
        return handle_timing_compare(args, env)
    elif args.timing_command == "analyze":
        return handle_timing_analyze(args, env)
    else:
        print(f"Unknown timing command: {args.timing_command}")
        return False


def handle_timing_dump(args, env):
    """Handle timing dump command using HeadlessInterface"""
    print(f"Dumping timing from: {args.pptx_file}")
    
    # Use HeadlessInterface for stateless timing dump
    java_cmd = [env.get_java_executable()]
    cmd = java_cmd + [
        "-cp", env.get_classpath(),
        "-Djava.awt.headless=true",
        "com.excudo.cli.HeadlessInterface",
        "--dump-timing", args.pptx_file, args.slide
    ]
    
    result = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        cwd=env.project_root
    )
    
    if result.returncode == 0:
        # Extract the output directory from the console output
        output_lines = result.stdout.split('\n')
        output_dir = None
        
        for line in output_lines:
            if "Output directory:" in line:
                output_dir = line.split("Output directory:")[-1].strip().strip('"')
                print(f"Timing dumps saved to: {output_dir}")
                break
                
        # If custom output directory specified, move the files
        if args.output_dir and output_dir:
            import shutil
            dest = Path(args.output_dir)
            dest.mkdir(parents=True, exist_ok=True)
            
            source = Path(output_dir)
            if source.exists():
                for xml_file in source.glob("*.xml"):
                    shutil.copy2(xml_file, dest)
                print(f"Copied timing dumps to: {args.output_dir}")
        
        return True
        
    print(f"Error: Failed to dump timing")
    if args.verbose:
        print(f"STDOUT:\n{result.stdout}")
        print(f"STDERR:\n{result.stderr}")
    return False


def handle_timing_compare(args, env):
    """Handle timing comparison between files"""
    print(f"Comparing timing between {args.file1} and {args.file2}")
    
    # First, dump timing for both files
    print("Dumping timing for first file...")
    # Implementation would dump both files and compare their timing structures
    
    print("Timing comparison not yet fully implemented")
    return False


def handle_timing_analyze(args, env):
    """Handle timing analysis with animation-specific analyzers"""
    if not hasattr(args, 'animation_type'):
        print("Error: Animation type required for timing analysis")
        print("Usage: pc.py timing analyze <pptx_file> --animation-type fly")
        return False
    
    animation_type = args.animation_type.lower()
    
    if animation_type == "fly":
        return analyze_fly_animations(args, env)
    else:
        print(f"Animation type '{animation_type}' not yet supported")
        print("Available types: fly")
        return False


def analyze_fly_animations(args, env):
    """Analyze FLY animations using timing dumps"""
    
    # First, check if we have timing dumps available
    timing_dumps_dir = find_latest_timing_dumps(env.project_root)
    
    if not timing_dumps_dir:
        print("No timing dumps found. Please run 'pc.py timing dump <file>' first.")
        return False
    
    print(f"Using timing dumps from: {timing_dumps_dir}")
    
    # Initialize FLY analyzer
    analyzer = FlyAnimationAnalyzer(timing_dumps_dir)
    
    # Perform analysis
    print("Analyzing FLY animations...")
    
    if hasattr(args, 'direction') and args.direction:
        # Filter by specific direction
        pattern_filter = args.direction
        print(f"Filtering for direction: {pattern_filter}")
        analysis = analyzer.analyze_native_dumps(pattern_filter)
    else:
        # Analyze all FLY variations
        analysis = analyzer.analyze_all_variations()
    
    # Generate report
    format_type = getattr(args, 'format', 'text')
    report = analyzer.generate_fly_report(format_type)
    
    # Output report
    if hasattr(args, 'output') and args.output:
        output_file = Path(args.output)
        output_file.write_text(report)
        print(f"Report saved to: {output_file}")
    else:
        print("\n" + report)
    
    # Show interactive options if requested
    if getattr(args, 'interactive', False):
        show_fly_interactive_menu(analyzer, analysis)
    
    return True


def find_latest_timing_dumps(project_root: Path) -> Optional[Path]:
    """Find the most recent timing dumps directory"""
    timing_dumps_root = project_root / ".excudo" / "logs" / "timing-dumps"
    
    if not timing_dumps_root.exists():
        return None
    
    # Find the latest timestamped directory
    dump_dirs = [d for d in timing_dumps_root.iterdir() if d.is_dir()]
    if not dump_dirs:
        return None
    
    # Sort by directory name (timestamp format YYYYMMDD_HHMMSS)
    latest_dir = sorted(dump_dirs, key=lambda d: d.name)[-1]
    return latest_dir


def show_fly_interactive_menu(analyzer: FlyAnimationAnalyzer, analysis: dict):
    """Show interactive menu for FLY animation analysis"""
    print("\n" + "="*60)
    print("FLY ANIMATION INTERACTIVE ANALYSIS")
    print("="*60)
    
    while True:
        print("\nOptions:")
        print("1. Show entrance animations summary")
        print("2. Show exit animations summary")
        print("3. Analyze coordinate expressions")
        print("4. Show validation errors")
        print("5. Compare with our FlyAnimationFactory")
        print("6. Export detailed report")
        print("0. Exit")
        
        try:
            choice = input("\nSelect option (0-6): ").strip()
            
            if choice == "0":
                break
            elif choice == "1":
                show_entrance_summary(analysis)
            elif choice == "2":
                show_exit_summary(analysis) 
            elif choice == "3":
                show_coordinate_analysis(analysis)
            elif choice == "4":
                show_validation_errors(analysis)
            elif choice == "5":
                show_factory_comparison(analysis)
            elif choice == "6":
                export_detailed_report(analyzer, analysis)
            else:
                print("Invalid option. Please try again.")
                
        except (KeyboardInterrupt, EOFError):
            break
    
    print("\nExiting interactive analysis.")


def show_entrance_summary(analysis: dict):
    """Show summary of entrance animations"""
    entrance_patterns = analysis.get("entrance_patterns", {})
    print(f"\nENTRANCE ANIMATIONS ({len(entrance_patterns)} variations):")
    print("-" * 40)
    
    for variation, patterns in entrance_patterns.items():
        print(f"{variation}: {len(patterns)} files")
        for pattern_info in patterns:
            filename = pattern_info["file"]
            pattern = pattern_info["pattern"]
            coord_info = []
            
            for coord_type, coords in pattern.get("coordinates", {}).items():
                if len(coords) >= 2:
                    start_val = coords[0][1]
                    end_val = coords[1][1]
                    coord_info.append(f"{coord_type}: {start_val} → {end_val}")
            
            print(f"  {filename}: {', '.join(coord_info)}")


def show_exit_summary(analysis: dict):
    """Show summary of exit animations"""
    exit_patterns = analysis.get("exit_patterns", {})
    print(f"\nEXIT ANIMATIONS ({len(exit_patterns)} variations):")
    print("-" * 40)
    
    for variation, patterns in exit_patterns.items():
        print(f"{variation}: {len(patterns)} files")
        for pattern_info in patterns:
            filename = pattern_info["file"]
            pattern = pattern_info["pattern"]
            coord_info = []
            
            for coord_type, coords in pattern.get("coordinates", {}).items():
                if len(coords) >= 2:
                    start_val = coords[0][1]
                    end_val = coords[1][1]
                    coord_info.append(f"{coord_type}: {start_val} → {end_val}")
            
            print(f"  {filename}: {', '.join(coord_info)}")


def show_coordinate_analysis(analysis: dict):
    """Show coordinate expression analysis"""
    coord_analysis = analysis.get("coordinate_analysis", {})
    if not coord_analysis:
        print("No coordinate analysis available.")
        return
    
    print("\nCOORDINATE EXPRESSION ANALYSIS:")
    print("-" * 40)
    
    # Expression types
    expr_types = coord_analysis.get("expression_types", {})
    if expr_types:
        print("\nExpression types found:")
        for expr_type, count in sorted(expr_types.items()):
            print(f"  {expr_type}: {count} occurrences")
    
    # Common expressions
    common_exprs = coord_analysis.get("common_expressions", {})
    if common_exprs:
        print("\nMost common expressions:")
        sorted_exprs = sorted(common_exprs.items(), key=lambda x: x[1], reverse=True)
        for expr, count in sorted_exprs[:10]:  # Top 10
            print(f"  '{expr}': {count} times")


def show_validation_errors(analysis: dict):
    """Show validation errors found in patterns"""
    coord_analysis = analysis.get("coordinate_analysis", {})
    invalid_exprs = coord_analysis.get("invalid_expressions", [])
    
    if not invalid_exprs:
        print("\n✅ No validation errors found!")
        return
    
    print(f"\n❌ VALIDATION ERRORS ({len(invalid_exprs)}):")
    print("-" * 40)
    for error in invalid_exprs:
        print(f"  {error}")


def show_factory_comparison(analysis: dict):
    """Compare with our FlyAnimationFactory implementation"""
    print("\nFLYANIMATIONFACTORY COMPARISON:")
    print("-" * 40)
    print("Checking our implementation against PowerPoint patterns...")
    
    # This would compare our FlyAnimationFactory.java expressions 
    # with the patterns found in the analysis
    print("⚠️  Comparison with FlyAnimationFactory not yet implemented")
    print("This would analyze:")
    print("  - Expression format differences")
    print("  - Additive mode usage")
    print("  - Coordinate calculation accuracy")
    print("  - Missing animation variations")


def export_detailed_report(analyzer: FlyAnimationAnalyzer, analysis: dict):
    """Export detailed analysis report"""
    print("\nExporting detailed report...")
    
    # Generate comprehensive report
    text_report = analyzer.generate_fly_report("text")
    markdown_report = analyzer.generate_fly_report("markdown")
    json_report = analyzer.generate_fly_report("json")
    
    # Save reports
    reports_dir = Path("fly_analysis_reports")
    reports_dir.mkdir(exist_ok=True)
    
    (reports_dir / "fly_analysis.txt").write_text(text_report)
    (reports_dir / "fly_analysis.md").write_text(markdown_report)
    (reports_dir / "fly_analysis.json").write_text(json_report)
    
    print(f"Reports exported to {reports_dir}/:")
    print("  - fly_analysis.txt (plain text)")
    print("  - fly_analysis.md (markdown)")
    print("  - fly_analysis.json (structured data)")