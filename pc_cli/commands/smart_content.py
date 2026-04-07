"""
SmartContent Command Handler for Excudo

Handles SmartContent/LLM commands: search, inject, upload, list
"""

import json
import subprocess


def handle_smart_command(args, env):
    """Handle SmartContent/LLM commands"""
    try:
        java_cmd = [env.get_java_executable()]
        classpath = env.get_classpath()
        
        # Build base command for HeadlessInterface
        cmd = java_cmd + [
            "-cp", classpath,
            "com.excudo.cli.HeadlessInterface",
            "--json"
        ]
        
        if args.smart_command == "search":
            cmd.extend(["--smart-search", args.query])
            cmd.extend(["--max-results", str(args.max)])
            if args.source != "all":
                cmd.extend(["--source", args.source])
                
        elif args.smart_command == "inject":
            cmd.extend([
                "--smart-inject", args.query,
                "--presentation", args.presentation,
                "--slide", str(args.slide)
            ])
            if args.cache_dir:
                cmd.extend(["--cache-dir", args.cache_dir])
            if args.position != "auto":
                cmd.extend(["--position", args.position])
                
        elif args.smart_command == "upload":
            cmd.extend([
                "--smart-upload", args.file,
                "--name", args.name
            ])
            if args.tags:
                cmd.extend(["--tags"] + args.tags)
            if args.cache_dir:
                cmd.extend(["--cache-dir", args.cache_dir])
                
        elif args.smart_command == "list":
            cmd.append("--smart-list")
            if args.source != "all":
                cmd.extend(["--source", args.source])
            if args.cache_dir:
                cmd.extend(["--cache-dir", args.cache_dir])
                
        else:
            print(f"Unknown SmartContent command: {args.smart_command}")
            return False
            
        # Execute command
        result = subprocess.run(cmd, cwd=env.project_root, capture_output=True, text=True)
        
        if result.returncode == 0:
            try:
                # Parse JSON response
                response = json.loads(result.stdout)
                _display_smart_response(response, args.smart_command)
                return True
            except json.JSONDecodeError:
                # Fallback to plain text output
                print(result.stdout)
                return True
        else:
            print(f"SmartContent command failed: {result.stderr}")
            return False
            
    except Exception as e:
        print(f"SmartContent command failed: {e}")
        return False


def _display_smart_response(response, command):
    """Display formatted SmartContent response"""
    if not response.get("success", False):
        error = response.get("error", "Unknown error")
        print(f"✗ {error}")
        return
        
    if command == "search":
        icons = response.get("icons", [])
        result_count = response.get("result_count", 0)
        search_time = response.get("search_time_ms", 0)
        
        print(f"  Found {result_count} icons in {search_time}ms")
        for icon in icons[:5]:  # Show first 5 results
            print(f"    {icon.get('name')} ({icon.get('source')}) - Score: {icon.get('relevance_score', 0):.2f}")
            if icon.get('author_name'):
                print(f"      Artist: {icon.get('author_name')}")
        
        if len(icons) > 5:
            print(f"    ... and {len(icons) - 5} more")
            
    elif command == "inject":
        shape_info = response.get("injected_shape", {})
        slide_num = response.get("slide_number", 1)
        query = response.get("query", "")
        
        print(f"  Injected '{query}' into slide {slide_num}")
        print(f"  Position: ({shape_info.get('x', 0)}, {shape_info.get('y', 0)})")
        print(f"  Size: {shape_info.get('width', 0)} x {shape_info.get('height', 0)}")
        print(f"  Attribution automatically added to slide notes")
        
    elif command == "upload":
        icon_info = response.get("uploaded_icon", {})
        print(f"  Uploaded: {icon_info.get('name')} ({icon_info.get('source')})")
        print(f"  Tags: {icon_info.get('tags', '')}")
        
    elif command == "list":
        total = response.get("total_count", 0)
        devicon = response.get("devicon_count", 0)
        local = response.get("local_count", 0)
        freepik = response.get("freepik_available", False)
        
        print(f"  Total: {total} icons")
        print(f"  Devicon: {devicon}, Local: {local}, Freepik: {'Available' if freepik else 'Not configured'}")