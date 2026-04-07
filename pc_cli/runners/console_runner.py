"""
Console Runner for Excudo

Handles running the Java application in different modes (UI, console, headless)
with proper environment variable configuration and cross-platform support.
"""

import os
import shutil
import platform
import subprocess


class PCRunner:
    """Handles running the Java application"""
    
    def __init__(self, env):
        self.env = env
        
    def run(self, mode: str = "ui", **kwargs) -> bool:
        """Run the application in specified mode"""
        java = self.env.get_java_executable()
        classpath = self.env.get_classpath()
        
        # Set up environment variables for logging and flags
        env_vars = os.environ.copy()
        if "log_level" in kwargs:
            env_vars["PC_LOG_LEVEL"] = str(kwargs["log_level"])
        elif self.env.config.get("log_level"):
            env_vars["PC_LOG_LEVEL"] = str(self.env.config["log_level"])
            
        # Set auto-approve flag for LLM commands
        if kwargs.get("auto_approve", False):
            env_vars["PC_AUTO_APPROVE"] = "true"
            
        if "log_console" in kwargs:
            env_vars["PC_LOG_CONSOLE"] = str(kwargs["log_console"]).lower()
        elif not self.env.config.get("log_console", True):
            env_vars["PC_LOG_CONSOLE"] = "false"
            
        if mode in ("ui", "gui"):
            main_class = "com.excudo.ExcudoApp"
            cmd = [java, "-cp", classpath]
            
            # Add JavaFX module configuration if available
            javafx_lib_path = self.env.project_root / "lib" / "javafx-sdk-21" / "lib"
            if javafx_lib_path.exists():
                cmd.extend(["--module-path", str(javafx_lib_path), 
                           "--add-modules", "javafx.controls,javafx.fxml,javafx.swing,javafx.graphics"])
            
            cmd.append(main_class)
            
            # Use xvfb-run on Linux if headless requested
            if platform.system() == "Linux" and kwargs.get("headless", False):
                if shutil.which("xvfb-run"):
                    cmd = ["xvfb-run", "-a"] + cmd
                else:
                    print("Warning: xvfb-run not available for headless mode")
                    
        elif mode in ("console", "headless"):
            main_class = "com.excudo.console.InteractiveConsole"

            # Add JavaFX + Monocle to classpath for headless rendering
            javafx_lib_path = self.env.project_root / "lib" / "javafx-sdk-21" / "lib"
            monocle_jar = self.env.project_root / "lib" / "openjfx-monocle-21.0.2.jar"
            full_cp = classpath
            if javafx_lib_path.exists():
                full_cp += ":" + str(javafx_lib_path) + "/*"
            if monocle_jar.exists():
                full_cp += ":" + str(monocle_jar)

            cmd = [java, "-cp", full_cp,
                   "-Dglass.platform=Monocle", "-Dmonocle.platform=Headless",
                   "-Dprism.order=sw", "-Djava.awt.headless=true",
                   main_class]
            if mode == "headless":
                env_vars["PC_LOG_CONSOLE"] = "true"

        elif mode == "mcp":
            # MCP server mode: JSON-RPC over stdio
            main_class = "com.excudo.console.MCPConsoleEngine"
            cmd = [java, "-cp", classpath, main_class]
            # Optional file argument
            file_arg = kwargs.get("file")
            if file_arg:
                cmd.append(file_arg)

        else:
            print(f"Unknown run mode: {mode}")
            return False
            
        try:
            import sys
            if mode == "mcp":
                # MCP mode: stdout is the JSON-RPC channel, status goes to stderr
                print("Starting MCP server...", file=sys.stderr)
            else:
                print(f"Starting {mode} mode...")
            result = subprocess.run(cmd, cwd=self.env.project_root, env=env_vars)
            return result.returncode == 0
        except Exception as e:
            print(f"Error running application: {e}")
            return False