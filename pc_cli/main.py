#!/usr/bin/env python3
"""
Excudo CLI Main Entry Point
Unified command-line interface for PowerPoint presentation manipulation

This CLI provides:
- Cross-platform Java application management
- Virtual environment handling for Python dependencies
- Integrated build system replacing bash/batch scripts
- Full access to all non-visual functionality
- Developer API for integration with other Python tools
- Structured logging management and filtering

Usage:
    pc.py build [--verbose] [--clean]
    pc.py run [ui|console|headless] [options]
    pc.py test [--coverage] [--filter] [--parallel]
    pc.py coverage [gaps|animation|priorities] [options]
    pc.py logs [--filter=COMPONENT] [--tail] [--clear]
    pc.py smart <command> [options]
    pc.py timing <command> [options]
"""

import os
import sys
import argparse
import json
from pathlib import Path

# Import modular components
from pc_cli.environment import PCEnvironment
from pc_cli.builders.java_builder import PCBuilder
from pc_cli.runners.console_runner import PCRunner
from pc_cli.testers.junit_tester import PCTester
from pc_cli.analyzers.logs import PCLogs
from pc_cli.analyzers.codebase import PCAnalyzer
from pc_cli.analyzers.animations.animation_analyzer import AnimationAnalyzer
from pc_cli.analyzers.ooxml import OOXMLValidator
from pc_cli.analyzers.coverage import CoverageAnalyzer
from pc_cli.commands.session import handle_session_command
from pc_cli.commands.smart_content import handle_smart_command
from pc_cli.commands.timing import handle_timing_command
from pc_cli.commands.deps import handle_deps_command
from pc_cli.commands.parity import configure_parity_parser, handle_parity_command
from pc_cli.config.api_manager import APIConfigManager

# Version and metadata
__version__ = "2.0.0"
__author__ = "Excudo Team"

def configure_env_parser(subparsers):
    # Create the env command argument parser and return
    env_parser = subparsers.add_parser("env", help="Manage Python environment")
    env_parser.add_argument("--setup", action="store_true", help="Setup virtual environment")
    env_parser.add_argument("--force", action="store_true", help="Force recreate environment")
    env_parser.add_argument("--info", action="store_true", help="Show environment info")
    return env_parser

def configure_test_parser(subparsers):
    # Create the test command argument parser and return
    test_parser = subparsers.add_parser("test", help="Run tests")
    test_parser.add_argument("suite", choices=["all", "unit", "integration"], 
                            default="all", nargs="?", help="Test suite to run")
    test_parser.add_argument("--filter", "-f", dest="test_filter",
                            help="Filter tests by name pattern (regex)")
    test_parser.add_argument("--parallel", "-p", action="store_true",
                            help="Run tests in parallel")
    test_parser.add_argument("--coverage", "-c", action="store_true",
                            help="Generate coverage report")
    test_parser.add_argument("--headless", action="store_true", default=True,
                            help="Run tests in headless mode (default: true)")
    test_parser.add_argument("--gui", action="store_true",
                            help="Run tests with GUI (overrides --headless)")
    test_parser.add_argument("--custom", action="store_true",
                            help="Run built-in TestRunner integration tests")
    test_parser.add_argument("--list", "-l", action="store_true",
                            help="List available tests")
    test_parser.add_argument("--verbose", "-v", action="store_true",
                            help="Enable verbose test output")
    test_parser.add_argument("--short", "-s", action="store_true",
                            help="Short output -- suppress per-class lines, show summary only")
    test_parser.add_argument("--workers", "-w", type=int, default=0,
                            help="Max concurrent test JVMs with --parallel "
                                 "(default: test_workers config key, else one per core)")
    return test_parser

def configure_session_parser(subparsers):
    # Create the session command argument parser and return
    session_parser = subparsers.add_parser("session", help="Manage presentation sessions")
    session_subparsers = session_parser.add_subparsers(dest="session_command", help="Session operations")
    
    # Session create
    create_parser = session_subparsers.add_parser("create", help="Create new session")
    create_parser.add_argument("file", nargs="?", help="PPTX file to load (optional for new presentation)")
    
    # Session list
    session_subparsers.add_parser("list", help="List active sessions")
    
    # Session info
    info_parser = session_subparsers.add_parser("info", help="Get session information")
    info_parser.add_argument("session_id", help="Session ID to get info for")
    
    # Session close
    close_parser = session_subparsers.add_parser("close", help="Close session")
    close_parser.add_argument("session_id", help="Session ID to close")
    return session_parser, session_subparsers

