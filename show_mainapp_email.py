from pathlib import Path
lines = Path(r'src\\main\\java\\org\\example\\MainApp.java').read_text().splitlines()
for idx in range(160, 250):
    print(f"{idx+1}: {lines[idx]}")
