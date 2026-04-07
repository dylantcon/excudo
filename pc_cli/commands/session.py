"""
Session Command Handler for Excudo

Handles session management commands: create, list, info, close
"""

import json
import subprocess


def handle_session_command(args, env):
    """Handle session management commands"""
    try:
        java_cmd = [env.get_java_executable()]
        classpath = env.get_classpath()
        
        # Build base command for HeadlessInterface
        cmd = java_cmd + [
            "-cp", classpath,
            "com.excudo.cli.HeadlessInterface",
            "--json"
        ]
        
        if args.session_command == "create":
            cmd.append("--create-session")
            if hasattr(args, 'file') and args.file:
                cmd.extend(["--presentation", args.file])
                
        elif args.session_command == "list":
            cmd.append("--list-sessions")
            
        elif args.session_command == "info":
            cmd.extend(["--session-info", args.session_id])
            
        elif args.session_command == "close":
            cmd.extend(["--close-session", args.session_id])
            
        else:
            print(f"Unknown session command: {args.session_command}")
            return False
            
        # Execute command
        result = subprocess.run(cmd, cwd=env.project_root, capture_output=True, text=True)
        
        if result.returncode == 0:
            try:
                # Parse JSON response
                response = json.loads(result.stdout)
                _display_session_response(response, args.session_command)
                return True
            except json.JSONDecodeError:
                # Fallback to plain text output
                print(result.stdout)
                return True
        else:
            print(f"Command failed: {result.stderr}")
            return False
            
    except Exception as e:
        print(f"Session command failed: {e}")
        return False


def _display_session_response(response, command):
    """Display formatted session response"""
    if response.get("success", False):
        if command == "create":
            session_id = response.get("session_id")
            file_loaded = response.get("file_loaded")
            print(f"✓ Session created: {session_id}")
            if file_loaded:
                print(f"  Loaded: {file_loaded}")
                
        elif command == "list":
            sessions = response.get("sessions", [])
            if sessions:
                print(f"Active sessions ({len(sessions)}):")
                for session in sessions:
                    print(f"  {session['id']}: {session.get('file', 'New presentation')}")
            else:
                print("No active sessions")
                
        elif command == "info":
            session = response.get("session", {})
            print(f"Session: {session.get('id')}")
            print(f"  File: {session.get('file', 'New presentation')}")
            print(f"  Created: {session.get('created')}")
            print(f"  Slides: {session.get('slide_count', 0)}")
            
        elif command == "close":
            session_id = response.get("session_id")
            print(f"✓ Session closed: {session_id}")
            
    else:
        error = response.get("error", "Unknown error")
        print(f"✗ {error}")