def configure_smart_parser(subparsers):
    # Create the smart command argument parser and return
    llm_parser = subparsers.add_parser("smart", help="SmartContent intelligent image injection")
    llm_subparsers = llm_parser.add_subparsers(dest="smart_command", help="SmartContent operations")
    
    # Icon search
    search_parser = llm_subparsers.add_parser("search", help="Search for icons")
    search_parser.add_argument("query", help="Search query for icons")
    search_parser.add_argument("--max", type=int, default=10, help="Maximum number of results (default: 10)")
    search_parser.add_argument("--source", choices=["devicon", "freepik", "local", "all"], default="all",
                              help="Icon source to search (default: all)")
    
    # Icon inject
    inject_parser = llm_subparsers.add_parser("inject", help="Inject icon into presentation slide")
    inject_parser.add_argument("presentation", help="Path to PPTX file")
    inject_parser.add_argument("slide", type=int, help="Slide number to inject icon into")
    inject_parser.add_argument("query", help="Icon search query")
    inject_parser.add_argument("--cache-dir", default="./icon-cache", help="Icon cache directory")
    inject_parser.add_argument("--position", choices=["auto", "top-left", "top-right", "bottom-left", "bottom-right"], 
                              default="auto", help="Icon position (default: auto)")
    
    # Icon upload
    upload_parser = llm_subparsers.add_parser("upload", help="Upload custom icon")
    upload_parser.add_argument("file", help="Path to icon file (SVG, PNG, JPG)")
    upload_parser.add_argument("name", help="Name for the uploaded icon")
    upload_parser.add_argument("--tags", nargs="+", default=[], help="Tags for the icon")
    upload_parser.add_argument("--cache-dir", default="./icon-cache", help="Icon cache directory")
    
    # Icon list
    list_parser = llm_subparsers.add_parser("list", help="List available icons")
    list_parser.add_argument("--source", choices=["devicon", "freepik", "local", "all"], default="all",
                            help="Icon source to list (default: all)")
    list_parser.add_argument("--cache-dir", default="./icon-cache", help="Icon cache directory")
    return llm_parser, llm_subparsers

def configure_validate_parser(subparsers):
    # Configure the validate command argument parser and return
    validate_parser = subparsers.add_parser("validate", help="Validate OOXML files for compliance")
    validate_parser.add_argument("file_or_dir", help="PPTX file or directory to validate")
    validate_parser.add_argument("--office-version", choices=["Office2007", "Office2010", "Office2013",
                                                             "Office2016", "Office2019", "Office2021",
                                                             "Microsoft365"], default="Microsoft365",
                               help="Office version to validate against")
    validate_parser.add_argument("--format", choices=["json", "xml"], default="json",
                               help="Output format (default: json)")
    validate_parser.add_argument("--recursive", "-r", action="store_true",
                               help="Recursively validate directories")
    validate_parser.add_argument("--include-valid", "-a", action="store_true",
                               help="Include files without errors in output")
    validate_parser.add_argument("--fail-on-error", action="store_true",
                               help="Exit with error code if validation errors found")
    validate_parser.add_argument("--libreoffice", action="store_true",
                               help="Use LibreOffice headless for structural validation (Layer 2)")
    validate_parser.add_argument("--powerpoint", action="store_true",
                               help="Use Wine+PowerPoint for authoritative validation (Layer 3)")
    validate_parser.add_argument("--all-layers", action="store_true",
                               help="Run all available validation layers")
    return validate_parser

def configure_analyze_parser(subparsers):
    # Configure the analyze command argument parser and return
    analyze_parser = subparsers.add_parser("analyze", help="Analyze codebase for unused components")
    analyze_parser.add_argument("--type", choices=["unused", "deps", "dead-methods", "all"], default="unused",
                               help="Type of analysis to perform")
    analyze_parser.add_argument("--format", choices=["text", "json", "csv"], default="text",
                               help="Output format")
    analyze_parser.add_argument("--verbose", action="store_true", help="Verbose output")
    return analyze_parser

