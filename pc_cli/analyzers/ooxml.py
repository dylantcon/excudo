"""
OOXML Validation for Excudo

Validates OOXML files using the OOXML-Validator CLI tool.
"""

import os
import json
import subprocess
from pathlib import Path
from typing import List, Dict, Any


class OOXMLValidator:
    """Validates OOXML files using the OOXML-Validator CLI tool"""
    
    def __init__(self, env):
        self.env = env
        self.project_root = env.project_root
        self.validator_path = self.project_root / "tools" / "OOXML-Validator" / "OOXMLValidatorCLI"
        
    def validate(self, file_or_dir: str, office_version: str = "Microsoft365", 
                output_format: str = "json", recursive: bool = False, 
                include_valid: bool = False, fail_on_error: bool = False) -> bool:
        """Validate OOXML files and return results"""
        try:
            if not self.validator_path.exists():
                print(f"ERROR: OOXML Validator not found at {self.validator_path}")
                print("Please ensure the validator is properly extracted in tools/OOXML-Validator/")
                return False
                
            # Make validator executable
            self.validator_path.chmod(0o755)
            
            # Build command
            cmd = [str(self.validator_path), os.path.abspath(file_or_dir)]
            
            # Add office version
            if office_version != "Microsoft365":
                cmd.append(office_version)
                
            # Add format flag  
            if output_format == "xml":
                cmd.append("--xml")
                
            # Add recursive flag
            if recursive:
                cmd.append("--recursive")
                
            # Add include valid flag
            if include_valid:
                cmd.append("--all")
                
            print(f"Validating {file_or_dir} against {office_version}...")
            
            # Execute validator
            result = subprocess.run(cmd, capture_output=True, text=True, cwd=self.project_root)
            
            if result.returncode == 0:
                # Parse and display results
                return self._display_validation_results(result.stdout, output_format, fail_on_error)
            else:
                print(f"Validator failed with exit code {result.returncode}")
                if result.stderr:
                    print(f"Error: {result.stderr}")
                return False
                
        except Exception as e:
            print(f"Validation failed: {e}")
            return False
            
    def _display_validation_results(self, output: str, output_format: str, fail_on_error: bool) -> bool:
        """Parse and display validation results"""
        try:
            if output_format == "json":
                # Parse JSON output
                if output.strip():
                    errors = json.loads(output)
                    return self._display_json_results(errors, fail_on_error)
                else:
                    print("✓ No validation errors found")
                    return True
            else:
                # Display XML output directly
                if output.strip():
                    print("Validation Results:")
                    print(output)
                    return not fail_on_error  # Return success unless fail_on_error is True
                else:
                    print("✓ No validation errors found")
                    return True
                    
        except json.JSONDecodeError as e:
            print(f"Error parsing validation results: {e}")
            print(f"Raw output: {output}")
            return False
            
    def _display_json_results(self, errors: List[Dict], fail_on_error: bool) -> bool:
        """Display JSON validation results in a user-friendly format"""
        if not errors:
            print("✓ No validation errors found")
            return True
            
        print(f"✗ Found {len(errors)} validation error(s):")
        print()
        
        # Group errors by file path
        errors_by_file = {}
        for error in errors:
            file_path = error.get("Path", {}).get("PartUri", "unknown")
            if file_path not in errors_by_file:
                errors_by_file[file_path] = []
            errors_by_file[file_path].append(error)
            
        # Display errors grouped by file
        for file_path, file_errors in errors_by_file.items():
            print(f"📄 {file_path}")
            for i, error in enumerate(file_errors, 1):
                print(f"  {i}. {error.get('ErrorType', 'Unknown')}: {error.get('Description', 'No description')}")
                xpath = error.get("Path", {}).get("XPath")
                if xpath:
                    print(f"     Location: {xpath}")
                print()
                
        # Summary
        error_types = {}
        for error in errors:
            error_type = error.get("ErrorType", "Unknown")
            error_types[error_type] = error_types.get(error_type, 0) + 1
            
        print("Error Summary:")
        for error_type, count in error_types.items():
            print(f"  {error_type}: {count}")
            
        return not fail_on_error  # Return success unless fail_on_error is True