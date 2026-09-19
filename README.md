# PvPCombat

PvP/RPG plugin for **Paper 1.21.1 / Java 21**.

## Осколок
Осколок в коде — это `AMETHYST_SHARD` с собственной PDC-меткой PvPCombat. Обычный переименованный аметист валютой не считается.

Выдать себе:
```text
/pvpcombat give <ник> <количество>
```
Например:
```text
/pvpcombat give QuerlyFF 10
```
Нужно `pvpcombat.admin` (по умолчанию OP).

## Команды
```text
/pvpcombat
/pvpcombat stats [игрок]
/pvpcombat give <игрок> <n>
/pvpcombat set <игрок> <damage|health|speed|satiety|ability> <ступень>
/pvpcombat reload
```

## Конфиг
В `config.yml` вынесены длительность Combat, заряды и КД жемчуга, элитры, фейерверки, источники и лимит тотемов, процент урона End Crystal, а также все числовые параметры характеристик, штрафов и weapon abilities.

## Сборка
```bash
mvn clean package
```
Готовый JAR: `target/PvPCombat.jar`. GitHub Actions автоматически собирает JAR при push в `main`.
