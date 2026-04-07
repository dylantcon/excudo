"""
Environment management for Excudo CLI

Handles Python virtual environment, Java detection, and configuration management.
"""

import os
import sys
import json
import venv
import shutil
import platform
import subprocess
from pathlib import Path
from typing import Optional, Dict, Any


class PCEnvironment:
    """Manages Python virtual environment and Java classpath"""
    
    def __init__(self, project_root: Path):
        self.project_root = project_root
        self.venv_dir = project_root / ".pc-venv"
        self.config_file = project_root / "pc-config.json"
        self.java_home = self._detect_java()
        self.config = self._load_config()
        
    def _detect_java(self) -> Optional[str]:
        """Detect Java installation"""
        java_home = os.environ.get('JAVA_HOME')
        if java_home and Path(java_home).exists():
            return java_home
            
        # Try common locations
        common_paths = [
            "/usr/lib/jvm/default-java",
            "/usr/lib/jvm/java-11-openjdk",
            "/usr/lib/jvm/java-17-openjdk",
            "/usr/lib/jvm/java-21-openjdk",
        ]
        
        for path in common_paths:
            if Path(path).exists():
                return path
                
        # Check if java is in PATH
        try:
            result = subprocess.run(['which', 'java'], capture_output=True, text=True)
            if result.returncode == 0:
                java_path = Path(result.stdout.strip())
                # Try to find JAVA_HOME from java binary
                possible_home = java_path.parent.parent
                if (possible_home / "lib").exists():
                    return str(possible_home)
        except:
            pass
            
        return None
        
    def _load_config(self) -> Dict[str, Any]:
        """Load or create configuration"""
        default_config = {
            "java_home": self.java_home,
            "log_level": "INFO",
            "log_console": True,
            "build_parallel": True,
            "test_parallel": True,
            "javafx_headless": True,
            "python_requirements": [],
            "classpath_extra": []
        }
        
        if self.config_file.exists():
            try:
                with open(self.config_file, 'r') as f:
                    config = json.load(f)
                # Merge with defaults
                for key, value in default_config.items():
                    if key not in config:
                        config[key] = value
                return config
            except Exception as e:
                print(f"Warning: Could not load config file: {e}")
                
        return default_config
        
    def save_config(self):
        """Save current configuration"""
        with open(self.config_file, 'w') as f:
            json.dump(self.config, f, indent=2)
            
    def setup_venv(self, force: bool = False) -> bool:
        """Setup Python virtual environment if needed"""
        if self.venv_dir.exists() and not force:
            return True
            
        if force and self.venv_dir.exists():
            shutil.rmtree(self.venv_dir)
            
        print(f"Creating Python virtual environment at {self.venv_dir}")
        try:
            venv.create(self.venv_dir, with_pip=True)
            
            # Install any configured requirements
            if self.config.get("python_requirements"):
                pip_path = self._get_venv_executable("pip")
                for req in self.config["python_requirements"]:
                    subprocess.run([pip_path, "install", req], check=True)
                    
            return True
        except Exception as e:
            print(f"Error creating virtual environment: {e}")
            return False
            
    def _get_venv_executable(self, name: str) -> str:
        """Get path to executable in virtual environment"""
        if platform.system() == "Windows":
            return str(self.venv_dir / "Scripts" / f"{name}.exe")
        else:
            return str(self.venv_dir / "bin" / name)
            
    def get_python_executable(self) -> str:
        """Get Python executable (venv if available, system otherwise)"""
        if self.venv_dir.exists():
            return self._get_venv_executable("python")
        return sys.executable
        
    def get_java_executable(self) -> str:
        """Get Java executable"""
        if self.java_home:
            java_exe = Path(self.java_home) / "bin" / "java"
            if java_exe.exists():
                return str(java_exe)
        return "java"  # Hope it's in PATH
        
    def get_javac_executable(self) -> str:
        """Get javac executable"""
        if self.java_home:
            javac_exe = Path(self.java_home) / "bin" / "javac"
            if javac_exe.exists():
                return str(javac_exe)
        return "javac"  # Hope it's in PATH
        
    def get_classpath(self) -> str:
        """Build complete Java classpath"""
        classpath_parts = ["build"]
        
        # Explicitly add each JAR file in lib directory
        lib_dir = self.project_root / "lib"
        if lib_dir.exists():
            # List all files in lib directory for debugging
            all_files = list(lib_dir.iterdir())
            jar_files = [f for f in all_files if f.suffix.lower() == '.jar' and f.is_file()]
            
            if self.config.get("verbose", False):
                print(f"Lib directory contains {len(all_files)} files")
                print(f"Found {len(jar_files)} JAR files in lib/")
                for jar in jar_files:
                    print(f"  - {jar.name}")
            
            # Add jsoup explicitly first if it exists
            jsoup_jar = lib_dir / "jsoup-1.17.1.jar"
            if jsoup_jar.exists():
                classpath_parts.append(str(jsoup_jar))
                if self.config.get("verbose", False):
                    print(f"Added jsoup JAR: {jsoup_jar}")
            
            # Add all other JARs
            for jar_file in jar_files:
                if jar_file != jsoup_jar:  # Avoid duplicates
                    classpath_parts.append(str(jar_file))
        
        # Add JavaFX JARs to classpath if they exist
        javafx_lib_path = self.project_root / "lib" / "javafx-sdk-21" / "lib"
        if javafx_lib_path.exists():
            # Add specific JavaFX JARs for platforms that don't support module-path
            for jar_file in javafx_lib_path.glob("*.jar"):
                classpath_parts.append(str(jar_file))
        
        # Add extra classpath entries from config
        extra_paths = self.config.get("classpath_extra", [])
        for extra_path in extra_paths:
            classpath_parts.append(str(extra_path))
        
        return os.pathsep.join(classpath_parts)