def configure_anim_parser(subparsers):
    # Configure the anim command argument parser and return
    anim_parser = subparsers.add_parser("anim", help="Animation analysis and tooling")
    anim_subparsers = anim_parser.add_subparsers(dest="anim_command", help="Animation operations")
    
    # Animation analyze
    anim_analyze_parser = anim_subparsers.add_parser("analyze", help="Analyze timing XML files")
    anim_analyze_parser.add_argument("timing_dir", help="Directory containing timing XML files")
    anim_analyze_parser.add_argument("--format", choices=["text", "json", "csv"], default="text",
                                   help="Output format")
    anim_analyze_parser.add_argument("--verbose", action="store_true", help="Verbose output")
    anim_analyze_parser.add_argument("--output", "-o", help="Output file (default: stdout)")
    anim_analyze_parser.add_argument("--pattern", default="*.xml", help="File pattern to match")
    return anim_parser

def configure_timing_parser(subparsers):
    # Configure the timing command argument parser and return
    timing_parser = subparsers.add_parser("timing", help="Extract and analyze PowerPoint timing data")
    timing_subparsers = timing_parser.add_subparsers(dest="timing_command", help="Timing operations")
    
    # Timing dump
    timing_dump_parser = timing_subparsers.add_parser("dump", help="Dump timing XML from PPTX file")
    timing_dump_parser.add_argument("pptx_file", help="PowerPoint file to analyze")
    timing_dump_parser.add_argument("--slide", help="Specific slide number or range (e.g., 1, 1-5, all)", default="all")
    timing_dump_parser.add_argument("--output-dir", help="Output directory for timing dumps")
    timing_dump_parser.add_argument("--verbose", action="store_true", help="Verbose output")
    
    # Timing compare
    timing_compare_parser = timing_subparsers.add_parser("compare", help="Compare timing between files")
    timing_compare_parser.add_argument("file1", help="First PowerPoint file")
    timing_compare_parser.add_argument("file2", help="Second PowerPoint file")
    timing_compare_parser.add_argument("--slide", help="Specific slide to compare")
    
    # Timing analyze - NEW: Animation-specific analysis
    timing_analyze_parser = timing_subparsers.add_parser("analyze", help="Analyze animations with specialized tools")
    timing_analyze_parser.add_argument("pptx_file", help="PowerPoint file to analyze")
    timing_analyze_parser.add_argument("--animation-type", choices=["fly"], required=True, help="Animation type to analyze")
    timing_analyze_parser.add_argument("--direction", help="Filter by specific direction (e.g., left, right)")
    timing_analyze_parser.add_argument("--format", choices=["text", "json", "markdown"], default="text", help="Output format")
    timing_analyze_parser.add_argument("--output", help="Output file for report")
    timing_analyze_parser.add_argument("--interactive", action="store_true", help="Show interactive analysis menu")
    return timing_parser

def configure_coverage_parser(subparsers):
    # Configure the coverage command parser and return
    coverage_parser = subparsers.add_parser("coverage", help="Analyze test coverage and identify gaps")
    coverage_subparsers = coverage_parser.add_subparsers(dest="coverage_command", help="Coverage operations")
    
    # Coverage gaps
    gaps_parser = coverage_subparsers.add_parser("gaps", help="Identify coverage gaps")
    gaps_parser.add_argument("--threshold", type=int, default=80, help="Coverage threshold percentage (default: 80)")
    gaps_parser.add_argument("--focus", nargs="+", default=["animation", "core"], 
                           help="Package patterns to focus on (default: animation core)")
    
    # Coverage animation
    anim_cov_parser = coverage_subparsers.add_parser("animation", help="Analyze animation architecture coverage")
    anim_cov_parser.add_argument("--format", choices=["text", "json", "csv"], default="text", 
                                help="Output format (default: text)")
    
    # Coverage priorities  
    priorities_parser = coverage_subparsers.add_parser("priorities", help="Suggest test creation priorities")
    priorities_parser.add_argument("--max", type=int, default=10, help="Maximum classes to suggest (default: 10)")
    return coverage_parser

def configure_deps_parser(subparsers):
    """Configure deps command parser"""
    deps_parser = subparsers.add_parser("deps", help="Fetch and verify JAR dependencies")
    deps_parser.add_argument("--verify", dest="deps_verify", action="store_true",
                            help="Verify all JARs without downloading")
    deps_parser.add_argument("--list", "-l", dest="deps_list", action="store_true",
                            help="List all dependencies and their status")
    deps_parser.add_argument("--dry-run", dest="deps_dry_run", action="store_true",
                            help="Show what would be downloaded without fetching")
    deps_parser.add_argument("--force", dest="deps_force", action="store_true",
                            help="Re-download all JARs regardless of cache")
    deps_parser.add_argument("--verbose", "-v", action="store_true",
                            help="Show status of up-to-date JARs")
    return deps_parser

