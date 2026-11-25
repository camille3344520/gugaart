# OpenCC（片库搜索的繁 → 简单字表）

片库搜索（`docs/search.md` §2）把片名和查询词里的繁体字折成简体再比（「速度與激情」→ 速度与激情）。用到的单字表生成自 OpenCC 的 `TSCharacters`。

- 来源：OpenCC（Open Chinese Convert）的词典数据 `data/dictionary/TSCharacters.txt`，<https://github.com/BYVoid/OpenCC>。
  本机生成时读的是 opencc-js 1.4.2 随包的同一份数据（`dist/esm-lib/dict/TSCharacters.js`，由 nk2028/opencc-data 从 OpenCC 生成）。
- 许可证：Apache License 2.0，原文 `LICENSE`（未修改的副本）。
- 怎么用的：`scripts/gen-search-tables.mjs` 只取「一个字 → 第一个候选字」、两边都在 BMP 里的 2966 对，写成 `ui/…/search/SearchTables.kt` 的 `T2S_FROM` / `T2S_TO`（生成物）。
  没有拷贝 OpenCC 的任何代码；改动 = 只留单字、只留第一个候选、改写成 Kotlin 字符串常量。

声明同步在 `shell-desktop/packaging/NOTICE.md` 与 `ui/…/screens/settings/OpenSourceLicenses.kt`（改一边要改另一边）。
