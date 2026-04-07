"""
Coverage Analyzer for Excudo

Analyzes JaCoCo coverage reports to identify testing gaps and provide actionable insights.
"""

import csv
import json
from pathlib import Path
from typing import Dict, List, Optional, Tuple


class CoverageAnalyzer:
    """Analyzes test coverage reports and provides gap analysis"""
    
    def __init__(self, env):
        self.env = env
        self.project_root = env.project_root
        
    def analyze_coverage_gaps(self, threshold: int = 80, focus_packages: Optional[List[str]] = None) -> bool:
        """
        Analyze coverage gaps from JaCoCo CSV report
        
        Args:
            threshold: Minimum coverage percentage threshold (default: 80)
            focus_packages: List of package patterns to focus on (default: animation + core packages)
            
        Returns:
            bool: True if analysis completed successfully
        """
        csv_path = self.project_root / "coverage-report" / "jacoco.csv"
        
        if not csv_path.exists():
            print("❌ Coverage report not found")
            print("   Run 'python3 pc.py test all --coverage' first to generate coverage data")
            return False
            
        if focus_packages is None:
            focus_packages = ["animation", "core"]
            
        try:
            gaps, animation_classes = self._parse_coverage_data(csv_path, threshold, focus_packages)
            self._print_analysis(gaps, animation_classes, threshold, focus_packages)
            return True
            
        except Exception as e:
            print(f"❌ Error analyzing coverage: {e}")
            return False
    
    def analyze_animation_coverage(self, output_format: str = "text") -> bool:
        """
        Focused analysis of animation architecture coverage
        
        Args:
            output_format: Output format (text, json, csv)
            
        Returns:
            bool: True if analysis completed successfully
        """
        csv_path = self.project_root / "coverage-report" / "jacoco.csv"
        
        if not csv_path.exists():
            print("❌ Coverage report not found")
            print("   Run 'python3 pc.py test all --coverage' first to generate coverage data")
            return False
            
        try:
            animation_packages = [
                "xml.writers.animations",
                "xml.writers.animations.concrete", 
                "xml.writers.animations.abstracts",
                "core.animations",
                "view.animation"
            ]
            
            gaps, animation_classes = self._parse_coverage_data(csv_path, 0, animation_packages)
            
            if output_format == "json":
                self._output_json(animation_classes)
            elif output_format == "csv":
                self._output_csv(animation_classes)
            else:
                self._print_animation_analysis(animation_classes)
                
            return True
            
        except Exception as e:
            print(f"❌ Error analyzing animation coverage: {e}")
            return False
    
    def suggest_test_priorities(self, max_classes: int = 10) -> bool:
        """
        Suggest priority classes for test creation
        
        Args:
            max_classes: Maximum number of classes to suggest
            
        Returns:
            bool: True if analysis completed successfully
        """
        csv_path = self.project_root / "coverage-report" / "jacoco.csv"
        
        if not csv_path.exists():
            print("❌ Coverage report not found")
            return False
            
        try:
            # Focus on core and animation packages
            focus_packages = ["core", "animation", "xml.writers"]
            gaps, _ = self._parse_coverage_data(csv_path, 80, focus_packages)
            
            # Sort by impact (instruction count * inverse coverage)
            def priority_score(cls_info):
                coverage = max(cls_info['coverage'], 1)  # Avoid division by zero
                return cls_info['total_instructions'] * (100 - coverage) / coverage
                
            priority_classes = sorted(gaps, key=priority_score, reverse=True)[:max_classes]
            
            print("🎯 TOP TEST PRIORITIES")
            print("=" * 50)
            print(f"Showing top {len(priority_classes)} classes by impact (instructions × coverage gap)")
            print()
            
            for i, cls in enumerate(priority_classes, 1):
                impact = int(priority_score(cls))
                complexity = self._assess_complexity(cls)
                
                print(f"{i:2d}. {cls['class']}")
                print(f"    📦 {cls['package']}")
                print(f"    📊 {cls['coverage']:5.1f}% coverage ({cls['instructions_missed']:,} instructions missing)")
                print(f"    🎯 Impact Score: {impact:,}")
                print(f"    🔧 Complexity: {complexity}")
                print()
                
            return True
            
        except Exception as e:
            print(f"❌ Error suggesting test priorities: {e}")
            return False
    
    def _parse_coverage_data(self, csv_path: Path, threshold: int, focus_packages: List[str]) -> Tuple[List[Dict], List[Dict]]:
        """Parse JaCoCo CSV data and extract relevant classes"""
        gaps = []
        animation_classes = []
        
        with open(csv_path, 'r') as f:
            reader = csv.DictReader(f)
            
            for row in reader:
                package = row['PACKAGE']
                class_name = row['CLASS']
                inst_missed = int(row['INSTRUCTION_MISSED'])
                inst_covered = int(row['INSTRUCTION_COVERED'])
                line_missed = int(row['LINE_MISSED'])
                line_covered = int(row['LINE_COVERED'])
                
                total_instructions = inst_missed + inst_covered
                if total_instructions == 0:
                    continue
                    
                coverage_pct = (inst_covered / total_instructions) * 100
                
                class_info = {
                    'package': package,
                    'class': class_name,
                    'coverage': coverage_pct,
                    'instructions_missed': inst_missed,
                    'instructions_covered': inst_covered,
                    'total_instructions': total_instructions,
                    'lines_missed': line_missed,
                    'lines_covered': line_covered,
                    'total_lines': line_missed + line_covered
                }
                
                # Check if package matches focus criteria
                is_focus_package = any(focus_pkg in package.lower() for focus_pkg in focus_packages)
                
                if is_focus_package:
                    animation_classes.append(class_info)
                    
                    if coverage_pct < threshold:
                        gaps.append(class_info)
        
        return gaps, animation_classes
    
    def _assess_complexity(self, class_info: Dict) -> str:
        """Assess the complexity of testing a class based on its metrics"""
        instructions = class_info['total_instructions']
        
        if instructions < 50:
            return "Low (Simple class)"
        elif instructions < 200:
            return "Medium (Standard class)"
        elif instructions < 500:
            return "High (Complex class)"
        else:
            return "Very High (Large/complex class)"
    
    def _print_analysis(self, gaps: List[Dict], animation_classes: List[Dict], threshold: int, focus_packages: List[str]):
        """Print comprehensive coverage analysis"""
        print("🎯 COVERAGE GAP ANALYSIS")
        print("=" * 60)
        print(f"Threshold: {threshold}% | Focus: {', '.join(focus_packages)}")
        print()
        
        if animation_classes:
            # Animation architecture summary
            total_classes = len(animation_classes)
            good_coverage = len([c for c in animation_classes if c['coverage'] >= 80])
            poor_coverage = len([c for c in animation_classes if c['coverage'] < 50])
            
            print(f"📊 FOCUS PACKAGE SUMMARY:")
            print(f"   {total_classes} classes analyzed")
            print(f"   {good_coverage} classes with good coverage (≥80%)")
            print(f"   {poor_coverage} classes with poor coverage (<50%)")
            print()
            
            # Top performers and worst performers
            sorted_classes = sorted(animation_classes, key=lambda x: x['coverage'])
            
            print("🔥 CRITICAL GAPS (0% coverage):")
            zero_coverage = [c for c in sorted_classes if c['coverage'] == 0]
            if zero_coverage:
                for cls in zero_coverage[:10]:  # Top 10 most critical
                    print(f"   ❌ {cls['class']} ({cls['total_instructions']} instructions)")
            else:
                print("   ✅ No classes with 0% coverage!")
            print()
            
            print("⚠️  NEEDS IMPROVEMENT:")
            needs_work = [c for c in sorted_classes if 0 < c['coverage'] < threshold]
            if needs_work:
                for cls in needs_work[:10]:
                    print(f"   🟡 {cls['coverage']:5.1f}% - {cls['class']} ({cls['instructions_missed']} missing)")
            else:
                print("   ✅ All classes above threshold!")
            print()
            
            print("✅ TOP PERFORMERS:")
            top_performers = [c for c in sorted(animation_classes, key=lambda x: -x['coverage']) if c['coverage'] >= 80]
            for cls in top_performers[:5]:
                print(f"   🏆 {cls['coverage']:5.1f}% - {cls['class']}")
        
        print()
        self._print_recommendations(gaps)
    
    def _print_animation_analysis(self, animation_classes: List[Dict]):
        """Print focused animation architecture analysis"""
        print("🎯 ANIMATION ARCHITECTURE COVERAGE")
        print("=" * 60)
        
        # Group by package
        packages = {}
        for cls in animation_classes:
            pkg = cls['package']
            if pkg not in packages:
                packages[pkg] = []
            packages[pkg].append(cls)
        
        for package, classes in sorted(packages.items()):
            if not classes:
                continue
                
            avg_coverage = sum(c['coverage'] for c in classes) / len(classes)
            total_instructions = sum(c['total_instructions'] for c in classes)
            
            print(f"\n📦 {package}")
            print(f"   Classes: {len(classes)} | Avg Coverage: {avg_coverage:.1f}% | Instructions: {total_instructions:,}")
            print()
            
            # Sort classes by coverage
            sorted_classes = sorted(classes, key=lambda x: x['coverage'])
            
            for cls in sorted_classes:
                if cls['coverage'] == 0:
                    status = "🔥"
                elif cls['coverage'] < 50:
                    status = "⚠️"
                elif cls['coverage'] < 80:
                    status = "🟡"
                else:
                    status = "✅"
                    
                print(f"   {status} {cls['coverage']:5.1f}% - {cls['class']}")
                print(f"      {cls['instructions_covered']:,}/{cls['total_instructions']:,} instructions")
    
    def _print_recommendations(self, gaps: List[Dict]):
        """Print actionable recommendations"""
        print("💡 RECOMMENDATIONS:")
        print("-" * 30)
        
        if not gaps:
            print("🎉 Excellent coverage! All focus packages meet the threshold.")
            return
            
        # Group recommendations by complexity
        simple_gaps = [g for g in gaps if g['total_instructions'] < 100]
        complex_gaps = [g for g in gaps if g['total_instructions'] >= 100]
        
        print("🚀 Quick Wins (< 100 instructions):")
        for gap in sorted(simple_gaps, key=lambda x: x['total_instructions'])[:5]:
            print(f"   • {gap['class']} ({gap['total_instructions']} instructions)")
        
        print()
        print("🎯 High Impact (≥ 100 instructions):")
        for gap in sorted(complex_gaps, key=lambda x: -x['total_instructions'])[:5]:
            print(f"   • {gap['class']} ({gap['total_instructions']} instructions)")
        
        print()
        print("📋 Next Steps:")
        print("   1. Start with quick wins to build momentum")
        print("   2. Use HTML report for line-by-line analysis:")
        print("      coverage-report/html/index.html")
        print("   3. Focus on core animation factories first")
        print("   4. Run 'pc.py coverage priorities' for detailed guidance")
    
    def _output_json(self, classes: List[Dict]):
        """Output animation coverage data as JSON"""
        output = {
            "coverage_analysis": {
                "timestamp": str(Path("coverage-report/jacoco.csv").stat().st_mtime),
                "total_classes": len(classes),
                "average_coverage": sum(c['coverage'] for c in classes) / len(classes) if classes else 0,
                "classes": classes
            }
        }
        print(json.dumps(output, indent=2))
    
    def _output_csv(self, classes: List[Dict]):
        """Output animation coverage data as CSV"""
        if not classes:
            return
            
        # Print CSV header
        print("package,class,coverage_pct,instructions_missed,instructions_covered,total_instructions")
        
        # Print data rows
        for cls in sorted(classes, key=lambda x: x['coverage']):
            print(f"{cls['package']},{cls['class']},{cls['coverage']:.1f},"
                  f"{cls['instructions_missed']},{cls['instructions_covered']},{cls['total_instructions']}")