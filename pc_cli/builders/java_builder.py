"""
Java Builder for Excudo

Handles building the Java application with intelligent JavaFX detection,
modular compilation steps, and cross-platform compatibility.
"""

import os
import shutil
import platform
import subprocess
from pathlib import Path
from typing import Optional


class PCBuilder:
    """Handles building the Java application"""
    
    def __init__(self, env):
        self.env = env
        self.project_root = env.project_root
        self.build_dir = self.project_root / "build"
        
    def clean(self) -> bool:
        """Clean build directory"""
        if self.build_dir.exists():
            print("Cleaning build directory...")
            shutil.rmtree(self.build_dir)
        return True
        
    def _find_changed_test_sources(self, test_files):
        """Return only test source files whose .class in build/test/ is missing or stale."""
        changed = []
        for src in test_files:
            rel = src.relative_to(self.project_root)
            parts = rel.parts
            if parts[0] == "src" and parts[2] == "java":
                class_rel = Path(*parts[3:]).with_suffix(".class")
            else:
                class_rel = rel.with_suffix(".class")
            class_file = self.build_dir / "test" / class_rel
            if not class_file.exists() or src.stat().st_mtime > class_file.stat().st_mtime:
                changed.append(src)
        return changed

    def _find_changed_sources(self, source_files):
        """Return only source files whose .class is missing or older than .java.
        This enables incremental compilation -- javac only sees changed files."""
        changed = []
        for src in source_files:
            # Map src/main/java/com/excudo/Foo.java -> build/com/excudo/Foo.class
            rel = src.relative_to(self.project_root)
            # Strip src/main/java/ or src/test/java/ prefix to get package path
            parts = rel.parts
            if parts[0] == "src" and parts[2] == "java":
                class_rel = Path(*parts[3:]).with_suffix(".class")
            else:
                class_rel = rel.with_suffix(".class")

            # Check both build/ and build/test/
            class_file = self.build_dir / class_rel
            if not class_file.exists() or src.stat().st_mtime > class_file.stat().st_mtime:
                changed.append(src)
        return changed

    def build(self, verbose: bool = False, clean: bool = False) -> bool:
        """Build the Java application"""
        if clean:
            self.clean()

        self.build_dir.mkdir(exist_ok=True)
        (self.build_dir / "test").mkdir(exist_ok=True)

        javac = self.env.get_javac_executable()

        # Compilation order (matches build.sh logic)
        compile_steps = [
            ("exceptions", "src/main/java/com/excudo/exceptions/*.java"),
            ("core utils", "src/main/java/com/excudo/core/utils/*.java"),
            ("core config", "src/main/java/com/excudo/core/config/*.java"),
            ("core model", "src/main/java/com/excudo/core/model/*.java"),
            ("utils", "src/main/java/com/excudo/utils/*.java"),
            ("XML builders", "src/main/java/com/excudo/xml/builders/*.java"),
            ("XML shapes", "src/main/java/com/excudo/xml/shapes/*.java"),
            ("core animations", "src/main/java/com/excudo/core/animations/*.java"),
            ("animation interfaces", "src/main/java/com/excudo/xml/writers/animations/*.java src/main/java/com/excudo/xml/writers/animations/abstracts/*.java"),
            ("parsers", "src/main/java/com/excudo/xml/parsers/*.java"),
            ("animation factories", "src/main/java/com/excudo/xml/writers/animations/concrete/*.java"),
            ("writers", "src/main/java/com/excudo/xml/writers/*.java"),
            ("core services", "src/main/java/com/excudo/core/services/*.java"),
            ("core themes", "src/main/java/com/excudo/core/themes/*.java"),
            ("theme writers", "src/main/java/com/excudo/xml/writers/themes/*.java"),
            ("core validation", "src/main/java/com/excudo/core/validation/*.java"),
            ("core results", "src/main/java/com/excudo/core/results/*.java"),
            ("core operations", "src/main/java/com/excudo/core/operations/*.java"),
            ("core geometry", "src/main/java/com/excudo/core/geometry/*.java"),
            ("core metrics", "src/main/java/com/excudo/core/metrics/*.java"),
            ("smartcontent", "src/main/java/com/excudo/core/smartcontent/*.java"),
            ("core parsing", "src/main/java/com/excudo/core/parsing/*.java"),
            ("prism4j grammars", "src/main/java/com/excudo/core/llm/prism/*.java"),
            ("mermaid ast", "src/main/java/com/excudo/core/mermaid/ast/*.java src/main/java/com/excudo/core/mermaid/ast/sequence/*.java"),
            ("mermaid parser", "src/main/java/com/excudo/core/mermaid/parser/*.java src/main/java/com/excudo/core/mermaid/parser/flowchart/*.java src/main/java/com/excudo/core/mermaid/parser/sequence/*.java"),
            ("mermaid layout", "src/main/java/com/excudo/core/mermaid/layout/*.java src/main/java/com/excudo/core/mermaid/layout/flowchart/*.java src/main/java/com/excudo/core/mermaid/layout/common/*.java src/main/java/com/excudo/core/mermaid/layout/sequence/*.java"),
            ("mermaid emitter", "src/main/java/com/excudo/core/mermaid/emitter/*.java"),
            ("application core", "src/main/java/com/excudo/core/inspection/*.java src/main/java/com/excudo/core/orchestration/*.java src/main/java/com/excudo/core/commands/*.java src/main/java/com/excudo/core/llm/*.java src/main/java/com/excudo/console/*.java src/main/java/com/excudo/console/utils/*.java src/main/java/com/excudo/cli/*.java"),
        ]
        
        classpath = self.env.get_classpath()
        javac_flags = ["-Xlint:deprecation", "-Xlint:unchecked", "-Xlint:-options"]
        
        skipped_steps = 0
        compiled_files = 0
        for step_name, pattern in compile_steps:
            # Expand glob patterns (handle multiple patterns separated by spaces)
            source_files = []
            patterns = pattern.split() if ' ' in pattern else [pattern]
            for p in patterns:
                source_files.extend(list(self.project_root.glob(p)))

            if not source_files:
                if verbose:
                    print(f"  No files found for pattern: {pattern}")
                continue

            # Incremental: only recompile files whose .class is stale or missing
            if not clean:
                changed = self._find_changed_sources(source_files)
                if not changed:
                    skipped_steps += 1
                    if verbose:
                        print(f"Compiling {step_name}... (up to date)")
                    continue
                source_files = changed

            print(f"Compiling {step_name}... ({len(source_files)} file{'s' if len(source_files) != 1 else ''})")
            compiled_files += len(source_files)

            cmd = [javac] + javac_flags + [
                "-cp", classpath,
                "-d", "build"
            ] + [str(f) for f in source_files]

            if verbose:
                print(f"  Running: {' '.join(cmd)}")
                print(f"  Classpath: {classpath}")

            try:
                result = subprocess.run(cmd, cwd=self.project_root,
                                      capture_output=True, text=True)
                if result.returncode != 0:
                    print(f"X Build failed at {step_name}")
                    if result.stderr:
                        print(result.stderr)
                    if result.stdout:
                        print(result.stdout)
                    # Ensure we don't continue with a failed build
                    if self.build_dir.exists():
                        # Remove partial build artifacts to prevent masking errors
                        print("Removing partial build artifacts...")
                        shutil.rmtree(self.build_dir)
                    return False
                elif verbose and result.stderr:
                    # Show warnings/deprecation messages in verbose mode
                    print(result.stderr)

            except Exception as e:
                print(f"X Build failed at {step_name}: {e}")
                return False

        if skipped_steps > 0 and not clean:
            print(f"Skipped {skipped_steps} unchanged step{'s' if skipped_steps != 1 else ''}, compiled {compiled_files} file{'s' if compiled_files != 1 else ''}")
                
        # Compile JavaFX view layer (if available)
        view_layer_success = self._build_view_layer(verbose)
        
        # Compile main application (skip JavaFX-dependent files if view layer failed)
        main_files = list(self.project_root.glob("src/main/java/com/excudo/*.java"))

        # Filter out JavaFX-dependent files if view layer compilation failed
        if not view_layer_success:
            main_files = [f for f in main_files if "ExcudoApp" not in f.name]

        # Incremental: skip if all main files are up to date
        if main_files and not clean:
            main_files = self._find_changed_sources(main_files)

        if main_files:
            print(f"Compiling main application... ({len(main_files)} file{'s' if len(main_files) != 1 else ''})")
            # Add JavaFX module configuration if available (same as view layer)
            javafx_modules = []
            javafx_lib_path = self.project_root / "lib" / "javafx-sdk-21" / "lib"
            if javafx_lib_path.exists() and view_layer_success:
                javafx_modules = ["--module-path", str(javafx_lib_path), 
                                  "--add-modules", "javafx.controls,javafx.fxml,javafx.swing,javafx.graphics"]
            
            cmd = [javac] + javac_flags + [
                "-cp", classpath,
                "-d", "build"
            ] + javafx_modules + [str(f) for f in main_files]
            
            try:
                result = subprocess.run(cmd, cwd=self.project_root,
                                      capture_output=True, text=True)
                if result.returncode != 0:
                    print("X Build failed at main application")
                    if result.stderr:
                        print(result.stderr)
                    if result.stdout:
                        print(result.stdout)
                    return False
                elif verbose and result.stderr:
                    print(result.stderr)
            except Exception as e:
                print(f"X Build failed at main application: {e}")
                return False
                
        # Compile test classes
        self._build_tests(verbose)
        
        # Copy resources
        self._copy_resources()
        
        print("+ Build successful!")
        return True
        
    def _detect_javafx_availability(self) -> tuple[bool, str]:
        """Detect if JavaFX is available in the current environment"""
        try:
            # Check if JavaFX SDK is present first
            javafx_lib_path = self.project_root / "lib" / "javafx-sdk-21" / "lib"
            if not javafx_lib_path.exists():
                return False, "JavaFX SDK not found in lib/javafx-sdk-21/"
            
            # Check if JavaFX JAR files exist
            javafx_controls = javafx_lib_path / "javafx.controls.jar"
            javafx_fxml = javafx_lib_path / "javafx.fxml.jar"
            if not javafx_controls.exists() or not javafx_fxml.exists():
                return False, f"JavaFX JAR files missing in {javafx_lib_path}"
            
            # Try to compile a simple JavaFX test
            test_code = """
import javafx.application.Application;
import javafx.stage.Stage;
public class JavaFXTest extends Application {
    public void start(Stage stage) {
        System.out.println("JavaFX is available");
    }
    public static void main(String[] args) {
        System.out.println("JavaFX detected");
    }
}
"""
            test_file = self.project_root / "JavaFXTest.java"
            test_file.write_text(test_code)
            
            javac = self.env.get_javac_executable()
            # Use the same classpath that we use for actual compilation
            classpath = self.env.get_classpath()
            
            # Include all JavaFX modules
            javafx_modules = ["--module-path", str(javafx_lib_path), 
                              "--add-modules", "javafx.controls,javafx.fxml,javafx.graphics,javafx.base"]
            
            cmd = [javac, "-cp", classpath] + javafx_modules + [str(test_file)]
            
            # Add headless flags for CI environments
            env = os.environ.copy()
            env.update({
                "JAVA_TOOL_OPTIONS": "-Djava.awt.headless=true -Dprism.order=sw",
                "DISPLAY": os.environ.get("DISPLAY", ":99")  # Use existing display or default
            })
            
            result = subprocess.run(cmd, capture_output=True, text=True, cwd=self.project_root, env=env)
            
            # Clean up test file
            test_file.unlink(missing_ok=True)
            (self.project_root / "JavaFXTest.class").unlink(missing_ok=True)
            
            if result.returncode == 0:
                return True, "JavaFX available"
            else:
                # Check if it's a module issue
                if "module" in result.stderr.lower():
                    return False, "JavaFX module configuration needed"
                else:
                    return False, f"JavaFX not available: {result.stderr.strip()}"
                
        except Exception as e:
            return False, f"JavaFX detection failed: {e}"

    def _build_view_layer(self, verbose: bool) -> bool:
        """Build JavaFX view layer with intelligent environment detection"""
        view_files = list(self.project_root.glob("src/main/java/com/excudo/view/**/*.java"))
        if not view_files:
            return True
        
        # Check if user explicitly disabled GUI compilation
        if self.env.config.get("headless_only", False):
            print("Skipping view layer compilation (headless_only=true in config)")
            return False

        # Incremental: skip if all view files are up to date
        changed_view = self._find_changed_sources(view_files)
        if not changed_view:
            if verbose:
                print("Compiling view layer (JavaFX)... (up to date)")
            return True

        view_files = changed_view
        print(f"Compiling view layer (JavaFX)... ({len(view_files)} file{'s' if len(view_files) != 1 else ''})")
        
        # Detect JavaFX availability
        javafx_available, javafx_status = self._detect_javafx_availability()
        if not javafx_available:
            print(f"! Skipping JavaFX compilation: {javafx_status}")
            print("  To enable JavaFX compilation:")
            print("  1. Install JavaFX SDK or use a JDK with JavaFX")
            print("  2. Set 'headless_only: false' in config if needed")
            return False
        
        javac = self.env.get_javac_executable()
        classpath = self.env.get_classpath()
        javac_flags = ["-Xlint:deprecation", "-Xlint:unchecked", "-Xlint:-options"]
        
        # Add JavaFX module configuration if available
        javafx_modules = []
        javafx_lib_path = self.project_root / "lib" / "javafx-sdk-21" / "lib"
        if javafx_lib_path.exists():
            javafx_modules = ["--module-path", str(javafx_lib_path), 
                              "--add-modules", "javafx.controls,javafx.fxml,javafx.swing,javafx.graphics"]
        
        cmd = [javac] + javac_flags + [
            "-cp", classpath,
            "-d", "build"
        ] + javafx_modules + [str(f) for f in view_files]
        
        # Use xvfb-run on Linux for headless environments with X11
        is_headless = (
            platform.system() == "Linux" and 
            not os.environ.get("DISPLAY") and 
            shutil.which("xvfb-run")
        )
        
        if is_headless:
            print("  Using xvfb-run for headless compilation")
            cmd = ["xvfb-run", "-a"] + cmd
            
        try:
            result = subprocess.run(cmd, cwd=self.project_root,
                                  capture_output=True, text=True)
            if result.returncode != 0:
                print("X View layer compilation failed")
                if result.stderr:
                    print("Error details:")
                    print(result.stderr)
                if result.stdout:
                    print(result.stdout)
                print("  This is usually due to:")
                print("  1. Missing JavaFX dependencies")
                print("  2. Incorrect classpath configuration")
                print("  3. Incompatible Java version")
                return False
            else:
                print("+ View layer compiled successfully")
                return True
        except Exception as e:
            print(f"X View layer compilation failed: {e}")
            return False
            
    def _build_tests(self, verbose: bool) -> bool:
        """Compile test classes"""
        test_files = list(self.project_root.glob("src/test/java/**/*.java"))
        if not test_files:
            return True
            
        # Check if we're in headless mode
        is_headless_mode = self.env.config.get("headless_only", False)
        
        # Filter out JavaFX-dependent tests if in headless mode
        if is_headless_mode:
            # Exclude all tests in the view package when headless
            view_test_path = self.project_root / "src/test/java/com/excudo/view"
            test_files = [f for f in test_files if not str(f).startswith(str(view_test_path))]
            
            if not test_files:
                print("No non-JavaFX tests to compile in headless mode")
                return True
                
            print(f"Compiling test classes (headless mode - excluding {view_test_path.name}/ tests)...")
        else:
            print("Compiling test classes...")

        # If any production .class is newer than the oldest test .class,
        # recompile ALL tests (test code depends on production classes).
        prod_classes = list(self.build_dir.glob("com/**/*.class"))
        test_classes = list((self.build_dir / "test").glob("com/**/*.class"))
        newest_prod = max((f.stat().st_mtime for f in prod_classes), default=0) if prod_classes else 0
        oldest_test = min((f.stat().st_mtime for f in test_classes), default=float('inf')) if test_classes else float('inf')

        if newest_prod > oldest_test:
            # Production code changed -- recompile all tests against new classes
            print(f"  Production classes changed, recompiling all tests ({len(test_files)} files)")
        else:
            # Only recompile test files whose own source changed
            changed_tests = self._find_changed_test_sources(test_files)
            if not changed_tests:
                print("+ Test classes up to date")
                return True
            test_files = changed_tests
            print(f"  {len(test_files)} file{'s' if len(test_files) != 1 else ''} to compile")

        javac = self.env.get_javac_executable()
        classpath = self.env.get_classpath()

        # Include JavaFX modules for test compilation only if not in headless mode
        javafx_modules = []
        if not is_headless_mode:
            javafx_lib_path = self.project_root / "lib" / "javafx-sdk-21" / "lib"
            if javafx_lib_path.exists():
                javafx_modules = [
                    "--module-path", str(javafx_lib_path),
                    "--add-modules", "javafx.controls,javafx.fxml,javafx.web,javafx.swing"
                ]

        cmd = [javac, "-cp", classpath + ":build", "-d", "build/test"] + javafx_modules + [str(f) for f in test_files]
        
        # Use xvfb-run on Linux for headless environments with JavaFX tests
        is_headless_system = (
            platform.system() == "Linux" and 
            not os.environ.get("DISPLAY") and 
            shutil.which("xvfb-run")
        )
        
        if is_headless_system and javafx_modules and not is_headless_mode:
            print("  Using xvfb-run for headless test compilation")
            cmd = ["xvfb-run", "-a"] + cmd
        
        try:
            result = subprocess.run(cmd, cwd=self.project_root,
                                  capture_output=True, text=True)
            if result.returncode != 0:
                print("! Test compilation failed")
                if result.stderr:
                    print(result.stderr)
                if result.stdout:
                    print(result.stdout)
                return False
            elif verbose and result.stderr:
                print(result.stderr)
            else:
                excluded_count = len(list(self.project_root.glob("src/test/java/**/view/**/*.java"))) if is_headless_mode else 0
                if excluded_count > 0:
                    print(f"+ Test classes compiled successfully ({excluded_count} JavaFX tests excluded)")
                else:
                    print("+ Test classes compiled successfully")
                return True
        except Exception as e:
            print(f"! Test compilation failed: {e}")
            return False
            
    def _copy_resources(self) -> bool:
        """Copy resource files"""
        resources_dir = self.project_root / "src/main/resources"
        if resources_dir.exists():
            print("Copying resources...")
            try:
                shutil.copytree(resources_dir, self.build_dir / "resources", dirs_exist_ok=True)
                print("+ Resources copied successfully")
                return True
            except Exception as e:
                print(f"! Resource copying failed: {e}")
                return False
        return True