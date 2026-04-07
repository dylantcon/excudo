"""
FLY Animation Analyzer

Specialized analyzer for FLY animations that can:
- Analyze all 16 FLY animation variations (8 directions × 2 types)
- Extract coordinate expressions from PowerPoint timing dumps
- Validate expressions against PowerPoint syntax rules
- Compare our generated FLY animations with native PowerPoint patterns
- Generate detailed reports with specific coordinate analysis
"""

import re
from pathlib import Path
from typing import Dict, List, Any, Optional
from .base_analyzer import BaseAnimationAnalyzer


class FlyAnimationAnalyzer(BaseAnimationAnalyzer):
    """Analyzer for FLY animations with coordinate expression analysis"""
    
    # All FLY animation variations
    FLY_DIRECTIONS = [
        "left", "right", "top", "bottom",
        "top_left", "top_right", "bottom_left", "bottom_right"
    ]
    
    FLY_TYPES = ["entrance", "exit"]  # FLY IN vs FLY OUT
    
    def get_animation_types(self) -> List[str]:
        """Return list of FLY animation types"""
        return ["Fly_In", "Fly_Out"]
    
    def extract_patterns(self, xml_file: Path) -> Dict[str, Any]:
        """
        Extract FLY-specific patterns from timing XML
        
        Returns:
            Dict containing coordinate expressions, directions, and metadata
        """
        try:
            root = self.parse_timing_xml(xml_file)
            
            pattern = {
                "filename": xml_file.name,
                "direction": self._extract_direction(xml_file.name),
                "animation_type": self._extract_animation_type(xml_file.name),
                "coordinates": {},
                "additive_mode": False,
                "expressions": {},
                "validation_errors": []
            }
            
            # Find coordinate animations (p:anim elements)
            anim_elements = self.find_animation_elements(root, "anim")
            
            for anim_elem in anim_elements:
                # Check if this is a coordinate animation
                attr_name = self._get_attribute_name(anim_elem)
                if attr_name in ["ppt_x", "ppt_y"]:
                    # Extract coordinate values
                    values = self.extract_coordinate_values(anim_elem)
                    if values:
                        pattern["coordinates"][attr_name] = values
                        
                        # Check for additive mode
                        if self._has_additive_mode(anim_elem):
                            pattern["additive_mode"] = True
                        
                        # Analyze expressions
                        expressions = self._analyze_expressions(values)
                        pattern["expressions"][attr_name] = expressions
            
            # Validate the pattern
            validation_errors = self.validate_pattern(pattern)
            pattern["validation_errors"] = validation_errors
            
            return pattern
            
        except Exception as e:
            return {"error": f"Failed to extract pattern: {str(e)}"}
    
    def _extract_direction(self, filename: str) -> str:
        """Extract FLY direction from filename"""
        filename_lower = filename.lower()
        
        # Check for compound directions first
        if "top_left" in filename_lower or "topleft" in filename_lower:
            return "top_left"
        elif "top_right" in filename_lower or "topright" in filename_lower:
            return "top_right"
        elif "bottom_left" in filename_lower or "bottomleft" in filename_lower:
            return "bottom_left"
        elif "bottom_right" in filename_lower or "bottomright" in filename_lower:
            return "bottom_right"
        # Check for simple directions
        elif "left" in filename_lower:
            return "left"
        elif "right" in filename_lower:
            return "right"
        elif "top" in filename_lower:
            return "top"
        elif "bottom" in filename_lower:
            return "bottom"
        else:
            return "unknown"
    
    def _extract_animation_type(self, filename: str) -> str:
        """Extract animation type (entrance/exit) from filename"""
        filename_lower = filename.lower()
        if "fly_in" in filename_lower or "from" in filename_lower:
            return "entrance"
        elif "fly_out" in filename_lower or "to" in filename_lower:
            return "exit"
        else:
            return "unknown"
    
    def _get_attribute_name(self, anim_elem) -> str:
        """Get attribute name from p:anim element"""
        ns = {'p': 'http://schemas.openxmlformats.org/presentationml/2006/main'}
        attr_name_elem = anim_elem.find('.//p:attrName', ns)
        if attr_name_elem is not None:
            return attr_name_elem.text or ""
        return ""
    
    def _has_additive_mode(self, anim_elem) -> bool:
        """Check if animation has additive='base' mode"""
        ns = {'p': 'http://schemas.openxmlformats.org/presentationml/2006/main'}
        cbhvr_elem = anim_elem.find('.//p:cBhvr', ns)
        if cbhvr_elem is not None:
            return cbhvr_elem.get('additive') == 'base'
        return False
    
    def _analyze_expressions(self, values: List[tuple]) -> Dict[str, Any]:
        """Analyze coordinate expressions for patterns"""
        analysis = {
            "start_expression": None,
            "end_expression": None,
            "expression_type": "unknown",
            "uses_hash_syntax": False,
            "mathematical_operations": []
        }
        
        if len(values) >= 2:
            start_val = values[0][1]  # tm="0" value
            end_val = values[1][1]    # tm="100000" value
            
            analysis["start_expression"] = start_val
            analysis["end_expression"] = end_val
            
            # Check for # syntax (PowerPoint variables)
            if "#" in start_val or "#" in end_val:
                analysis["uses_hash_syntax"] = True
            
            # Analyze mathematical operations
            for expr in [start_val, end_val]:
                if "+" in expr:
                    analysis["mathematical_operations"].append("addition")
                if "-" in expr:
                    analysis["mathematical_operations"].append("subtraction")
                if "*" in expr:
                    analysis["mathematical_operations"].append("multiplication")
                if "/" in expr:
                    analysis["mathematical_operations"].append("division")
            
            # Classify expression type
            analysis["expression_type"] = self._classify_expression_type(start_val, end_val)
        
        return analysis
    
    def _classify_expression_type(self, start_expr: str, end_expr: str) -> str:
        """Classify the type of coordinate expression"""
        # PowerPoint coordinate expression patterns
        if start_expr == "#ppt_x" or end_expr == "#ppt_x":
            return "normal_position"
        elif start_expr == "ppt_x" or end_expr == "ppt_x":
            return "normal_position_no_hash"
        elif "0-#ppt_w/2" in start_expr or "0-#ppt_w/2" in end_expr:
            return "left_edge_entrance"
        elif "1+#ppt_w/2" in start_expr or "1+#ppt_w/2" in end_expr:
            return "right_edge_entrance"
        elif "0-#ppt_h/2" in start_expr or "0-#ppt_h/2" in end_expr:
            return "top_edge_entrance"
        elif "#ppt_h+#ppt_h/2" in start_expr or "#ppt_h+#ppt_h/2" in end_expr:
            return "bottom_edge_entrance"
        else:
            return "custom_expression"
    
    def validate_pattern(self, pattern: Dict[str, Any]) -> List[str]:
        """Validate FLY animation pattern against PowerPoint rules"""
        errors = []
        
        # Check for required coordinates
        if "ppt_x" not in pattern.get("coordinates", {}):
            errors.append("Missing ppt_x coordinate animation")
        if "ppt_y" not in pattern.get("coordinates", {}):
            errors.append("Missing ppt_y coordinate animation")
        
        # Validate expressions
        for coord_type, coord_data in pattern.get("coordinates", {}).items():
            if len(coord_data) < 2:
                errors.append(f"{coord_type}: Missing start/end values")
                continue
            
            start_val = coord_data[0][1]
            end_val = coord_data[1][1]
            
            # Check for invalid expressions like "-1-#ppt_w/2"
            if self._is_invalid_expression(start_val):
                errors.append(f"{coord_type} start: Invalid expression '{start_val}'")
            if self._is_invalid_expression(end_val):
                errors.append(f"{coord_type} end: Invalid expression '{end_val}'")
        
        # Validate additive mode for FLY animations
        if not pattern.get("additive_mode", False):
            errors.append("FLY animations should use additive='base' mode")
        
        # Check direction consistency
        direction = pattern.get("direction", "unknown")
        if direction == "unknown":
            errors.append("Could not determine FLY direction from filename")
        
        return errors
    
    def _is_invalid_expression(self, expression: str) -> bool:
        """Check if expression is invalid PowerPoint syntax"""
        # Known invalid patterns
        invalid_patterns = [
            r"-1-#ppt_w/2",    # Invalid: negative one minus expression
            r"-1-#ppt_h/2",    # Invalid: negative one minus expression
            r"-1-ppt_w/2",     # Invalid: missing hash
            r"-1-ppt_h/2",     # Invalid: missing hash
        ]
        
        for pattern in invalid_patterns:
            if re.search(pattern, expression):
                return True
        
        return False
    
    def _classify_variation(self, filename: str, pattern: Dict[str, Any]) -> str:
        """Classify FLY animation variation"""
        direction = pattern.get("direction", "unknown")
        anim_type = pattern.get("animation_type", "unknown")
        return f"{anim_type}_{direction}"
    
    def analyze_all_variations(self) -> Dict[str, Any]:
        """
        Analyze all FLY animation variations and create comprehensive report
        """
        analysis = self.analyze_native_dumps()
        
        # Additional FLY-specific analysis
        if "variations" in analysis:
            # Group by entrance/exit
            entrance_variations = {}
            exit_variations = {}
            
            for variation_key, patterns in analysis["variations"].items():
                if "entrance" in variation_key:
                    entrance_variations[variation_key] = patterns
                elif "exit" in variation_key:
                    exit_variations[variation_key] = patterns
            
            analysis["entrance_patterns"] = entrance_variations
            analysis["exit_patterns"] = exit_variations
            
            # Analyze coordinate expression patterns
            analysis["coordinate_analysis"] = self._analyze_coordinate_patterns(analysis["patterns"])
        
        return analysis
    
    def _analyze_coordinate_patterns(self, patterns: Dict[str, Any]) -> Dict[str, Any]:
        """Analyze coordinate expression patterns across all FLY variations"""
        coord_analysis = {
            "expression_types": {},
            "additive_usage": {"count": 0, "total": 0},
            "hash_syntax_usage": {"count": 0, "total": 0},
            "common_expressions": {},
            "invalid_expressions": []
        }
        
        for filename, pattern in patterns.items():
            coord_analysis["additive_usage"]["total"] += 1
            coord_analysis["hash_syntax_usage"]["total"] += 1
            
            if pattern.get("additive_mode", False):
                coord_analysis["additive_usage"]["count"] += 1
            
            # Analyze expressions
            for coord_type, expressions in pattern.get("expressions", {}).items():
                expr_type = expressions.get("expression_type", "unknown")
                if expr_type not in coord_analysis["expression_types"]:
                    coord_analysis["expression_types"][expr_type] = 0
                coord_analysis["expression_types"][expr_type] += 1
                
                if expressions.get("uses_hash_syntax", False):
                    coord_analysis["hash_syntax_usage"]["count"] += 1
                
                # Track common expressions
                start_expr = expressions.get("start_expression", "")
                end_expr = expressions.get("end_expression", "")
                
                for expr in [start_expr, end_expr]:
                    if expr:
                        if expr not in coord_analysis["common_expressions"]:
                            coord_analysis["common_expressions"][expr] = 0
                        coord_analysis["common_expressions"][expr] += 1
            
            # Check for validation errors
            if pattern.get("validation_errors"):
                coord_analysis["invalid_expressions"].extend([
                    f"{filename}: {error}" for error in pattern["validation_errors"]
                ])
        
        return coord_analysis
    
    def generate_fly_report(self, format: str = "text") -> str:
        """Generate FLY-specific analysis report"""
        analysis = self.analyze_all_variations()
        
        if format == "json":
            return super().generate_report("json")
        elif format == "markdown":
            return self._generate_fly_markdown_report(analysis)
        else:
            return self._generate_fly_text_report(analysis)
    
    def _generate_fly_text_report(self, analysis: Dict[str, Any]) -> str:
        """Generate detailed FLY analysis text report"""
        lines = []
        lines.append("FLY ANIMATION ANALYSIS REPORT")
        lines.append("=" * 50)
        
        lines.append(f"Files analyzed: {len(analysis.get('files_analyzed', []))}")
        lines.append(f"Variations found: {len(analysis.get('variations', {}))}")
        
        # Entrance vs Exit breakdown
        entrance_count = len(analysis.get("entrance_patterns", {}))
        exit_count = len(analysis.get("exit_patterns", {}))
        lines.append(f"Entrance animations: {entrance_count}")
        lines.append(f"Exit animations: {exit_count}")
        
        # Coordinate analysis
        coord_analysis = analysis.get("coordinate_analysis", {})
        if coord_analysis:
            lines.append("\nCOORDINATE EXPRESSION ANALYSIS")
            lines.append("-" * 30)
            
            # Additive usage
            additive = coord_analysis.get("additive_usage", {})
            if additive.get("total", 0) > 0:
                percentage = (additive.get("count", 0) / additive["total"]) * 100
                lines.append(f"Additive mode usage: {additive['count']}/{additive['total']} ({percentage:.1f}%)")
            
            # Hash syntax usage
            hash_usage = coord_analysis.get("hash_syntax_usage", {})
            if hash_usage.get("total", 0) > 0:
                percentage = (hash_usage.get("count", 0) / hash_usage["total"]) * 100
                lines.append(f"Hash syntax usage: {hash_usage['count']}/{hash_usage['total']} ({percentage:.1f}%)")
            
            # Expression types
            expr_types = coord_analysis.get("expression_types", {})
            if expr_types:
                lines.append("\nExpression types:")
                for expr_type, count in sorted(expr_types.items()):
                    lines.append(f"  {expr_type}: {count}")
        
        # Validation errors
        errors = analysis.get("errors", [])
        if errors:
            lines.append(f"\nValidation errors: {len(errors)}")
            for error in errors[:5]:  # Show first 5 errors
                lines.append(f"  - {error}")
            if len(errors) > 5:
                lines.append(f"  ... and {len(errors) - 5} more")
        
        return "\n".join(lines)
    
    def _generate_fly_markdown_report(self, analysis: Dict[str, Any]) -> str:
        """Generate detailed FLY analysis markdown report"""
        lines = []
        lines.append("# FLY Animation Analysis Report")
        lines.append("")
        
        lines.append("## Summary")
        lines.append(f"- **Files analyzed**: {len(analysis.get('files_analyzed', []))}")
        lines.append(f"- **Variations found**: {len(analysis.get('variations', {}))}")
        lines.append(f"- **Entrance animations**: {len(analysis.get('entrance_patterns', {}))}")
        lines.append(f"- **Exit animations**: {len(analysis.get('exit_patterns', {}))}")
        lines.append("")
        
        # Coordinate analysis
        coord_analysis = analysis.get("coordinate_analysis", {})
        if coord_analysis:
            lines.append("## Coordinate Expression Analysis")
            lines.append("")
            
            # Expression types table
            expr_types = coord_analysis.get("expression_types", {})
            if expr_types:
                lines.append("### Expression Types")
                lines.append("| Expression Type | Count |")
                lines.append("|---|---|")
                for expr_type, count in sorted(expr_types.items()):
                    lines.append(f"| {expr_type} | {count} |")
                lines.append("")
        
        return "\n".join(lines)