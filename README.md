# PvPCombat

PvP/RPG plugin for **Paper 1.21.1 / Java 21**.

## Что реализовано

- PvP Combat-tag с настраиваемой длительностью.
- 3 (настраивается) заряда Ender Pearl + дополнительный cooldown.
- Ограничения элитр и Elytra-firework boost во время Combat.
- Лимит тотемов, запрет пополнения из внешних источников в Combat и трофейные тотемы.
- Ограниченные зачарования булавы с сохранением vanilla-совместимости.
- Настраиваемый урон End Crystal.
- Прокачка урона, здоровья, скорости, экономии сытости и Умений.
- Способности меча, топора, лука и арбалета.
- Потеря одной ступени при смерти с отдельным cooldown.
- Антифарм Осколков для пары `killer -> victim`.
- Безопасная интеграция Combat UI с чужими scoreboard: PvPCombat не заменяет scoreboard другого плагина; при занятом/shared SIDEBAR используется ActionBar fallback.
- Java API через Bukkit ServicesManager для других плагинов.

## Осколок

Осколок — это предмет, который по умолчанию визуально использует `AMETHYST_SHARD`, но определяется **служебной PDC-меткой PvPCombat**. Простое переименование аметиста через наковальню не создаёт настоящий Осколок.

Выдать себе:

```text
/pvpcombat give QuerlyFF 10
```

Нужно право `pvpcombat.admin` (по умолчанию OP).

## Команды

```text
/pvpcombat
/pvpcombat stats [игрок]
/pvpcombat give <игрок> <количество>
/pvpcombat set <игрок> <damage|health|speed|satiety|ability> <ступень>
/pvpcombat reload
```

Для `health` команда `set` работает с внутренней ступенью относительно базового здоровья: `0` = базовые 10 сердец при стандартном config, отрицательные ступени — штрафная зона.

## Конфигурация

`config.yml` разделён на секции:

- `combat` — Combat duration и cooldown'ы наград/штрафов;
- `pearls` — количество зарядов и recharge;
- `movement` — элитры и фейерверки;
- `totems` — лимит, внешние источники, длительность боя для трофея и антиспам уведомлений;
- `end-crystal` — процент ванильного урона;
- `stats` — все пределы и ступени характеристик;
- `death-penalty` — веса потери характеристик;
- `abilities` — все шансы, длительности и множители weapon abilities;
- `items` — внешний вид Осколка;
- `scoreboard` — Combat UI и режим интеграции.

Экономия сытости применяется к **exhaustion**, поэтому 70% действительно означает уменьшение расхода на 70%, без округления hunger bar.

## Интеграция с другими плагинами

API регистрируется через Bukkit `ServicesManager` как `PvPCombatApi`. Доступны создание, выдача, подсчёт, проверка и списание Осколков, чтение статов и проверка Combat-state.

Здоровье и скорость PvPCombat применяет собственными keyed `AttributeModifier`, не перезаписывая base-атрибуты других плагинов.

## Сборка

```bash
mvn clean package
```

Готовый файл: `target/PvPCombat.jar`.

GitHub Actions собирает проект на **Java 21** при push в `main`, `fix/**`, `feature/**` и при pull request в `main`.
