#!/usr/bin/env python3
"""
Excudo CLI - Legacy Wrapper

This file serves as a backward-compatible entry point that imports
and delegates to the modular pc_cli package. All functionality has been
refactored into organized modules under pc_cli/.

For the new modular structure, see:
- pc_cli/main.py - Main entry point
- pc_cli/environment.py - Environment management
- pc_cli/builders/ - Build system
- pc_cli/runners/ - Application runners
- pc_cli/testers/ - Test execution
- pc_cli/analyzers/ - Code and data analysis
- pc_cli/commands/ - Command handlers
"""

import sys
from pc_cli import main

if __name__ == "__main__":
    sys.exit(main())