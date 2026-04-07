"""
Codebase Analysis for Excudo

Analyzes codebase for unused components, dependencies, and structure.
"""

import re
import json
import subprocess
from pathlib import Path
from typing import Dict, Any, List, Tuple, Set
from collections import defaultdict


class PCAnalyzer:
    """Analyzes codebase for unused components, dependencies, and structure"""

    def __init__(self, env):
        self.env = env
        self.project_root = env.project_root

    def analyze(self, analysis_type: str = "unused", output_format: str = "text", verbose: bool = False) -> bool:
        """Perform code analysis"""
        try:
            if analysis_type == "unused":
                return self._analyze_unused_classes(output_format, verbose)
            elif analysis_type == "deps":
                return self._analyze_dependencies(output_format, verbose)
            elif analysis_type == "dead-methods":
                return self._analyze_dead_methods(output_format, verbose)
            elif analysis_type == "all":
                success1 = self._analyze_unused_classes(output_format, verbose)
                success2 = self._analyze_dependencies(output_format, verbose)
                success3 = self._analyze_dead_methods(output_format, verbose)
                return success1 and success2 and success3
            else:
                print(f"Unknown analysis type: {analysis_type}")
                return False
        except Exception as e:
            print(f"Analysis failed: {e}")
            return False

    def _analyze_unused_classes(self, output_format: str, verbose: bool) -> bool:
        """Find classes that are never imported or referenced"""
        print("Analyzing unused classes...")

        # Find all Java source files
        java_files = list(self.project_root.glob("src/main/java/**/*.java"))
        print(f"Found {len(java_files)} Java files")

        # Extract class names and their files
        classes = {}
        for java_file in java_files:
            try:
                with open(java_file, 'r', encoding='utf-8') as f:
                    content = f.read()

                # Extract class name from package + class declaration
                package_match = re.search(r'package\s+([\w.]+);', content)
                class_match = re.search(r'public\s+class\s+(\w+)', content)

                if package_match and class_match:
                    package = package_match.group(1)
                    class_name = class_match.group(1)
                    full_class_name = f"{package}.{class_name}"
                    classes[full_class_name] = {
                        'file': java_file,
                        'simple_name': class_name,
                        'package': package,
                        'referenced_by': set(),
                        'references': set()
                    }
            except Exception as e:
                if verbose:
                    print(f"Warning: Could not parse {java_file}: {e}")

        print(f"Found {len(classes)} public classes")

        # Find references between classes
        for class_name, class_info in classes.items():
            try:
                with open(class_info['file'], 'r', encoding='utf-8') as f:
                    content = f.read()

                # Look for imports and class references
                for other_class, other_info in classes.items():
                    if class_name == other_class:
                        continue

                    # Check for import statements
                    if f"import {other_class}" in content:
                        class_info['references'].add(other_class)
                        other_info['referenced_by'].add(class_name)

                    # Check for simple class name usage (less reliable but catches more)
                    if other_info['simple_name'] in content and len(other_info['simple_name']) > 3:
                        # Avoid false positives on very short names
                        if re.search(rf'\b{re.escape(other_info["simple_name"])}\b', content):
                            class_info['references'].add(other_class)
                            other_info['referenced_by'].add(class_name)

            except Exception as e:
                if verbose:
                    print(f"Warning: Could not analyze references in {class_info['file']}: {e}")

        # Find unused classes (never referenced by others)
        unused_classes = []
        main_classes = []
        test_classes = []

        for class_name, class_info in classes.items():
            # Skip main application classes and tests
            if (class_info['simple_name'].endswith('App') or
                class_info['simple_name'].endswith('Application') or
                class_info['simple_name'] in ['Main', 'ExcudoApp', 'InteractiveConsole', 'HeadlessInterface']):
                main_classes.append((class_name, class_info))
            elif '/test/' in str(class_info['file']):
                test_classes.append((class_name, class_info))
            elif len(class_info['referenced_by']) == 0:
                unused_classes.append((class_name, class_info))

        # Output results
        self._output_unused_results(unused_classes, main_classes, output_format, verbose)

        return True

    def _output_unused_results(self, unused_classes: List[Tuple], main_classes: List[Tuple], output_format: str, verbose: bool):
        """Output unused class analysis results"""
        if output_format == "json":
            result = {
                "unused_classes": [{"class": name, "file": str(info['file'])} for name, info in unused_classes],
                "main_classes": [{"class": name, "file": str(info['file'])} for name, info in main_classes],
                "total_unused": len(unused_classes)
            }
            print(json.dumps(result, indent=2))
        elif output_format == "csv":
            print("Class,File,Type")
            for name, info in unused_classes:
                print(f'"{name}","{info["file"]}","unused"')
            for name, info in main_classes:
                print(f'"{name}","{info["file"]}","main"')
        else:  # text format
            print(f"\nUNUSED CLASS ANALYSIS RESULTS")
            print(f"================================")
            print(f"Unused classes: {len(unused_classes)}")
            print(f"Main/App classes: {len(main_classes)}")

            if unused_classes:
                print(f"\nPOTENTIALLY UNUSED CLASSES:")
                print(f"---------------------------")
                for name, info in sorted(unused_classes):
                    rel_path = info['file'].relative_to(self.project_root)
                    print(f"  - {info['simple_name']}")
                    if verbose:
                        print(f"    File: {rel_path}")
                        print(f"    Package: {info['package']}")

            if main_classes and verbose:
                print(f"\nMAIN/APPLICATION CLASSES:")
                print(f"-------------------------")
                for name, info in sorted(main_classes):
                    rel_path = info['file'].relative_to(self.project_root)
                    print(f"  - {info['simple_name']} - {rel_path}")

            if len(unused_classes) > 0:
                print(f"\nRECOMMENDATIONS:")
                print(f"  - Review {len(unused_classes)} unused classes for removal")
                print(f"  - Consider if any are utilities that should be integrated")
                print(f"  - Check if any are incomplete implementations")

    def _analyze_dependencies(self, output_format: str, verbose: bool) -> bool:
        """Analyze dependencies using jdeps"""
        print("Analyzing dependencies with jdeps...")

        # Need compiled classes for jdeps
        build_dir = self.project_root / "build"
        if not build_dir.exists():
            print("Build directory not found. Run 'pc.py build' first.")
            return False

        try:
            result = subprocess.run([
                "jdeps", "--print-module-deps",
                str(build_dir)
            ], capture_output=True, text=True, cwd=self.project_root)

            if result.returncode == 0:
                print("Dependency analysis completed")
                print(f"Module dependencies: {result.stdout.strip()}")
            else:
                print(f"jdeps failed: {result.stderr}")
                return False

        except Exception as e:
            print(f"Could not run jdeps: {e}")
            return False

        return True

    # ========== DEAD METHOD ANALYSIS ==========

    def _analyze_dead_methods(self, output_format: str, verbose: bool) -> bool:
        """Find methods that are declared but never referenced via bytecode analysis.

        Uses javap to extract method declarations and constant pool references
        from compiled .class files. Applies inheritance propagation to handle
        interface/abstract dispatch, then reports the difference.

        Caveats printed in output:
        - Reflection-based calls are invisible to this analysis
        - Method references (Class::method) use invokedynamic, not Methodref
        - Framework callbacks (FXML, JUnit @Test) may appear as false positives
        """
        build_dir = self.project_root / "build"
        if not build_dir.exists():
            print("Build directory not found. Run 'python3 pc.py build' first.")
            return False

        # Collect .class files -- production under build/com/, tests under build/test/
        prod_classes = sorted(build_dir.glob("com/**/*.class"))
        test_classes = sorted(build_dir.glob("test/**/*.class"))

        if not prod_classes:
            print("No production .class files found under build/com/")
            return False

        if output_format != "json":
            print(f"Scanning {len(prod_classes)} production classes, {len(test_classes)} test classes...")

        # Run javap on production classes (declarations + references)
        prod_declared, prod_referenced, inheritance = self._run_javap(prod_classes, build_dir, verbose)

        # Run javap on test classes (references only -- we don't report test dead code)
        if test_classes:
            _, test_referenced, _ = self._run_javap(test_classes, build_dir, verbose)
        else:
            test_referenced = set()

        all_referenced = prod_referenced | test_referenced

        # Propagate references through inheritance hierarchy
        self._propagate_inheritance(all_referenced, prod_declared, inheritance)

        # Compute dead = declared - referenced, then apply exclusion filters
        dead_raw = set(prod_declared.keys()) - all_referenced
        dead_filtered = []
        excluded_count = 0

        for method_key in sorted(dead_raw):
            info = prod_declared[method_key]
            if self._should_exclude(method_key, info):
                excluded_count += 1
                continue
            dead_filtered.append((method_key, info))

        self._output_dead_method_results(
            dead_filtered, len(prod_declared), excluded_count, output_format, verbose)
        return True

    def _run_javap(self, class_files: List[Path], build_dir: Path,
                   verbose: bool) -> Tuple[Dict, Set, Dict]:
        """Run javap -v -p on class files and parse the output.

        Returns (declared, referenced, inheritance) where:
        - declared: dict of (class, method, descriptor) -> {access, flags}
        - referenced: set of (class, method, descriptor)
        - inheritance: dict of class -> {super, interfaces[]}
        """
        declared = {}
        referenced = set()
        inheritance = {}  # class -> {"super": str, "interfaces": [str]}

        # Batch into chunks to avoid argument length limits
        chunk_size = 200
        for i in range(0, len(class_files), chunk_size):
            chunk = class_files[i:i + chunk_size]
            try:
                result = subprocess.run(
                    ["javap", "-v", "-p"] + [str(f) for f in chunk],
                    capture_output=True, text=True, cwd=build_dir,
                    timeout=60
                )
                output = result.stdout
            except subprocess.TimeoutExpired:
                print(f"  javap timed out on chunk {i // chunk_size + 1}")
                continue
            except Exception as e:
                if verbose:
                    print(f"  javap error on chunk {i // chunk_size + 1}: {e}")
                continue

            self._parse_javap_output(output, declared, referenced, inheritance)

        return declared, referenced, inheritance

    def _parse_javap_output(self, output: str, declared: Dict, referenced: Set,
                            inheritance: Dict):
        """Parse javap -v -p output into declared methods, references, and inheritance.

        State machine approach: track current class context from headers,
        extract Methodref/InterfaceMethodref from constant pool,
        extract method declarations from the method section.
        """
        current_class = None
        current_super = None
        current_interfaces = []
        in_constant_pool = False
        pending_method_name = None
        pending_method_access = None

        # Regex patterns
        re_this_class = re.compile(r'this_class:\s+#\d+\s+// (.+)')
        re_super_class = re.compile(r'super_class:\s+#\d+\s+// (.+)')
        re_interfaces = re.compile(r'^\s+(.+)$')
        re_methodref = re.compile(
            r'(Methodref|InterfaceMethodref)\s+#\d+\.#\d+\s+// (.+)')
        re_method_decl = re.compile(
            r'^\s+((?:public|protected|private|static|final|abstract|native|synchronized|strictfp)\s+)*'
            r'(?:[\w.<>\[\]$,\s]+?)\s+([\w$<>]+)\(')
        re_descriptor = re.compile(r'^\s+descriptor:\s+(.+)')
        re_flags = re.compile(r'^\s+flags:\s*\(0x[0-9a-f]+\)\s+(.*)')
        re_class_header = re.compile(
            r'^(?:public|protected|private)?\s*(?:abstract\s+)?(?:final\s+)?'
            r'(?:class|interface|enum)\s+([\w.$]+)')

        reading_interfaces = False

        for line in output.split('\n'):
            # Detect class boundary
            m = re_this_class.search(line)
            if m:
                # Save previous class inheritance
                if current_class and current_class.startswith("com/excudo/"):
                    inheritance[current_class] = {
                        "super": current_super,
                        "interfaces": current_interfaces
                    }
                current_class = m.group(1)
                current_super = None
                current_interfaces = []
                in_constant_pool = True
                pending_method_name = None
                continue

            m = re_super_class.search(line)
            if m:
                current_super = m.group(1)
                continue

            # Detect interface list (lines after "interfaces: N" containing class names)
            if 'interfaces:' in line and '#' not in line:
                reading_interfaces = True
                continue
            if reading_interfaces:
                if line.strip().startswith('#') or '//' in line:
                    iface_match = re.search(r'// (.+)', line)
                    if iface_match:
                        current_interfaces.append(iface_match.group(1).strip())
                    continue
                else:
                    reading_interfaces = False

            # Constant pool: extract method references
            m = re_methodref.search(line)
            if m and current_class:
                ref_comment = m.group(2).strip()
                # Format: owner/Class.methodName:descriptor
                dot_idx = ref_comment.rfind('.')
                colon_idx = ref_comment.find(':', dot_idx) if dot_idx >= 0 else -1
                if dot_idx >= 0 and colon_idx >= 0:
                    ref_owner = ref_comment[:dot_idx]
                    ref_method = ref_comment[dot_idx + 1:colon_idx]
                    ref_desc = ref_comment[colon_idx + 1:]
                    # Only track references to our own codebase
                    if ref_owner.startswith("com/excudo/"):
                        referenced.add((ref_owner, ref_method, ref_desc))
                continue

            # Method declarations section (after constant pool)
            # Detect method signature line
            if current_class and current_class.startswith("com/excudo/"):
                # Method declaration: access modifiers + return type + name(
                # We detect the method line, then read descriptor and flags
                # from subsequent lines
                if pending_method_name:
                    # Look for descriptor line
                    dm = re_descriptor.match(line)
                    if dm:
                        desc = dm.group(1).strip()
                        declared[(current_class, pending_method_name, desc)] = {
                            "access": pending_method_access,
                            "flags": ""
                        }
                        # Will update flags on next line if present
                        pending_method_name = None
                        pending_method_access = None
                        continue
                    fm = re_flags.match(line)
                    if fm:
                        # This is a flags line for the previously declared method
                        # Find the most recently added entry for this class
                        # and update its flags
                        pass
                    # If we hit something else, the pending method had no descriptor
                    # (shouldn't happen with javap output)
                    if not line.strip().startswith('descriptor:'):
                        pending_method_name = None
                        pending_method_access = None

                # Detect a method declaration line
                # javap outputs methods like:
                #   public void execute();
                #   private static java.util.Deque lambda$layoutElements$0(java.lang.String);
                stripped = line.strip()
                if stripped and not stripped.startswith('#') and not stripped.startswith('//'):
                    # Check if this looks like a method declaration
                    # (has parentheses, appears in method section)
                    if '(' in stripped and ')' in stripped and not stripped.startswith('Code:'):
                        # Extract access modifier
                        access = "package-private"
                        for mod in ("public", "protected", "private"):
                            if stripped.startswith(mod + " "):
                                access = mod
                                break
                        # Extract method name (word before the opening paren)
                        paren_idx = stripped.index('(')
                        pre_paren = stripped[:paren_idx].rstrip()
                        # Method name is the last word/token before (
                        parts = pre_paren.split()
                        if parts:
                            method_name = parts[-1]
                            # Clean up generics
                            if '.' in method_name:
                                method_name = method_name.rsplit('.', 1)[-1]
                            pending_method_name = method_name
                            pending_method_access = access

        # Save last class inheritance
        if current_class and current_class.startswith("com/excudo/"):
            inheritance[current_class] = {
                "super": current_super,
                "interfaces": current_interfaces
            }

        # Second pass: update flags for declared methods
        # Re-parse to capture flags lines that follow descriptor lines
        current_class = None
        last_declared_key = None
        for line in output.split('\n'):
            m = re_this_class.search(line)
            if m:
                current_class = m.group(1)
                last_declared_key = None
                continue
            if current_class and current_class.startswith("com/excudo/"):
                dm = re_descriptor.match(line)
                if dm:
                    # Find which declared method this descriptor belongs to
                    desc = dm.group(1).strip()
                    # Search for a matching key
                    for key in declared:
                        if key[0] == current_class and key[2] == desc:
                            last_declared_key = key
                            break
                    continue
                fm = re_flags.match(line)
                if fm and last_declared_key:
                    declared[last_declared_key]["flags"] = fm.group(1).strip()
                    last_declared_key = None
                    continue

    def _propagate_inheritance(self, referenced: Set, declared: Dict,
                               inheritance: Dict):
        """Propagate references through the class hierarchy.

        When code calls InterfaceX.method(), the Methodref targets InterfaceX.
        But the actual implementation in ConcreteY.method() is not directly
        referenced. This method bridges that gap.

        For each referenced (parent, method, descriptor), if any declared
        method exists in a subtype with the same (method, descriptor),
        add it to the referenced set.
        """
        # Build child map: parent -> [children]
        children_map = defaultdict(list)
        for cls, info in inheritance.items():
            if info["super"] and info["super"].startswith("com/excudo/"):
                children_map[info["super"]].append(cls)
            for iface in info["interfaces"]:
                if iface.startswith("com/excudo/"):
                    children_map[iface].append(cls)

        # Collect all subtypes transitively
        def get_all_subtypes(parent):
            result = set()
            stack = [parent]
            while stack:
                p = stack.pop()
                for child in children_map.get(p, []):
                    if child not in result:
                        result.add(child)
                        stack.append(child)
            return result

        # For each referenced method, propagate to subtypes
        to_add = set()
        for (ref_class, ref_method, ref_desc) in list(referenced):
            subtypes = get_all_subtypes(ref_class)
            for subtype in subtypes:
                key = (subtype, ref_method, ref_desc)
                if key in declared:
                    to_add.add(key)

        referenced.update(to_add)

    def _should_exclude(self, method_key: Tuple, info: Dict) -> bool:
        """Determine if a dead method should be excluded from the report."""
        cls, method, desc = method_key
        flags = info.get("flags", "")

        # Constructors and static initializers
        if method in ("<init>", "<clinit>"):
            return True

        # javap shows constructors with class simple name, not <init>
        # e.g. "TextBody" for com/excudo/core/model/TextBody constructor
        simple_class = cls.rsplit('/', 1)[-1] if '/' in cls else cls
        # Also handle inner class constructors: Outer$Inner -> Inner
        if '$' in simple_class:
            simple_class = simple_class.rsplit('$', 1)[-1]
        if method == simple_class:
            return True
        # Handle full inner class name pattern: "Outer$Inner" as method name
        if '$' in method:
            return True

        # Synthetic, bridge methods (compiler-generated)
        if "ACC_SYNTHETIC" in flags or "ACC_BRIDGE" in flags:
            return True

        # Lambda bodies
        if method.startswith("lambda$"):
            return True

        # Compiler-generated inner class accessors
        if re.match(r'access\$\d+', method):
            return True

        # main() entry points
        if method == "main" and desc == "([Ljava/lang/String;)V":
            return True

        # Enum synthetic methods
        if method == "values" and desc.startswith("()["):
            return True
        if method == "valueOf" and desc.startswith("(Ljava/lang/String;)"):
            return True

        # Standard Object overrides (often called via Object dispatch)
        if method in ("toString", "hashCode") and desc in ("()Ljava/lang/String;", "()I"):
            return True
        if method == "equals" and desc == "(Ljava/lang/Object;)Z":
            return True

        # Abstract methods (no implementation, just contract)
        if "ACC_ABSTRACT" in flags:
            return True

        # JavaFX lifecycle methods
        if method in ("start", "init", "stop"):
            return True

        # JUnit/test framework methods (annotated @Test, @Before, etc.)
        # These are invoked reflectively by the test runner
        # We skip test classes entirely, but just in case:
        if "test" in cls.lower() or "Test" in cls:
            return True

        return False

    def _output_dead_method_results(self, dead_methods: List[Tuple],
                                     total_declared: int, excluded_count: int,
                                     output_format: str, verbose: bool):
        """Output dead method analysis results."""
        if output_format == "json":
            methods_list = []
            for (cls, method, desc), info in dead_methods:
                methods_list.append({
                    "class": cls.replace("/", "."),
                    "method": method,
                    "descriptor": desc,
                    "access": info["access"]
                })
            result = {
                "dead_methods": methods_list,
                "total_declared": total_declared,
                "total_dead": len(dead_methods),
                "total_excluded": excluded_count
            }
            print(json.dumps(result, indent=2))
            return

        if output_format == "csv":
            print("Class,Method,Descriptor,Access")
            for (cls, method, desc), info in dead_methods:
                print(f'"{cls.replace("/", ".")}","{method}","{desc}","{info["access"]}"')
            return

        # Text format
        private_dead = [(k, v) for k, v in dead_methods if v["access"] == "private"]
        non_private_dead = [(k, v) for k, v in dead_methods if v["access"] != "private"]

        print(f"\nDEAD METHOD ANALYSIS RESULTS")
        print(f"================================")
        print(f"Total production methods declared: {total_declared:,}")
        print(f"Potentially dead methods: {len(dead_methods)}")
        print(f"  - private (high confidence): {len(private_dead)}")
        print(f"  - non-private (may be reflection/framework): {len(non_private_dead)}")
        print(f"Methods excluded (synthetic/bridge/lambda/constructor): {excluded_count}")

        if dead_methods:
            # Group by class
            by_class = defaultdict(list)
            for (cls, method, desc), info in dead_methods:
                by_class[cls].append((method, desc, info))

            if private_dead:
                print(f"\nPRIVATE DEAD METHODS (high confidence):")
                print(f"----------------------------------------")
                for cls in sorted(by_class.keys()):
                    methods = [(m, d, i) for m, d, i in by_class[cls] if i["access"] == "private"]
                    if methods:
                        print(f"  {cls.replace('/', '.')}")
                        for method, desc, info in sorted(methods, key=lambda x: x[0]):
                            readable = self._descriptor_to_readable(method, desc)
                            print(f"    - {readable}")

            if non_private_dead and verbose:
                print(f"\nNON-PRIVATE DEAD METHODS (lower confidence):")
                print(f"----------------------------------------------")
                for cls in sorted(by_class.keys()):
                    methods = [(m, d, i) for m, d, i in by_class[cls] if i["access"] != "private"]
                    if methods:
                        print(f"  {cls.replace('/', '.')}")
                        for method, desc, info in sorted(methods, key=lambda x: x[0]):
                            readable = self._descriptor_to_readable(method, desc)
                            print(f"    - [{info['access']}] {readable}")

        print(f"\nCAVEATS:")
        print(f"  - Reflection-based calls (FXML, frameworks) are invisible to this analysis")
        print(f"  - Method references (Class::method) use invokedynamic, not Methodref")
        print(f"  - Use --verbose to include non-private methods (lower confidence)")

    @staticmethod
    def _descriptor_to_readable(method_name: str, descriptor: str) -> str:
        """Convert a JVM method descriptor to a human-readable signature.

        E.g. (Ljava/lang/String;I)V -> methodName(String, int)
        """
        type_map = {
            'Z': 'boolean', 'B': 'byte', 'C': 'char', 'S': 'short',
            'I': 'int', 'J': 'long', 'F': 'float', 'D': 'double', 'V': 'void'
        }

        params = []
        i = 1  # skip opening (
        while i < len(descriptor) and descriptor[i] != ')':
            array_depth = 0
            while i < len(descriptor) and descriptor[i] == '[':
                array_depth += 1
                i += 1
            if i >= len(descriptor):
                break
            ch = descriptor[i]
            if ch in type_map:
                params.append(type_map[ch] + "[]" * array_depth)
                i += 1
            elif ch == 'L':
                end = descriptor.index(';', i)
                class_name = descriptor[i + 1:end].rsplit('/', 1)[-1]
                # Clean up inner class names
                class_name = class_name.replace('$', '.')
                params.append(class_name + "[]" * array_depth)
                i = end + 1
            else:
                i += 1

        return f"{method_name}({', '.join(params)})"