def configure_build_parser(subparsers):
    """Configure build command parser"""
    build_parser = subparsers.add_parser("build", help="Build the Java application")
    build_parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    build_parser.add_argument("--clean", "-c", action="store_true", help="Clean build")
    build_parser.add_argument("--headless", action="store_true", help="Build in headless mode")
    return build_parser

def configure_run_parser(subparsers):
    """Configure run command parser"""
    run_parser = subparsers.add_parser("run", help="Run the application")
    run_parser.add_argument("mode", choices=["gui", "console", "mcp"], help="Run mode")
    run_parser.add_argument("--headless", action="store_true", help="Run in headless mode")
    run_parser.add_argument("--file", help="PPTX file to load on startup (MCP mode)")
    run_parser.add_argument("--auto-approve", action="store_true", help="Auto-approve mode")
    run_parser.add_argument("--log-level", default="INFO", help="Log level")
    run_parser.add_argument("--log-console", action="store_true", help="Log to console")
    run_parser.add_argument("--mcp", action="store_true",
                            help="gui mode: also start the in-process MCP HTTP server "
                                 "(the MCP-launcher path; plain 'run gui' is normal mode)")
    return run_parser

def configure_logs_parser(subparsers):
    """Configure logs command parser"""
    logs_parser = subparsers.add_parser("logs", help="View application logs")
    logs_parser.add_argument("--list", action="store_true", help="List log components")
    logs_parser.add_argument("--component", help="View specific component log")
    logs_parser.add_argument("--clear", action="store_true", help="Clear logs")
    logs_parser.add_argument("--filter", help="Filter logs")
    logs_parser.add_argument("--follow", "-f", action="store_true", help="Follow log output")
    return logs_parser

def configure_config_parser(subparsers):
    """Configure config command parser"""
    config_parser = subparsers.add_parser("config", help="Manage configuration")
    config_parser.add_argument("--get", help="Get config value")
    config_parser.add_argument("--set", nargs=2, metavar=("KEY", "VALUE"), help="Set config value")
    config_parser.add_argument("--list", action="store_true", help="List all config")
    return config_parser

