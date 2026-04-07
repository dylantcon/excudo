"""
Log Management and Analysis for Excudo

Handles log management, filtering, and analysis capabilities.
"""

import time
from pathlib import Path
from typing import List, Optional


class PCLogs:
    """Handles log management and filtering"""
    
    def __init__(self, env):
        self.env = env
        self.logs_dir = env.project_root / "logs"
        
    def list_components(self) -> List[str]:
        """List available log components"""
        components = []
        debug_dir = self.logs_dir / "debug"
        if debug_dir.exists():
            for log_file in debug_dir.glob("*.log"):
                components.append(log_file.stem)
        return sorted(components)
        
    def view_logs(self, component: Optional[str] = None, tail: bool = False, 
                  lines: int = 100) -> bool:
        """View logs for specified component or all logs"""
        if not self.logs_dir.exists():
            print("No logs directory found. Run the application first.")
            return False
            
        if component:
            log_file = self.logs_dir / "debug" / f"{component}.log"
            if not log_file.exists():
                print(f"No logs found for component: {component}")
                print(f"Available components: {', '.join(self.list_components())}")
                return False
            files_to_show = [log_file]
        else:
            # Show main log
            main_log = self.logs_dir / "main.log"
            files_to_show = [main_log] if main_log.exists() else []
            
        for log_file in files_to_show:
            print(f"\n=== {log_file.name} ===")
            try:
                if tail:
                    # Show last N lines
                    with open(log_file, 'r') as f:
                        lines_list = f.readlines()
                        for line in lines_list[-lines:]:
                            print(line.rstrip())
                else:
                    # Show entire file
                    with open(log_file, 'r') as f:
                        print(f.read())
            except Exception as e:
                print(f"Error reading {log_file}: {e}")
                
        return True
        
    def clear_logs(self) -> bool:
        """Clear all log files"""
        if not self.logs_dir.exists():
            return True
            
        try:
            for log_file in self.logs_dir.rglob("*.log"):
                log_file.unlink()
            print("+ All logs cleared")
            return True
        except Exception as e:
            print(f"Error clearing logs: {e}")
            return False
            
    def follow_logs(self, component: Optional[str] = None) -> bool:
        """Follow logs in real-time (like tail -f)"""
        if component:
            log_file = self.logs_dir / "debug" / f"{component}.log"
            if not log_file.exists():
                print(f"No logs found for component: {component}")
                return False
        else:
            log_file = self.logs_dir / "main.log"
            
        if not log_file.exists():
            print(f"Log file does not exist: {log_file}")
            return False
            
        print(f"Following {log_file} (Ctrl+C to stop)...")
        try:
            # Simple tail -f implementation
            with open(log_file, 'r') as f:
                # Go to end of file
                f.seek(0, 2)
                while True:
                    line = f.readline()
                    if line:
                        print(line.rstrip())
                    else:
                        time.sleep(0.1)
        except KeyboardInterrupt:
            print("\nStopped following logs")
            return True
        except Exception as e:
            print(f"Error following logs: {e}")
            return False