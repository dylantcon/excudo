"""
Base animation analyzer framework

Provides the foundation for animation-specific analyzers with standardized 
patterns for analyzing native PowerPoint timing dumps, validating generated 
animations, and creating comparison reports.
"""

import xml.etree.ElementTree as ET
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Dict, List, Tuple, Optional, Any
import json
import re


class BaseAnimationAnalyzer(ABC):
    """Base class for animation-specific analyzers"""
    
    def __init__(self, timing_dumps_dir: Optional[Path] = None):
        """
        Initialize analyzer with timing dumps directory
        
        Args:
            timing_dumps_dir: Path to directory containing PowerPoint timing dumps
        """
        self.timing_dumps_dir = timing_dumps_dir
        self._native_patterns = {}
        self._generated_patterns = {}
        
    @abstractmethod
    def get_animation_types(self) -> List[str]:
        """Return list of animation types this analyzer handles"""
        pass
        
    @abstractmethod
    def extract_patterns(self, xml_file: Path) -> Dict[str, Any]:
        """Extract animation-specific patterns from timing XML"""
        pass
        
    @abstractmethod
    def validate_pattern(self, pattern: Dict[str, Any]) -> List[str]:
        """Validate animation pattern against PowerPoint rules"""
        pass
        
    def analyze_native_dumps(self, pattern_filter: Optional[str] = None) -> Dict[str, Any]:
        """
        Analyze native PowerPoint timing dumps for this animation type
        
        Args:
            pattern_filter: Optional regex to filter which files to analyze
            
        Returns:
            Dictionary containing analysis results
        """
        if not self.timing_dumps_dir or not self.timing_dumps_dir.exists():
            return {"error": "No timing dumps directory available"}
            
        results = {
            "animation_type": self.__class__.__name__.replace("Analyzer", ""),
            "files_analyzed": [],
            "patterns": {},
            "variations": {},
            "errors": []
        }
        
        # Find relevant timing dump files
        animation_types = self.get_animation_types()
        matching_files = []
        
        for xml_file in self.timing_dumps_dir.glob("*.xml"):
            filename = xml_file.name
            
            # Check if file matches our animation types
            if any(anim_type.lower() in filename.lower() for anim_type in animation_types):
                if pattern_filter:
                    if re.search(pattern_filter, filename, re.IGNORECASE):
                        matching_files.append(xml_file)
                else:
                    matching_files.append(xml_file)
        
        # Analyze each matching file
        for xml_file in sorted(matching_files):
            try:
                pattern = self.extract_patterns(xml_file)
                if pattern:
                    results["files_analyzed"].append(xml_file.name)
                    results["patterns"][xml_file.name] = pattern
                    
                    # Group by variation type
                    variation = self._classify_variation(xml_file.name, pattern)
                    if variation not in results["variations"]:
                        results["variations"][variation] = []
                    results["variations"][variation].append({
                        "file": xml_file.name,
                        "pattern": pattern
                    })
                    
            except Exception as e:
                results["errors"].append(f"{xml_file.name}: {str(e)}")
        
        self._native_patterns = results
        return results
    
    def _classify_variation(self, filename: str, pattern: Dict[str, Any]) -> str:
        """Classify animation variation based on filename and pattern"""
        # Default implementation - subclasses should override
        if "entrance" in filename.lower() or "in" in filename.lower():
            return "entrance"
        elif "exit" in filename.lower() or "out" in filename.lower():
            return "exit"
        else:
            return "unknown"
    
    def compare_patterns(self, native_pattern: Dict[str, Any], 
                        generated_pattern: Dict[str, Any]) -> Dict[str, Any]:
        """
        Compare native PowerPoint pattern with generated pattern
        
        Args:
            native_pattern: Pattern extracted from PowerPoint timing dump
            generated_pattern: Pattern from our generated animation
            
        Returns:
            Comparison results with differences and compatibility score
        """
        comparison = {
            "compatibility_score": 0.0,
            "differences": [],
            "matches": [],
            "critical_issues": [],
            "warnings": []
        }
        
        # Compare structure
        native_keys = set(native_pattern.keys())
        generated_keys = set(generated_pattern.keys())
        
        missing_keys = native_keys - generated_keys
        extra_keys = generated_keys - native_keys
        
        if missing_keys:
            comparison["critical_issues"].append(f"Missing keys: {missing_keys}")
        if extra_keys:
            comparison["warnings"].append(f"Extra keys: {extra_keys}")
        
        # Compare common keys
        common_keys = native_keys & generated_keys
        matches = 0
        total_comparisons = len(common_keys)
        
        for key in common_keys:
            if native_pattern[key] == generated_pattern[key]:
                comparison["matches"].append(f"{key}: {native_pattern[key]}")
                matches += 1
            else:
                comparison["differences"].append({
                    "key": key,
                    "native": native_pattern[key],
                    "generated": generated_pattern[key]
                })
        
        # Calculate compatibility score
        if total_comparisons > 0:
            comparison["compatibility_score"] = matches / total_comparisons
        
        return comparison
    
    def generate_report(self, format: str = "text") -> str:
        """
        Generate analysis report
        
        Args:
            format: Output format ("text", "json", "markdown")
            
        Returns:
            Formatted report string
        """
        if format == "json":
            return json.dumps({
                "native_patterns": self._native_patterns,
                "generated_patterns": self._generated_patterns
            }, indent=2)
        elif format == "markdown":
            return self._generate_markdown_report()
        else:
            return self._generate_text_report()
    
    def _generate_text_report(self) -> str:
        """Generate plain text report"""
        lines = []
        lines.append(f"{self.__class__.__name__} Analysis Report")
        lines.append("=" * 50)
        
        if self._native_patterns:
            lines.append(f"\nNative Patterns Analyzed: {len(self._native_patterns.get('files_analyzed', []))}")
            for variation, patterns in self._native_patterns.get('variations', {}).items():
                lines.append(f"  {variation}: {len(patterns)} files")
        
        if self._native_patterns.get('errors'):
            lines.append(f"\nErrors: {len(self._native_patterns['errors'])}")
            for error in self._native_patterns['errors']:
                lines.append(f"  - {error}")
        
        return "\n".join(lines)
    
    def _generate_markdown_report(self) -> str:
        """Generate markdown report"""
        lines = []
        lines.append(f"# {self.__class__.__name__} Analysis Report")
        lines.append("")
        
        if self._native_patterns:
            lines.append("## Native Pattern Analysis")
            lines.append(f"- Files analyzed: {len(self._native_patterns.get('files_analyzed', []))}")
            
            for variation, patterns in self._native_patterns.get('variations', {}).items():
                lines.append(f"- {variation}: {len(patterns)} files")
        
        return "\n".join(lines)
    
    @staticmethod
    def parse_timing_xml(xml_file: Path) -> ET.Element:
        """Parse timing XML file and return root element"""
        tree = ET.parse(xml_file)
        return tree.getroot()
    
    @staticmethod
    def find_animation_elements(root: ET.Element, element_name: str) -> List[ET.Element]:
        """Find animation elements by name in timing XML"""
        ns = {'p': 'http://schemas.openxmlformats.org/presentationml/2006/main'}
        return root.findall(f'.//p:{element_name}', ns)
    
    @staticmethod
    def extract_coordinate_values(anim_element: ET.Element) -> List[Tuple[str, str]]:
        """Extract coordinate values from p:anim element"""
        ns = {'p': 'http://schemas.openxmlformats.org/presentationml/2006/main'}
        values = []
        
        tav_list = anim_element.find('.//p:tavLst', ns)
        if tav_list is not None:
            for tav in tav_list.findall('p:tav', ns):
                tm = tav.get('tm', '0')
                val_elem = tav.find('.//p:strVal', ns)
                if val_elem is not None:
                    val = val_elem.get('val', '')
                    values.append((tm, val))
        
        return values