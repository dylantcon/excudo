"""
JUnit Tester for Excudo

Handles test discovery, execution, and management with support for parallel execution,
coverage reporting, and cross-platform compatibility.
"""

import os
import re
import shutil
import threading
import subprocess
from pathlib import Path
from typing import Dict, List, Optional
from concurrent.futures import ThreadPoolExecutor, as_completed


class PCTester:
    """Handles test execution and management"""
    
    def __init__(self, env):
        self.env = env
        self.project_root = env.project_root
        self.test_output_dir = self.project_root / "test-output"
        self.junit_jar = self.project_root / "lib" / "junit-platform-console-standalone-1.9.3.jar"
        
    def discover_tests(self, test_filter: Optional[str] = None) -> Dict[str, List[str]]:
        """Discover all test classes and methods"""
        test_classes = {
            "unit": [],
            "integration": [],
            "all": []
        }
        
        # Check if we're in headless mode
        is_headless_mode = self.env.config.get("headless_only", False)
        
        # Find all test Java files
        test_files = list(self.project_root.glob("src/test/java/**/*Test.java"))
        
        # Only include Demo classes in non-CI environments or if specifically requested
        include_demos = os.environ.get('INCLUDE_DEMOS', 'false').lower() == 'true'
        if include_demos and not os.environ.get('CI', '').lower() == 'true':
            test_files.extend(list(self.project_root.glob("src/test/java/**/*Demo.java")))
        
        # Filter out JavaFX-dependent tests if in headless mode
        if is_headless_mode:
            view_test_path = self.project_root / "src/test/java/com/excudo/view"
            test_files = [f for f in test_files if not str(f).startswith(str(view_test_path))]
        
        for test_file in test_files:
            # Convert file path to class name (cross-platform)
            relative_path = test_file.relative_to(self.project_root / "src/test/java")
            class_name = str(relative_path).replace(os.sep, ".").replace(".java", "")
            
            # Apply filter if specified
            if test_filter and not re.search(test_filter, class_name):
                continue
                
            # Categorize tests
            if "integration" in class_name.lower() or "demo" in class_name.lower():
                test_classes["integration"].append(class_name)
            else:
                test_classes["unit"].append(class_name)
            test_classes["all"].append(class_name)
            
        return test_classes
        
    def run_tests(self, test_suite: str = "all", test_filter: Optional[str] = None,
                  parallel: bool = False, coverage: bool = False, headless: bool = True,
                  verbose: bool = False, short: bool = False) -> bool:
        """Run tests with specified options"""

        # Ensure build directory exists and is up-to-date
        if not self._ensure_tests_compiled(test_filter):
            print("X Test compilation failed")
            return False

        # Create test output directory
        self.test_output_dir.mkdir(exist_ok=True)

        # Discover tests
        test_classes = self.discover_tests(test_filter)

        if test_suite not in test_classes:
            print(f"Unknown test suite: {test_suite}")
            print(f"Available suites: {', '.join(test_classes.keys())}")
            return False

        classes_to_run = test_classes[test_suite]
        if not classes_to_run:
            print(f"No tests found for suite '{test_suite}'" +
                  (f" with filter '{test_filter}'" if test_filter else ""))
            return False

        print(f"Running {len(classes_to_run)} test class(es) from suite '{test_suite}'...")

        # Clear previous coverage data if coverage is enabled
        if coverage:
            self._clear_coverage_data()

        # Run tests
        success = self._run_test_batch(classes_to_run, parallel, coverage, headless, verbose, short)

        # Generate coverage report if coverage was enabled
        if success and coverage:
            print("\n> Generating coverage report...")
            self.generate_coverage_report()

        return success
    
    def _ensure_tests_compiled(self, test_filter: Optional[str] = None) -> bool:
        """Ensure test classes are compiled"""
        test_build_dir = self.project_root / "build" / "test"
        
        # Check if we're in headless mode
        is_headless_mode = self.env.config.get("headless_only", False)
        
        # Always try to compile tests explicitly since main build might skip them
        if test_filter:
            print(f"Compiling test classes (filtered by: {test_filter})...")
        else:
            print("Compiling test classes...")
        
        # Find test source files - filter if specific test requested
        if test_filter:
            # Only compile the specific test file(s) matching the filter
            all_test_files = list(self.project_root.glob("src/test/java/**/*.java"))
            test_files = []
            matched_packages = set()
            for test_file in all_test_files:
                # Convert file path to class name for filtering
                relative_path = test_file.relative_to(self.project_root / "src/test/java")
                class_name = str(relative_path).replace(os.sep, ".").replace(".java", "")
                if re.search(test_filter, class_name):
                    test_files.append(test_file)
                    matched_packages.add(test_file.parent)

            if not test_files:
                print(f"No test files found matching filter: {test_filter}")
                return True

            # Include non-Test helper/fixture files from matched packages
            # (e.g., WriterTestFixtures used by multiple test classes)
            # Only adds files that don't end in Test.java to avoid pulling in
            # unrelated test classes with their own dependency chains.
            helpers_added = 0
            for pkg_dir in matched_packages:
                for java_file in pkg_dir.glob("*.java"):
                    if java_file not in test_files and not java_file.name.endswith("Test.java"):
                        test_files.append(java_file)
                        helpers_added += 1

            # Always include shared test utilities (test/utils/) since they may
            # be imported by tests in any package (e.g., StubPPTXOrchestrator)
            test_utils_dir = self.project_root / "src/test/java/com/excudo/test/utils"
            if test_utils_dir.exists() and test_utils_dir not in matched_packages:
                for java_file in test_utils_dir.glob("*.java"):
                    if java_file not in test_files:
                        test_files.append(java_file)
                        helpers_added += 1

            print(f"Found {len(test_files) - helpers_added} test file(s) matching filter" +
                  (f" (+{helpers_added} helper(s))" if helpers_added else "") + ":")
            for tf in test_files:
                print(f"  {tf.relative_to(self.project_root)}")
        else:
            # Find all test source files (original behavior for full compilation)
            test_files = list(self.project_root.glob("src/test/java/**/*.java"))
            if not test_files:
                print("No test files found")
                return True
        
        # Filter out JavaFX-dependent tests if in headless mode
        if is_headless_mode:
            view_test_path = self.project_root / "src/test/java/com/excudo/view"
            original_count = len(test_files)
            test_files = [f for f in test_files if not str(f).startswith(str(view_test_path))]
            excluded_count = original_count - len(test_files)
            
            if excluded_count > 0:
                print(f"  Excluding {excluded_count} JavaFX test(s) in headless mode")
            
            if not test_files:
                print("No non-JavaFX tests to compile in headless mode")
                return True
        
        # Create test build directory
        test_build_dir.mkdir(parents=True, exist_ok=True)
        
        # Get compilation classpath
        lib_classpath = self._get_test_classpath()
        # Include main classes and previously-compiled test classes in classpath
        # (filtered compilations may depend on test helpers compiled in earlier full builds)
        classpath = f"build{os.pathsep}build/test{os.pathsep}{lib_classpath}"
        javac = self.env.get_javac_executable()
        
        # Build compilation command with JavaFX module path if available
        cmd = [javac, "-cp", classpath]
        
        # Add JavaFX module path for test compilation only if not in headless mode
        if not is_headless_mode:
            javafx_lib_path = self.project_root / "lib" / "javafx-sdk-21" / "lib"
            if javafx_lib_path.exists():
                cmd.extend(["--module-path", str(javafx_lib_path), 
                           "--add-modules", "javafx.controls,javafx.fxml,javafx.web,javafx.swing"])
        
        # Source list goes through a javac @argfile: compiling every test
        # (276+ absolute paths) inline exceeds the Windows 32K command-line
        # limit (WinError 206).
        argfile = test_build_dir / "test-sources.args"
        argfile.write_text(
            "\n".join('"' + str(f).replace("\\", "/") + '"' for f in test_files),
            encoding="utf-8")
        cmd.extend(["-d", "build/test", f"@{argfile}"])
        
        if self.env.config.get("verbose", False):
            print(f"  Test compilation command: {' '.join(cmd)}")
            print(f"  Test compilation classpath: {classpath}")
        
        try:
            result = subprocess.run(cmd, cwd=self.project_root, capture_output=True, text=True)
            if result.returncode != 0:
                print("X Test compilation failed")
                if result.stderr:
                    print("Compilation errors:")
                    print(result.stderr)
                if result.stdout:
                    print("Compilation output:")
                    print(result.stdout)
                return False
            else:
                print("+ Test classes compiled successfully")
                return True
        except Exception as e:
            print(f"X Test compilation failed: {e}")
            return False
    
    def _run_test_batch(self, test_classes: List[str], parallel: bool,
                        coverage: bool, headless: bool,
                        verbose: bool = False, short: bool = False) -> bool:
        """Run a batch of test classes, either sequentially or in parallel.
        Unified execution path -- same output format regardless of mode."""
        passed = 0
        failed = 0
        failed_classes = []

        def _record_result(test_class: str, result: bool):
            nonlocal passed, failed
            if not short:
                print(f"  {test_class}: {'PASSED' if result else 'FAILED'}")
            if result:
                passed += 1
            else:
                failed += 1
                failed_classes.append(test_class)

        if parallel and len(test_classes) > 1:
            with ThreadPoolExecutor(max_workers=min(len(test_classes), os.cpu_count() or 4)) as executor:
                futures = {
                    executor.submit(self._run_single_test_class, tc, coverage, headless, verbose, short): tc
                    for tc in test_classes
                }
                for future in as_completed(futures):
                    test_class = futures[future]
                    try:
                        _record_result(test_class, future.result())
                    except Exception as e:
                        if not short:
                            print(f"  {test_class}: EXCEPTION - {e}")
                        failed += 1
                        failed_classes.append(test_class)
        else:
            for test_class in test_classes:
                result = self._run_single_test_class(test_class, coverage, headless, verbose, short)
                _record_result(test_class, result)

        total = passed + failed
        print(f"\nResults: {passed}/{total} passed" +
              (f", {failed} FAILED" if failed else ""))
        for cls in failed_classes:
            print(f"  FAIL: {cls}")

        return failed == 0
    
    def _run_single_test_class(self, test_class: str, coverage: bool, headless: bool,
                              verbose: bool = False, short: bool = False) -> bool:
        """Run a single test class"""
        if self._is_junit_test(test_class):
            return self._run_junit_test(test_class, coverage, headless, verbose, short)
        else:
            return self._run_custom_test(test_class, coverage, headless, verbose, short)
    
    def _is_junit_test(self, test_class: str) -> bool:
        """Check if a test class uses JUnit annotations"""
        # Look for the test class file to check for JUnit annotations
        # Convert class name to file path (platform-agnostic)
        class_path_parts = test_class.split(".")
        test_file_path = self.project_root / "src/test/java"
        for part in class_path_parts:
            test_file_path = test_file_path / part
        test_file_path = test_file_path.with_suffix(".java")
        if test_file_path.exists():
            try:
                content = test_file_path.read_text()
                # Check for JUnit annotations or imports
                return "@Test" in content or "import org.junit" in content
            except Exception:
                # If we can't read the file, assume JUnit
                return True
        
        # Default to JUnit if we can't find the file
        return True
    
    def _run_junit_test(self, test_class: str, coverage: bool, headless: bool,
                        verbose: bool = False, short: bool = False) -> bool:
        """Run a JUnit test using the JUnit Platform Console Launcher"""
        if not self.junit_jar.exists():
            print(f"JUnit JAR not found at {self.junit_jar}")
            return False

        java = self.env.get_java_executable()
        classpath = f"build/test{os.pathsep}build{os.pathsep}{self._get_test_classpath()}"

        # Set up environment for headless testing
        env_vars = os.environ.copy()
        if headless:
            env_vars.update({
                "JAVA_TOOL_OPTIONS": "-Djava.awt.headless=true -Dprism.order=sw -Dprism.verbose=true -Dglass.platform=Monocle -Dmonocle.platform=Headless",
                "TESTFX_HEADLESS": "true"
            })

        # Build command with JaCoCo agent if coverage is enabled
        cmd = [java]
        if coverage:
            jacoco_agent = self.project_root / "lib" / "jacocoagent.jar"
            if jacoco_agent.exists():
                cmd.extend([f"-javaagent:{jacoco_agent}=destfile=jacoco.exec,append=true"])

        cmd.extend(["-jar", str(self.junit_jar),
                   "--classpath", classpath, "--select-class", test_class])

        if verbose:
            cmd.append("--details=verbose")

        simple_name = test_class.rsplit(".", 1)[-1]

        try:
            result = subprocess.run(cmd, cwd=self.project_root, env=env_vars,
                                  capture_output=True, text=True)

            if verbose:
                if result.stdout:
                    print(result.stdout)
                if result.stderr:
                    print(result.stderr)
            elif result.returncode != 0 and not short:
                # Show output on failure for diagnostics
                if result.stdout:
                    print(result.stdout)
                if result.stderr:
                    print(result.stderr)

            # In short mode, only print failures
            if short and result.returncode != 0:
                print(f"  FAIL: {simple_name}")
                if result.stdout:
                    for line in result.stdout.splitlines():
                        if "failed" in line.lower() or "error" in line.lower():
                            print(f"    {line.strip()}")

            return result.returncode == 0
        except Exception as e:
            print(f"Error running JUnit test {test_class}: {e}")
            return False

    def _run_custom_test(self, test_class: str, coverage: bool, headless: bool,
                         verbose: bool = False, short: bool = False) -> bool:
        """Run a custom test with main method"""
        java = self.env.get_java_executable()
        classpath = f"build/test{os.pathsep}build{os.pathsep}{self._get_test_classpath()}"

        # Set up environment for headless testing
        env_vars = os.environ.copy()
        if headless:
            env_vars.update({
                "JAVA_TOOL_OPTIONS": "-Djava.awt.headless=true",
                "DISPLAY": ":99"
            })

        # Build command with JaCoCo agent if coverage is enabled
        cmd = [java]
        if coverage:
            jacoco_agent = self.project_root / "lib" / "jacocoagent.jar"
            if jacoco_agent.exists():
                cmd.extend([f"-javaagent:{jacoco_agent}=destfile=jacoco.exec,append=true"])

        cmd.extend(["-cp", classpath, test_class])

        if not short:
            print(f"Running custom test: {test_class}")

        try:
            result = subprocess.run(cmd, cwd=self.project_root, env=env_vars,
                                  capture_output=True, text=True)

            if verbose or (result.returncode != 0 and not short):
                if result.stdout:
                    print(result.stdout)
                if result.stderr:
                    print(result.stderr)

            if short and result.returncode != 0:
                simple_name = test_class.rsplit(".", 1)[-1]
                print(f"  FAIL: {simple_name}")

            return result.returncode == 0
        except Exception as e:
            print(f"Error running custom test {test_class}: {e}")
            return False
    
    def _get_test_classpath(self) -> str:
        """Get classpath for test compilation and execution"""
        return self.env.get_classpath()
    
    def _clear_coverage_data(self):
        """Clear previous coverage data"""
        coverage_file = self.project_root / "jacoco.exec"
        if coverage_file.exists():
            coverage_file.unlink()
            print("  Cleared previous JaCoCo coverage data")
    
    def generate_coverage_report(self):
        """Generate coverage report using JaCoCo"""
        coverage_file = self.project_root / "jacoco.exec"
        if not coverage_file.exists():
            print("  No coverage data found (jacoco.exec missing)")
            return
            
        # Create coverage report directory
        report_dir = self.project_root / "coverage-report"
        report_dir.mkdir(exist_ok=True)
        
        # Generate HTML report using JaCoCo CLI
        jacoco_cli = self.project_root / "lib" / "jacococli.jar"
        if not jacoco_cli.exists():
            print("  JaCoCo CLI not found - coverage report skipped")
            return
            
        java = self.env.get_java_executable()
        src_dirs = [
            self.project_root / "src" / "main" / "java",
            self.project_root / "src" / "test" / "java"
        ]
        
        # Build source directories that exist
        existing_src_dirs = [str(d) for d in src_dirs if d.exists()]
        if not existing_src_dirs:
            print("  No source directories found for coverage report")
            return
            
        # Find production class files only - use specific directory structure
        production_class_dir = self.project_root / "build" / "com"
        if not production_class_dir.exists():
            print("  No production class files found in build/com - ensure main classes are compiled")
            return
            
        # Count production classes for verification
        production_classes = list(production_class_dir.rglob("*.class"))
        print(f"  Analyzing {len(production_classes)} production class files for coverage")
        
        if len(production_classes) == 0:
            print("  No production class files found for coverage analysis")
            return
            
        cmd = [
            java, "-jar", str(jacoco_cli), "report", str(coverage_file),
            "--sourcefiles", str(self.project_root / "src" / "main" / "java"),
            "--classfiles", str(production_class_dir),
            "--html", str(report_dir / "html"),
            "--xml", str(report_dir / "jacoco.xml"),
            "--csv", str(report_dir / "jacoco.csv")
        ]
        
        try:
            result = subprocess.run(cmd, cwd=self.project_root, capture_output=True, text=True)
            if result.returncode == 0:
                print(f"  Coverage report generated: {report_dir / 'html' / 'index.html'}")
                self._parse_coverage_summary(report_dir / "jacoco.csv")
            else:
                print(f"  Coverage report generation failed: {result.stderr}")
        except Exception as e:
            print(f"  Error generating coverage report: {e}")
    
    def _parse_coverage_summary(self, csv_file: Path):
        """Parse and display coverage summary from CSV report"""
        if not csv_file.exists():
            return
            
        try:
            with open(csv_file, 'r') as f:
                lines = f.readlines()
                if len(lines) < 2:
                    return
                    
            # Parse CSV header
            header = lines[0].strip().split(',')
            
            # Find relevant columns
            try:
                instruction_missed_idx = header.index('INSTRUCTION_MISSED')
                instruction_covered_idx = header.index('INSTRUCTION_COVERED')
                branch_missed_idx = header.index('BRANCH_MISSED')
                branch_covered_idx = header.index('BRANCH_COVERED')
                line_missed_idx = header.index('LINE_MISSED')
                line_covered_idx = header.index('LINE_COVERED')
                
                # Sum all rows (excluding header)
                total_inst_missed = 0
                total_inst_covered = 0
                total_branch_missed = 0
                total_branch_covered = 0
                total_line_missed = 0
                total_line_covered = 0
                
                for line in lines[1:]:  # Skip header
                    if line.strip():
                        parts = line.strip().split(',')
                        if len(parts) > max(instruction_missed_idx, instruction_covered_idx, 
                                          branch_missed_idx, branch_covered_idx,
                                          line_missed_idx, line_covered_idx):
                            total_inst_missed += int(parts[instruction_missed_idx])
                            total_inst_covered += int(parts[instruction_covered_idx])
                            total_branch_missed += int(parts[branch_missed_idx])
                            total_branch_covered += int(parts[branch_covered_idx])
                            total_line_missed += int(parts[line_missed_idx])
                            total_line_covered += int(parts[line_covered_idx])
                
                inst_total = total_inst_missed + total_inst_covered
                branch_total = total_branch_missed + total_branch_covered
                line_total = total_line_missed + total_line_covered
                
                print(f"\n  Coverage Summary:")
                if inst_total > 0:
                    inst_pct = (total_inst_covered / inst_total) * 100
                    print(f"    Instructions: {total_inst_covered:,}/{inst_total:,} ({inst_pct:.1f}%)")
                if branch_total > 0:
                    branch_pct = (total_branch_covered / branch_total) * 100
                    print(f"    Branches: {total_branch_covered:,}/{branch_total:,} ({branch_pct:.1f}%)")
                if line_total > 0:
                    line_pct = (total_line_covered / line_total) * 100
                    print(f"    Lines: {total_line_covered:,}/{line_total:,} ({line_pct:.1f}%)")
                    
            except (ValueError, IndexError) as e:
                print(f"    Error parsing coverage data: {e}")
                
        except Exception as e:
            print(f"    Error reading coverage summary: {e}")