"""
API Configuration Manager for Excudo

Provides secure, user-friendly API key management for distribution.
"""

import os
import json
import getpass
import requests
from pathlib import Path
from typing import Optional, Dict, Any
import re


class APIConfigManager:
    """Manages API keys and configuration for distribution-ready deployment"""
    
    def __init__(self, project_root: Path):
        self.project_root = project_root
        self.env_file = project_root / ".env"
        self.env_local_file = project_root / ".env.local"
        
    def setup_interactive(self) -> bool:
        """Interactive setup wizard for API keys"""
        print("🚀 Excudo API Setup")
        print("=" * 50)
        print()
        print("This tool helps you configure API keys for:")
        print("  • Anthropic Claude API (Required for LLM features)")
        print("  • Freepik API (Optional for enhanced icons)")
        print()
        
        # Anthropic API Key
        print("📋 STEP 1: Anthropic Claude API Key")
        print("-" * 30)
        print("Required for AI-powered presentation editing.")
        print("Get your API key from: https://console.anthropic.com/")
        print()
        
        anthropic_key = self._prompt_api_key(
            "Enter your Anthropic API key",
            "ANTHROPIC_API_KEY",
            self._validate_anthropic_key
        )
        
        if not anthropic_key:
            print("❌ Anthropic API key is required for core functionality.")
            return False
            
        # Freepik API Key (Optional)
        print("\n📋 STEP 2: Freepik API Key (Optional)")
        print("-" * 40)
        print("Optional: Enables premium icon search and injection.")
        print("Get your API key from: https://www.freepik.com/api")
        print("Press Enter to skip if you don't have a Freepik account.")
        print()
        
        freepik_key = self._prompt_api_key(
            "Enter your Freepik API key (or press Enter to skip)",
            "FREEPIK_API_KEY",
            self._validate_freepik_key,
            optional=True
        )
        
        # Model Selection
        print("\n📋 STEP 3: Model Selection (Optional)")
        print("-" * 35)
        print("Default: claude-3-5-sonnet-20241022 (recommended)")
        print("Advanced users can specify a different Claude model.")
        print()
        
        model = input("Claude model [claude-3-5-sonnet-20241022]: ").strip()
        if not model:
            model = "claude-3-5-sonnet-20241022"
            
        # Extended Thinking
        print("\n📋 STEP 4: Extended Thinking (Optional)")
        print("-" * 38)
        print("Enable extended thinking for complex animation reasoning.")
        print("Recommended: Yes")
        print()
        
        thinking = input("Enable extended thinking? [Y/n]: ").strip().lower()
        extended_thinking = thinking != 'n'
        
        # Write configuration
        return self._write_configuration({
            "ANTHROPIC_API_KEY": anthropic_key,
            "ANTHROPIC_MODEL": model,
            "ANTHROPIC_EXTENDED_THINKING": str(extended_thinking).lower(),
            "FREEPIK_API_KEY": freepik_key if freepik_key else None
        })
        
    def set_anthropic_key(self, api_key: str) -> bool:
        """Set Anthropic API key directly"""
        if not self._validate_anthropic_key(api_key):
            print("❌ Invalid Anthropic API key format")
            return False
            
        return self._update_env_var("ANTHROPIC_API_KEY", api_key)
        
    def set_freepik_key(self, api_key: str) -> bool:
        """Set Freepik API key directly"""
        if not self._validate_freepik_key(api_key):
            print("❌ Invalid Freepik API key format")
            return False
            
        return self._update_env_var("FREEPIK_API_KEY", api_key)
        
    def validate_configuration(self) -> Dict[str, Any]:
        """Validate current API configuration"""
        results = {
            "anthropic": {"configured": False, "valid": False, "error": None},
            "freepik": {"configured": False, "valid": False, "error": None},
            "extended_thinking": {"enabled": False}
        }
        
        # Check Anthropic
        anthropic_key = self._get_env_var("ANTHROPIC_API_KEY")
        if anthropic_key:
            results["anthropic"]["configured"] = True
            if self._validate_anthropic_key(anthropic_key):
                # Test API call
                try:
                    valid, error = self._test_anthropic_api(anthropic_key)
                    results["anthropic"]["valid"] = valid
                    if error:
                        results["anthropic"]["error"] = error
                except Exception as e:
                    results["anthropic"]["error"] = str(e)
            else:
                results["anthropic"]["error"] = "Invalid key format"
                
        # Check Freepik
        freepik_key = self._get_env_var("FREEPIK_API_KEY")
        if freepik_key:
            results["freepik"]["configured"] = True
            if self._validate_freepik_key(freepik_key):
                try:
                    valid, error = self._test_freepik_api(freepik_key)
                    results["freepik"]["valid"] = valid
                    if error:
                        results["freepik"]["error"] = error
                except Exception as e:
                    results["freepik"]["error"] = str(e)
            else:
                results["freepik"]["error"] = "Invalid key format"
                
        # Check extended thinking
        thinking = self._get_env_var("ANTHROPIC_EXTENDED_THINKING")
        results["extended_thinking"]["enabled"] = thinking == "true"
        
        return results
        
    def _prompt_api_key(self, prompt: str, env_var: str, validator, optional: bool = False) -> Optional[str]:
        """Prompt user for API key with validation"""
        current = self._get_env_var(env_var)
        if current:
            print(f"Current {env_var}: {current[:8]}...{current[-4:] if len(current) > 12 else ''}")
            if input("Keep current key? [Y/n]: ").strip().lower() != 'n':
                return current
                
        while True:
            try:
                key = getpass.getpass(f"{prompt}: ").strip()
                if not key and optional:
                    return None
                if not key:
                    print("❌ API key cannot be empty")
                    continue
                    
                if validator(key):
                    return key
                else:
                    print("❌ Invalid API key format")
                    
            except KeyboardInterrupt:
                print("\n❌ Setup cancelled")
                return None
                
    def _validate_anthropic_key(self, key: str) -> bool:
        """Validate Anthropic API key format"""
        return bool(re.match(r'^sk-ant-api03-[A-Za-z0-9_-]{95}$', key))
        
    def _validate_freepik_key(self, key: str) -> bool:
        """Validate Freepik API key format"""
        return bool(re.match(r'^[A-Za-z0-9]{32,}$', key))
        
    def _test_anthropic_api(self, api_key: str) -> tuple[bool, Optional[str]]:
        """Test Anthropic API key with a simple request"""
        try:
            response = requests.post(
                "https://api.anthropic.com/v1/messages",
                headers={
                    "x-api-key": api_key,
                    "anthropic-version": "2023-06-01",
                    "content-type": "application/json"
                },
                json={
                    "model": "claude-3-5-sonnet-20241022",
                    "max_tokens": 10,
                    "messages": [{"role": "user", "content": "Hello"}]
                },
                timeout=10
            )
            
            if response.status_code == 200:
                return True, None
            elif response.status_code == 401:
                return False, "Invalid API key"
            elif response.status_code == 429:
                return True, "Rate limited (key is valid)"
            else:
                return False, f"API error: {response.status_code}"
                
        except requests.exceptions.RequestException as e:
            return False, f"Network error: {str(e)}"
            
    def _test_freepik_api(self, api_key: str) -> tuple[bool, Optional[str]]:
        """Test Freepik API key"""
        try:
            response = requests.get(
                "https://api.freepik.com/v1/icons",
                headers={"x-freepik-api-key": api_key},
                params={"query": "test", "limit": 1},
                timeout=10
            )
            
            if response.status_code == 200:
                return True, None
            elif response.status_code == 401:
                return False, "Invalid API key"
            elif response.status_code == 429:
                return True, "Rate limited (key is valid)"
            else:
                return False, f"API error: {response.status_code}"
                
        except requests.exceptions.RequestException as e:
            return False, f"Network error: {str(e)}"
            
    def _get_env_var(self, key: str) -> Optional[str]:
        """Get environment variable from .env files or system"""
        # Check .env.local first
        if self.env_local_file.exists():
            value = self._read_env_file(self.env_local_file, key)
            if value:
                return value
                
        # Check .env
        if self.env_file.exists():
            value = self._read_env_file(self.env_file, key)
            if value:
                return value
                
        # Check system environment
        return os.getenv(key)
        
    def _read_env_file(self, file_path: Path, key: str) -> Optional[str]:
        """Read specific key from env file"""
        try:
            with open(file_path, 'r') as f:
                for line in f:
                    line = line.strip()
                    if line.startswith(f"{key}="):
                        value = line[len(key)+1:]
                        # Remove quotes
                        if value.startswith('"') and value.endswith('"'):
                            value = value[1:-1]
                        elif value.startswith("'") and value.endswith("'"):
                            value = value[1:-1]
                        return value
        except IOError:
            pass
        return None
        
    def _update_env_var(self, key: str, value: str) -> bool:
        """Update environment variable in .env.local"""
        try:
            # Read existing .env.local
            env_vars = {}
            if self.env_local_file.exists():
                with open(self.env_local_file, 'r') as f:
                    for line in f:
                        line = line.strip()
                        if '=' in line and not line.startswith('#'):
                            k, v = line.split('=', 1)
                            env_vars[k.strip()] = v.strip()
                            
            # Update the key
            env_vars[key] = f'"{value}"'
            
            # Write back to .env.local
            with open(self.env_local_file, 'w') as f:
                f.write("# Excudo Local Configuration\n")
                f.write("# This file is automatically generated and should not be committed\n\n")
                for k, v in env_vars.items():
                    f.write(f"{k}={v}\n")
                    
            print(f"✅ {key} updated in {self.env_local_file}")
            return True
            
        except IOError as e:
            print(f"❌ Failed to update {key}: {e}")
            return False
            
    def _write_configuration(self, config: Dict[str, Optional[str]]) -> bool:
        """Write complete configuration to .env.local"""
        try:
            with open(self.env_local_file, 'w') as f:
                f.write("# Excudo Local Configuration\n")
                f.write("# Generated by setup wizard - DO NOT COMMIT TO VERSION CONTROL\n\n")
                
                for key, value in config.items():
                    if value is not None:
                        f.write(f'{key}="{value}"\n')
                        
            print(f"\n✅ Configuration saved to {self.env_local_file}")
            print("\n🎉 Setup complete! You can now use Excudo.")
            print("\nNext steps:")
            print("  1. Test your configuration: pc.py config --validate-api")
            print("  2. Start editing presentations: pc.py run console")
            print(f"\n📁 Your API keys are stored in: {self.env_local_file}")
            print("   (This file should not be committed to version control)")
            
            return True
            
        except IOError as e:
            print(f"❌ Failed to write configuration: {e}")
            return False