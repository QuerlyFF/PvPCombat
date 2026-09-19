from pathlib import Path
import sys

ROOT = Path("src/main/java")
MAX_LINE_LENGTH = 180
errors: list[str] = []

for path in sorted(ROOT.rglob("*.java")):
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()

    # Сжатый класс почти всегда получается очень коротким по числу строк.
    if len(lines) < 8:
        errors.append(f"{path}: подозрительно мало строк ({len(lines)}); возможен сжатый однострочный код")

    if ";import " in text or ";public " in text or ";private " in text:
        errors.append(f"{path}: обнаружены склеенные Java-конструкции в одной строке")

    for number, line in enumerate(lines, start=1):
        if "\t" in line:
            errors.append(f"{path}:{number}: используется TAB вместо пробелов")
        if line.rstrip() != line:
            errors.append(f"{path}:{number}: пробелы в конце строки")
        if len(line) > MAX_LINE_LENGTH:
            errors.append(
                f"{path}:{number}: строка длиной {len(line)} символов; максимум {MAX_LINE_LENGTH}"
            )

if errors:
    print("Java style check failed:\n")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Java style check passed.")
