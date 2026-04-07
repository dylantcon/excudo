"""
Animation Analysis for Excudo

Analyzes timing XML files to understand animation element usage patterns.
"""

import json
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, Any, List, Optional
import glob as glob_module
import csv
import io
import re


class AnimationAnalyzer:
    """Analyzes timing XML files to understand animation element usage patterns"""
    
    def __init__(self, env):
        self.env = env
        self.project_root = env.project_root
        
    def analyze_timing_files(self, timing_dir: str, output_format: str = "text", 
                           verbose: bool = False, output_file: str = None, 
                           file_pattern: str = "*.xml") -> bool:
        """Analyze timing XML files for animation element patterns"""
        try:
            timing_path = Path(timing_dir)
            if not timing_path.exists():
                print(f"Error: Directory '{timing_dir}' does not exist")
                return False
                
            # Find all XML files matching pattern
            xml_files = list(timing_path.glob(file_pattern))
            if not xml_files:
                print(f"Error: No XML files found matching pattern '{file_pattern}' in '{timing_dir}'")
                return False
                
            print(f"Analyzing {len(xml_files)} timing XML files...")
            
            # All possible child elements of p:childTnLst (from MS documentation)
            anim_elements = {
                # Animation behavior elements
                'p:anim': 'Coordinate animation (X/Y movement)',
                'p:animClr': 'Color animation',
                'p:animEffect': 'Filter effects (fade, wipe, etc.)',
                'p:animMotion': 'Motion path animation',
                'p:animRot': 'Rotation animation',
                'p:animScale': 'Scale animation',
                'p:set': 'Property set (visibility, etc.)',
                
                # Media elements
                'p:audio': 'Audio elements',
                'p:video': 'Video elements',
                
                # Command and control elements
                'p:cmd': 'Command elements',
                'p:excl': 'Exclusive elements',
                
                # Container elements (timing hierarchy)
                'p:par': 'Parallel time containers',
                'p:seq': 'Sequential time containers'
            }
            
            # Analysis results
            results = {
                'total_files': len(xml_files),
                'element_counts': {elem: 0 for elem in anim_elements.keys()},
                'files_by_element': {elem: [] for elem in anim_elements.keys()},
                'animation_patterns': {},
                'preset_analysis': {},
                'files_analyzed': []
            }
            
            # Analyze each file
            for xml_file in xml_files:
                try:
                    file_info = self._analyze_single_timing_file(xml_file, anim_elements, verbose)
                    results['files_analyzed'].append(file_info)
                    
                    # Update counts
                    for elem_type, count in file_info['elements'].items():
                        if count > 0:
                            results['element_counts'][elem_type] += 1
                            results['files_by_element'][elem_type].append(xml_file.name)
                            
                    # Track animation patterns
                    pattern_key = self._get_pattern_key(file_info['elements'])
                    if pattern_key not in results['animation_patterns']:
                        results['animation_patterns'][pattern_key] = {
                            'count': 0,
                            'examples': []
                        }
                    results['animation_patterns'][pattern_key]['count'] += 1
                    results['animation_patterns'][pattern_key]['examples'].append(xml_file.name)
                    
                    # Track preset ID/subtype combinations
                    preset_key = f"{file_info.get('presetID', 'N/A')}-{file_info.get('presetSubtype', 'N/A')}"
                    if preset_key not in results['preset_analysis']:
                        results['preset_analysis'][preset_key] = {
                            'count': 0,
                            'files': [],
                            'pattern': pattern_key
                        }
                    results['preset_analysis'][preset_key]['count'] += 1
                    results['preset_analysis'][preset_key]['files'].append(xml_file.name)
                    
                except Exception as e:
                    if verbose:
                        print(f"Warning: Failed to analyze {xml_file.name}: {e}")
                    
            # Output results
            self._output_animation_results(results, anim_elements, output_format, output_file)
            
            return True
            
        except Exception as e:
            print(f"Animation analysis failed: {e}")
            if verbose:
                import traceback
                traceback.print_exc()
            return False
            
    def _analyze_single_timing_file(self, xml_file: Path, anim_elements: dict, verbose: bool) -> dict:
        """Analyze a single timing XML file"""
        file_info = {
            'filename': xml_file.name,
            'elements': {elem: 0 for elem in anim_elements.keys()},
            'presetID': None,
            'presetSubtype': None,
            'title': self._extract_title_from_filename(xml_file.name)
        }
        
        try:
            # Read the file content and extract XML portion (skip header)
            with open(xml_file, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Find the start of XML content (skip the header line)
            xml_start = content.find('<p:timing')
            if xml_start == -1:
                if verbose:
                    print(f"Warning: No XML content found in {xml_file.name}")
                return file_info
                
            xml_content = content[xml_start:]
            
            # Parse the XML content
            root = ET.fromstring(xml_content)
            
            # Define namespace for PowerPoint elements
            namespaces = {'p': 'http://schemas.openxmlformats.org/presentationml/2006/main'}
            
            # Count each animation element type with proper namespace handling
            for elem_type in anim_elements.keys():
                # Use the full namespaced element name
                elements = root.findall(f".//{elem_type}", namespaces)
                file_info['elements'][elem_type] = len(elements)
                
            # Extract preset information from cTn elements
            ctn_elements = root.findall(".//p:cTn[@presetID]", namespaces)
            if ctn_elements:
                # Use the first preset ID found (typically the main animation)
                file_info['presetID'] = ctn_elements[0].get('presetID')
                file_info['presetSubtype'] = ctn_elements[0].get('presetSubtype', '0')
                
        except Exception as e:
            if verbose:
                print(f"Warning: XML parsing error in {xml_file.name}: {e}")
                
        return file_info
        
    def _extract_title_from_filename(self, filename: str) -> str:
        """Extract animation title from filename"""
        # Remove slide number prefix and .xml suffix
        # e.g., "slide_007_Fly_In_From_Bottom.xml" -> "Fly In From Bottom"
        match = re.match(r'slide_\d+_(.+)\.xml', filename)
        if match:
            title = match.group(1).replace('_', ' ')
            return title
        return filename
        
    def _get_pattern_key(self, elements: dict) -> str:
        """Generate a pattern key from element usage"""
        pattern_parts = []
        for elem_type, count in elements.items():
            if count > 0:
                if count == 1:
                    pattern_parts.append(elem_type.split(':')[1])
                else:
                    pattern_parts.append(f"{elem_type.split(':')[1]}×{count}")
        
        return "+".join(sorted(pattern_parts)) if pattern_parts else "empty"
        
    def _output_animation_results(self, results: dict, anim_elements: dict, output_format: str, output_file: Optional[str]):
        """Output animation analysis results"""
        if output_format == "json":
            output_data = json.dumps(results, indent=2, default=str)
        elif output_format == "csv":
            output_data = self._generate_csv_output(results)
        else:  # text format
            output_data = self._generate_text_output(results, anim_elements)
            
        if output_file:
            Path(output_file).write_text(output_data, encoding='utf-8')
            print(f"Results written to {output_file}")
        else:
            print(output_data)
            
    def _generate_text_output(self, results: dict, anim_elements: dict) -> str:
        """Generate human-readable text output"""
        output = []
        output.append("ANIMATION ELEMENT ANALYSIS RESULTS")
        output.append("=" * 50)
        output.append(f"Total files analyzed: {results['total_files']}")
        output.append("")
        
        # Element usage summary
        output.append("ELEMENT USAGE SUMMARY")
        output.append("-" * 30)
        for elem_type, description in anim_elements.items():
            count = results['element_counts'][elem_type]
            if count > 0:
                files = results['files_by_element'][elem_type]
                output.append(f"{elem_type:15} : {count:3d} files - {description}")
                if count <= 5:  # Show files if small number
                    output.append(f"                  Files: {', '.join(files)}")
        output.append("")
        
        # Animation patterns
        output.append("ANIMATION PATTERNS")
        output.append("-" * 25)
        sorted_patterns = sorted(results['animation_patterns'].items(), 
                               key=lambda x: x[1]['count'], reverse=True)
        for pattern, info in sorted_patterns[:10]:  # Top 10 patterns
            output.append(f"{pattern:30} : {info['count']:3d} files")
            if info['count'] <= 3:  # Show examples for rare patterns
                output.append(f"{'':32}   Examples: {', '.join(info['examples'][:3])}")
        output.append("")
        
        # Preset analysis
        output.append("PRESET ID/SUBTYPE ANALYSIS")
        output.append("-" * 35)
        sorted_presets = sorted(results['preset_analysis'].items(),
                              key=lambda x: x[1]['count'], reverse=True)
        for preset, info in sorted_presets[:15]:  # Top 15 presets
            output.append(f"{preset:20} : {info['count']:3d} files ({info['pattern']})")
            
        return "\n".join(output)
        
    def _generate_csv_output(self, results: dict) -> str:
        """Generate CSV output"""
        output = io.StringIO()
        writer = csv.writer(output)
        
        # Header
        writer.writerow([
            'filename', 'title', 'presetID', 'presetSubtype',
            'p:anim', 'p:animClr', 'p:animEffect', 'p:animMotion', 
            'p:animRot', 'p:animScale', 'p:set',
            'p:audio', 'p:video', 'p:cmd', 'p:excl', 'p:par', 'p:seq'
        ])
        
        # Data rows
        for file_info in results['files_analyzed']:
            row = [
                file_info['filename'],
                file_info['title'],
                file_info.get('presetID', ''),
                file_info.get('presetSubtype', ''),
                file_info['elements']['p:anim'],
                file_info['elements']['p:animClr'],
                file_info['elements']['p:animEffect'],
                file_info['elements']['p:animMotion'],
                file_info['elements']['p:animRot'],
                file_info['elements']['p:animScale'],
                file_info['elements']['p:set'],
                file_info['elements']['p:audio'],
                file_info['elements']['p:video'],
                file_info['elements']['p:cmd'],
                file_info['elements']['p:excl'],
                file_info['elements']['p:par'],
                file_info['elements']['p:seq']
            ]
            writer.writerow(row)
            
        return output.getvalue()