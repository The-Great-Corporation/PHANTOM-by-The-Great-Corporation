# Install script for PHANTOM server
Write-Host "Installing PHANTOM Server..."

# Check Python
if (!(Get-Command python -ErrorAction SilentlyContinue)) {
    Write-Host "Python is not installed or not in PATH." -ForegroundColor Red
    exit 1
}

# Create venv
if (!(Test-Path -Path "venv")) {
    Write-Host "Creating virtual environment..."
    python -m venv venv
}

# Install dependencies (if any)
# Write-Host "Installing requirements..."
# .\venv\Scripts\python -m pip install -r requirements.txt

# Create desktop shortcut
$WshShell = New-Object -comObject WScript.Shell
$Shortcut = $WshShell.CreateShortcut("$Home\Desktop\PHANTOM Server.lnk")
$Shortcut.TargetPath = "$PWD\venv\Scripts\pythonw.exe"
$Shortcut.Arguments = "$PWD\server\gui_app.py"
$Shortcut.WorkingDirectory = "$PWD"
$Shortcut.Save()

Write-Host "Installation complete! Shortcut created on Desktop." -ForegroundColor Green