def main():
    """Main CLI entry point"""
    # Detect project root
    project_root = Path(__file__).parent.parent.absolute()
    
    # Initialize environment and components
    env = PCEnvironment(project_root)
    builder = PCBuilder(env)
    runner = PCRunner(env)
    logs = PCLogs(env)
    tester = PCTester(env)
    
    parser = argparse.ArgumentParser(
        description="Excudo CLI",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )
    
    parser.add_argument("--version", action="version", version=f"pc.py {__version__}")
    parser.add_argument("--verbose", "-v", action="store_true", help="Enable verbose output")
    
    subparsers = parser.add_subparsers(dest="command", help="Available commands")

    # Deps command
    deps_parser = configure_deps_parser(subparsers)

    # Build command
    build_parser = configure_build_parser(subparsers)

    # Run command
    run_parser = configure_run_parser(subparsers)

    # Logs command
    logs_parser = configure_logs_parser(subparsers)
    
    # Config command  
    config_parser = configure_config_parser(subparsers)

    # Environment command
    env_parser = configure_env_parser(subparsers)

    # Test command
    test_parser = configure_test_parser(subparsers)
    
    # Session command
    session_parser, session_subparsers = configure_session_parser(subparsers)
    
    # SmartContent/LLM command
    llm_parser, llm_subparsers = configure_smart_parser(subparsers)
    
    # Validate command
    validate_parser = configure_validate_parser(subparsers)
    
    # Analyze command
    analyze_parser = configure_analyze_parser(subparsers)
    
    # Animation analysis command
    anim_parser = configure_anim_parser(subparsers)
    
    # Timing command - standalone timing dump without REPL
    timing_parser = configure_timing_parser(subparsers)
    
    # Coverage command
    coverage_parser = configure_coverage_parser(subparsers)

    # Parity command - renderer-vs-PowerPoint visual comparison
    parity_parser = configure_parity_parser(subparsers)

    args = parser.parse_args()
    
    if not args.command:
        parser.print_help()
        return 1
        
    # Handle commands
    try:
        if args.command == "deps":
            return handle_deps_command(args, env)

        elif args.command == "build":
            # Set headless mode in environment config temporarily
            original_headless = env.config.get("headless_only", False)
            if args.headless:
                env.config["headless_only"] = True
            
            success = builder.build(verbose=args.verbose, clean=args.clean)
            
            # Restore original setting
            env.config["headless_only"] = original_headless
            return 0 if success else 1
            
        elif args.command == "run":
            success = runner.run(args.mode,
                               headless=args.headless,
                               auto_approve=args.auto_approve,
                               log_level=args.log_level,
                               log_console=args.log_console,
                               mcp=getattr(args, 'mcp', False),
                               file=getattr(args, 'file', None))
            return 0 if success else 1
            
        elif args.command == "logs":
            if args.list:
                components = logs.list_components()
                if components:
                    print("Available log components:")
                    for comp in components:
                        print(f"  {comp}")
                else:
                    print("No log components found")
                return 0
                
            if args.clear:
                success = logs.clear_logs()
                return 0 if success else 1
                
            if args.follow:
                success = logs.follow_logs(args.component)
                return 0 if success else 1
                
            success = logs.view_logs(args.component, args.tail, args.lines)
            return 0 if success else 1
            
        elif args.command == "config":
            api_manager = APIConfigManager(env.project_root)
            
            if args.setup_api:
                success = api_manager.setup_interactive()
                return 0 if success else 1
                
            if args.validate_api:
                results = api_manager.validate_configuration()
                print("🔍 API Configuration Validation")
                print("=" * 40)
                
                # Anthropic API
                anthropic = results["anthropic"]
                status = "✅" if anthropic["valid"] else "❌" if anthropic["configured"] else "⚠️"
                print(f"{status} Anthropic Claude API: ", end="")
                if not anthropic["configured"]:
                    print("Not configured")
                elif anthropic["valid"]:
                    print("Valid and working")
                else:
                    print(f"Error - {anthropic['error']}")
                    
                # Freepik API
                freepik = results["freepik"]
                if freepik["configured"]:
                    status = "✅" if freepik["valid"] else "❌"
                    print(f"{status} Freepik API: ", end="")
                    if freepik["valid"]:
                        print("Valid and working")
                    else:
                        print(f"Error - {freepik['error']}")
                else:
                    print("⚠️  Freepik API: Not configured (optional)")
                    
                # Extended thinking
                thinking = results["extended_thinking"]
                status = "✅" if thinking["enabled"] else "ℹ️"
                print(f"{status} Extended Thinking: {'Enabled' if thinking['enabled'] else 'Disabled'}")
                
                # Overall status
                if anthropic["valid"]:
                    print("\n🎉 Configuration is ready for use!")
                else:
                    print(f"\n❌ Setup required. Run: pc.py config --setup-api")
                    return 1
                return 0
                
            if args.set_anthropic_key:
                success = api_manager.set_anthropic_key(args.set_anthropic_key)
                return 0 if success else 1
                
            if args.set_freepik_key:
                success = api_manager.set_freepik_key(args.set_freepik_key)
                return 0 if success else 1
                
            if args.enable_extended_thinking:
                success = api_manager._update_env_var("ANTHROPIC_EXTENDED_THINKING", "true")
                if success:
                    print("✅ Extended thinking enabled for complex reasoning")
                return 0 if success else 1
                
            if args.disable_extended_thinking:
                success = api_manager._update_env_var("ANTHROPIC_EXTENDED_THINKING", "false")
                if success:
                    print("✅ Extended thinking disabled")
                return 0 if success else 1
                
            if args.show:
                print(json.dumps(env.config, indent=2))
                return 0
                
            if args.set:
                key, value = args.set
                # Try to parse as JSON for complex values
                try:
                    env.config[key] = json.loads(value)
                except json.JSONDecodeError:
                    env.config[key] = value
                env.save_config()
                print(f"Set {key} = {value}")
                return 0
                
            if args.reset:
                env.config_file.unlink(missing_ok=True)
                print("Configuration reset to defaults")
                return 0
                
            print("Use --show, --set, --reset, --setup-api, or --validate-api")
            return 1
            
        elif args.command == "env":
            if args.info:
                print(f"Project root: {env.project_root}")
                print(f"Python executable: {env.get_python_executable()}")
                print(f"Java executable: {env.get_java_executable()}")
                print(f"Virtual environment: {env.venv_dir}")
                print(f"Virtual environment exists: {env.venv_dir.exists()}")
                return 0
                
            if args.setup:
                success = env.setup_venv(force=args.force)
                return 0 if success else 1
                
            print("Use --info or --setup")
            return 1
            
        elif args.command == "test":
            if args.list:
                test_classes = tester.discover_tests(args.test_filter)
                print("Available test suites:")
                for suite, classes in test_classes.items():
                    if classes:
                        print(f"\n{suite.upper()} ({len(classes)} classes):")
                        for class_name in sorted(classes):
                            print(f"  {class_name}")
                return 0
                
            if args.custom:
                success = tester.run_custom_test_runner(verbose=args.verbose)
                return 0 if success else 1
                
            # Override headless setting if --gui is specified
            headless = args.headless and not args.gui
            
            # Set headless_only config temporarily if running tests in headless mode
            original_headless = env.config.get("headless_only", False)
            if headless:
                env.config["headless_only"] = True
            
            success = tester.run_tests(
                test_suite=args.suite,
                test_filter=args.test_filter,
                parallel=args.parallel,
                coverage=args.coverage,
                short=args.short,
                workers=args.workers)
            return 0 if success else 1
            
        elif args.command == "coverage":
            if not args.coverage_command:
                coverage_parser.print_help()
                return 1
                
            coverage_analyzer = CoverageAnalyzer(env)
            
            if args.coverage_command == "gaps":
                success = coverage_analyzer.analyze_coverage_gaps(
                    threshold=args.threshold,
                    focus_packages=args.focus
                )
                return 0 if success else 1
                
            elif args.coverage_command == "animation":
                success = coverage_analyzer.analyze_animation_coverage(
                    output_format=args.format
                )
                return 0 if success else 1
                
            elif args.coverage_command == "priorities":
                success = coverage_analyzer.suggest_test_priorities(
                    max_classes=args.max
                )
                return 0 if success else 1
                
            else:
                coverage_parser.print_help()
                return 1

        elif args.command == "analyze":
            analyzer = PCAnalyzer(env)
            success = analyzer.analyze(
                analysis_type=args.type,
                output_format=args.format,
                verbose=args.verbose
            )
            return 0 if success else 1

        elif args.command == "parity":
            return handle_parity_command(args, env)

        elif args.command == "validate":
            use_libreoffice = args.libreoffice or args.all_layers
            use_powerpoint = args.powerpoint or args.all_layers
            use_ooxml = not args.libreoffice and not args.powerpoint and not args.all_layers

            all_passed = True

            # Default: OOXML schema validator (existing behavior)
            if use_ooxml or args.all_layers:
                ooxml_validator = OOXMLValidator(env)
                success = ooxml_validator.validate(
                    args.file_or_dir,
                    office_version=args.office_version,
                    output_format=args.format,
                    recursive=args.recursive,
                    include_valid=args.include_valid,
                    fail_on_error=args.fail_on_error,
                )
                if not success:
                    all_passed = False

            # Layer 2: LibreOffice headless
            if use_libreoffice:
                from pc_cli.validators.libreoffice_validator import run_validation
                print("\n--- LibreOffice Headless Validation (Layer 2) ---")
                success = run_validation(args.file_or_dir, recursive=args.recursive)
                if not success:
                    all_passed = False

            # Layer 3: Wine+PowerPoint
            if use_powerpoint:
                from pc_cli.validators.powerpoint_validator import run_powerpoint_validation
                print("\n--- PowerPoint Validation via Wine (Layer 3) ---")
                if os.path.isfile(args.file_or_dir):
                    success = run_powerpoint_validation(args.file_or_dir)
                    if not success:
                        all_passed = False
                else:
                    print("PowerPoint validation only supports single files, not directories")
                    all_passed = False

            return 0 if all_passed else 1

    except KeyboardInterrupt:
        print("\nOperation cancelled")
        return 1
    except Exception as e:
        print(f"Error: {e}")
        if args.verbose:
            import traceback
            traceback.print_exc()
        return 1

if __name__ == "__main__":
    sys.exit(main())
