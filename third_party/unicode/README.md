# Unicode CLDR 的拼音数据（片库搜索的全拼表）

片库搜索（`docs/search.md` §3）认拼音全拼（aganzhengzhuan → 阿甘正传）。U+4E00–U+9FA5 每个字的主读音取自 ICU 的 `Han-Latin` 转写，
它的数据来自 Unicode CLDR（汉字读音源自 Unihan）。

- 取法：`scripts/gen-search-tables.mjs` 调 Windows 自带的 ICU（`System32\icu.dll`，Windows 10 1703 起系统自带），一个字一个字转写，去掉声调（ü 写 v）。
  不联网、不装东西；只能在 Windows 上取（别的系统用 `--pinyin-dump` 喂一份 Windows 上导出的）。
- 许可证：Unicode License V3（SPDX `Unicode-3.0`），原文 `LICENSE`（摘自 ICU 的许可证全文）。
- 结果写成 `ui/…/search/SearchTables.kt` 的 `PINYIN_SYLLABLES` / `PINYIN_FULL`（生成物）。同一个文件里的 `PINYIN_ALT`（多音字的其它读音）是本仓库手写的，不来自 CLDR。
- 首字母表 `ui/…/data/PinyinTable.kt`（`scripts/gen-pinyin-table.mjs`）用的是 ICU 的拼音排序规则，同属 CLDR 数据。

声明同步在 `shell-desktop/packaging/NOTICE.md` 与 `ui/…/screens/settings/OpenSourceLicenses.kt`（改一边要改另一边）。
