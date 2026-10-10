# Dilnoza Books — loyiha rejasi

> Android uchun offline kitob yozish ilovasi. Foydalanuvchi o'z kitobini yozadi: boblar, stillangan matn, jadvallar, rasmlar. Hammasi avtomatik saqlanadi va PDF, DOCX, EPUB, HTML, TXT yoki Markdown ko'rinishida telefonga eksport qilinadi.

---

## 1. Asosiy ma'lumotlar

| Parametr | Qiymat |
|---|---|
| Ilova nomi | **Dilnoza Books** |
| Package (applicationId) | `dilnoza.books` |
| Til | Kotlin (100%) |
| UI | Jetpack Compose + Material 3 |
| minSdk / targetSdk | 26 (Android 8.0) / so'nggi barqaror SDK |
| Internet | **Yo'q.** `INTERNET` ruxsati manifestga umuman qo'shilmaydi |
| Interfeys tillari | O'zbek (lotin), rus, ingliz. Telefon tiliga qarab tanlanadi, sozlamada o'zgartirsa bo'ladi |
| Tema | Yorug' / qorong'i / tizimga mos |

## 2. Funksional talablar

### 2.1 Kitoblar javoni
- Kitoblar ro'yxati muqova, nom, muallif, oxirgi tahrir sanasi va so'zlar soni bilan ko'rsatiladi
- Kitob yaratish, nomini o'zgartirish, nusxalash, o'chirish (o'chirishdan oldin tasdiq so'raladi)
- Kitoblar ichida qidirish va saralash (sana, nom bo'yicha)
- Zaxira faylidan (`.dbook`) kitob import qilish

### 2.2 Kitob sozlamalari
- Nom, muallif, qisqa tavsif
- Muqova: galereyadan rasm tanlanadi yoki faqat rang va matndan iborat muqova yasaladi
- Sahifa o'lchami (A4, A5, Letter), chegaralar va asosiy shrift. Bular eksport uchun ishlatiladi
- Header/footer matni va sahifa raqamlari (yoqish/o'chirish)

### 2.3 Boblar
- Bob qo'shish, nomini o'zgartirish, o'chirish
- Boblarni sudrab tartiblash (drag & drop)
- Avtomatik mundarija bob nomlari va ichidagi sarlavhalardan tuziladi; bosilganda o'sha joyga o'tiladi

### 2.4 Muharrir (bloklarga asoslangan)
**Blok turlari:**

| Blok | Imkoniyatlari |
|---|---|
| Sarlavha | H1, H2, H3 |
| Paragraf | Stillangan matn (pastda) |
| Ro'yxat | Nuqtali yoki raqamli, ichma-ich (2 daraja) |
| Iqtibos | Alohida stil bilan |
| Rasm | Galereya yoki kamera; izoh (caption), kenglik (25/50/75/100%), tekislash |
| Jadval | Qator va ustun qo'shish/o'chirish, sarlavha qatori, katak ichida oddiy stillar |
| Ajratgich | Gorizontal chiziq |
| Sahifa uzilishi | Eksportda yangi sahifadan boshlanadi |

**Matn stillari:** qalin, kursiv, tagiga chizilgan, ustidan chizilgan, matn rangi, fon (marker) rangi, shrift (3–5 ta ichki shrift), shrift o'lchami, tekislash (chap, markaz, o'ng, eni bo'yicha), qator oralig'i.

**Muharrir imkoniyatlari:**
- Blokni qo'shish, o'chirish, nusxalash, yuqoriga/pastga surish, turini o'zgartirish
- Undo/Redo (har bob uchun 100 qadamgacha)
- Bob ichida qidirish va almashtirish (find & replace)
- So'zlar va belgilar soni

### 2.4.1 Varaqlar (print layout)
- Muharrir bobni **haqiqiy varaqlar** koʻrinishida koʻrsatadi: varaqning oʻlchami qatʼiy (kitob sozlamasida A5, A4 yoki Letter), varaqlar orasida aniq oraliq bor va har varaq ostida sahifa raqami turadi
- Varaq toʻlsa, matn **avtomatik** keyingi varaqqa oqadi; matn oʻchirilsa, orqaga qaytadi. Paragraf satr chegarasidan boʻlinadi va varaqning yuqorisida ham, pastida ham kamida 2 satr qoladi. Rasm, jadval va ajratgich boʻlinmaydi. Sarlavha keyingi matndan ajralmaydi
- Bob oxirida **"+ Yangi varaq"** tugmasi bor. U sahifa uzilishi qoʻyadi va kursorni yangi varaqqa olib oʻtadi
- Sahifa raqamlari **butun kitob boʻylab** davom etadi; boblar roʻyxatida har bob egallagan betlar koʻrsatiladi (masalan, "4–5-betlar")
- **Har qanday ekranda bir xil koʻrinish**: varaq nuqtalarda (pt) joylanadi va ekranga masshtablanadi, matn ilova ichiga joylangan Noto Serif shriftida chiziladi. Shuning uchun satrlar har qanday telefonda aynan bir xil joydan uziladi (720×1280 va 1080×2400 ekranlarda tekshirilgan). PDF eksport ham shu dvigateldan foydalanadi

### 2.5 Avtosaqlash
- Har bir o'zgarish **~500 ms** tanaffusdan so'ng (debounce) bazaga yoziladi
- Ilova fonga o'tganda (`onStop`), bob almashtirilganda va ekrandan chiqilganda darhol saqlanadi
- Yozish Room tranzaksiyasi ichida bajariladi, shuning uchun yarim saqlangan holat qolmaydi
- Ekranda "Saqlandi ✓" holati ko'rsatiladi

### 2.6 Kitob ichida qidirish
- Butun kitob bo'ylab qidiriladi (Room FTS4 jadvali)
- Natijalar bob nomi va kontekst bilan chiqadi; bosilganda o'sha blokka o'tiladi va topilgan joy belgilanadi

### 2.7 Eksport
Hammasi offline, qurilmaning o'zida yaratiladi.

| Format | Usul | Tarkibi |
|---|---|---|
| **PDF** | Android `PdfDocument` + `StaticLayout` | Muqova, mundarija (sahifa raqamlari bilan, 2 o'tishda), sahifalash, rasmlar, jadvallar, header/footer, shrift joylanadi |
| **DOCX** | OOXML'ni o'zimiz yaratamiz (`ZipOutputStream` + XML) | `document.xml`, `styles.xml`, `numbering.xml`, rasmlar (`word/media`), Word'ning mundarija maydoni (TOC field), sahifa uzilishlari |
| **EPUB 3** | zip + XHTML | `mimetype` (siqilmagan, birinchi fayl), `container.xml`, `content.opf`, `nav.xhtml`, har bob uchun alohida XHTML, muqova |
| **HTML** | Bitta `.html` fayl | CSS ichida, rasmlar base64 ko'rinishida |
| **TXT** | Oddiy matn | Stillarsiz, boblar ajratilgan |
| **Markdown** | `.md` | Sarlavhalar, ro'yxatlar, jadvallar; rasmlar bilan `.zip` qilish ixtiyoriy |

**Eksport oqimi:** format → sozlamalar (sahifa o'lchami, muqova va mundarija kiritilsinmi) → progress → **"Telefonga saqlash"** (SAF `CreateDocument`, foydalanuvchi papkani o'zi tanlaydi) yoki **"Ulashish"** (`FileProvider` + share intent).

### 2.8 Zaxira (backup)
- Kitob `.dbook` faylga eksport qilinadi (ichida zip: `book.json` + `images/`)
- `book.json` ichida format versiyasi saqlanadi (`schemaVersion`), kelajakdagi o'zgarishlar uchun
- Import paytida fayl tekshiriladi; buzilgan bo'lsa, tushunarli xato ko'rsatiladi. Kitob yangi ID bilan qo'shiladi va mavjud kitobni ustidan yozmaydi
- "Barcha kitoblarni zaxiralash" bitta zip faylga chiqariladi

### 2.9 Xavfsizlik: PIN va barmoq izi
- 4–6 xonali PIN; xeshlangan holda saqlanadi (PBKDF2 + salt, DataStore'da)
- Barmoq izi yoki yuz orqali kirish (`BiometricPrompt`), ixtiyoriy
- Ilova fonga o'tib, belgilangan vaqtdan keyin qaytsa qulflanadi (darhol, 1 daqiqa yoki 5 daqiqa)
- Ixtiyoriy: skrinshot olishni taqiqlash (`FLAG_SECURE`)
- PIN unutilsa ma'lumotlarni tiklab bo'lmaydi. Bu haqda PIN o'rnatilayotganda ogohlantiriladi

### 2.10 Sozlamalar
Tema, interfeys tili, muharrirning standart shrifti va o'lchami, PIN/biometrik qulf, zaxira, ilova haqida.

## 3. Nofunksional talablar
- **Offline:** hech qanday tarmoq so'rovi yo'q, tahlil (analytics) yo'q
- **Ishonchlilik:** ilova kutilmaganda yopilsa ham oxirgi 1 soniyadan oldingi o'zgarishlar saqlangan bo'ladi
- **Tezlik:** 300+ sahifali kitob ham muharrirda sekinlashmaydi (bob ichida `LazyColumn`, boblar alohida yuklanadi)
- **Rasmlar:** ilova ichki xotirasiga nusxalanadi va eng uzun tomoni 2048 px gacha kichraytiriladi (JPEG 85%). Galereyadan o'chirilsa ham kitobda qoladi
- **Hajm:** APK ~10–15 MB atrofida (shriftlar bilan)
- **Kirill va lotin:** ichki shriftlarning barchasi ikkala yozuvni ham, shuningdek o'zbekcha `oʻ`, `gʻ` harflarini ham qo'llab-quvvatlaydi (PDF uchun bu muhim)

## 4. Texnik arxitektura

### 4.1 Texnologiyalar

| Vazifa | Kutubxona |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| DI | Hilt |
| Baza | Room (+ FTS4) |
| Sozlamalar | DataStore Preferences |
| Seriyalash | kotlinx.serialization (blok kontenti, backup) |
| Rasm yuklash | Coil |
| Asinxron ishlar | Coroutines + Flow |
| Biometrika | androidx.biometric |
| Tillar | AppCompat per-app language API |
| Test | JUnit, Turbine, Room testing, Compose UI test |

Barcha versiyalar Gradle version catalog'da (`libs.versions.toml`) yoziladi. Har biri 1-bosqichda so'nggi barqaror versiyaga o'rnatiladi.

### 4.2 Arxitektura
MVVM + Clean (soddalashtirilgan), bitta `:app` moduli, paketlar bo'yicha bo'linadi:

```
dilnoza.books
├── core/            # umumiy util, tema, dispatcher'lar
├── data/
│   ├── db/          # Room: entity, dao, database, migration
│   ├── files/       # rasm saqlash, ichki fayllar
│   ├── prefs/       # DataStore
│   └── repository/  # repository implementatsiyalari
├── domain/
│   ├── model/       # Book, Chapter, Block, RichText, Span ...
│   └── usecase/
├── editor/          # muharrir: holat, undo/redo, rich text, bloklar UI
├── export/
│   ├── pdf/  docx/  epub/  html/  text/
│   └── ExportManager
├── backup/          # .dbook import/export
├── security/        # PIN, biometrika, qulf
└── ui/
    ├── library/  book/  chapters/  editor/  export/  search/  settings/  lock/
    └── navigation/
```

### 4.3 Ma'lumotlar modeli

```
Book(id, title, author, description, coverPath, coverColor,
     pageSize, marginsMm, fontFamily, headerText, footerText,
     showPageNumbers, createdAt, updatedAt)

Chapter(id, bookId → Book, position, title, createdAt, updatedAt)

Block(id, chapterId → Chapter, position, type, contentJson, updatedAt)

BlockFts(blockId, plainText)   -- FTS4, qidiruv uchun
```

`contentJson` blok turiga qarab tuziladi:
- **Matn bloklari:** `{ text, spans: [{start, end, bold, italic, underline, strike, color, bg, font, size}], align, lineHeight, level }`
- **Rasm:** `{ path, caption, widthPercent, align }`
- **Jadval:** `{ rows, cols, headerRow, cells: [[RichText]] }`

`position` qiymatlari oraliq bilan beriladi (1000, 2000...). Shunda tartiblashda faqat bitta qator yangilanadi.

### 4.4 Muharrir — eng xavfli qism
- Har bir matn bloki o'z `RichText` modeliga ega: `text` va `spans` ro'yxati
- Compose'da `BasicTextField` ishlatiladi; ko'rsatish uchun spanlar `AnnotatedString`ga aylantiriladi
- **Asosiy qiyinchilik:** matn o'zgarganda spanlarni to'g'ri siljitish kerak (qo'shish, o'chirish, almashtirish). Buning uchun alohida, unit testlar bilan to'liq qoplangan `SpanAdjuster` yoziladi
- **Zaxira varianti:** agar o'zimizning yechim 2-bosqichda barqaror bo'lmasa, offline ishlaydigan ochiq kodli `compose-rich-editor` kutubxonasiga o'tamiz
- Undo/Redo: buyruqlar steki (blok qo'shish/o'chirish/surish, matn o'zgarishi). Matn o'zgarishlari ~1 soniya ichida birlashtirib yoziladi

## 5. Ekranlar va navigatsiya

```
[Qulf (PIN)] ──► Javon ──► Kitob (boblar + mundarija) ──► Muharrir
                   │               │                         │
                   │               ├─► Kitob sozlamalari     ├─► Qidirish/almashtirish
                   │               ├─► Eksport               └─► Rasm / jadval tahriri
                   │               └─► Kitob ichida qidirish
                   └─► Sozlamalar (tema, til, PIN, zaxira)
```

## 6. Bosqichma-bosqich reja

Har bosqich alohida branch va PR bo'lib boradi. Har bosqich oxirida ilova yig'iladi (build) va telefonda tekshiriladi.

### 0-bosqich — Repo va muhit
1. GitHub repo (siz ochasiz) → lokal papka bilan bog'lanadi
2. `.gitignore` (Android), `README.md`, `REJA.md`
3. GitHub Actions: har PR'da `assembleDebug` + `testDebugUnitTest` + `lint`
4. Branch strategiyasi: `main` (barqaror) ← `feature/*`

**Tayyor bo'ldi deyiladi, qachonki:** repo ochilgan, CI ishlagan.

### 1-bosqich — Skelet, baza, kitob va boblar
1. Gradle loyihasi: Kotlin, Compose, Hilt, Room, version catalog, `dilnoza.books`
2. Tema (yorug'/qorong'i), 3 tildagi string resurslari, navigatsiya
3. Room: `Book`, `Chapter`, `Block` + DAO + repository
4. Javon ekrani: kitob yaratish, ro'yxat, o'chirish, nusxalash
5. Kitob ekrani: boblar ro'yxati, qo'shish, nomini o'zgartirish, o'chirish, drag & drop
6. Kitob sozlamalari ekrani (muqovadan tashqari)
7. Avtosaqlash mexanizmi (debounce + `onStop` flush) va "Saqlandi" indikatori
8. DAO va repository unit testlari

**Tayyor:** kitob va bob yaratiladi; ilova yopib ochilganda hammasi joyida turadi.

### 2-bosqich — Muharrir
1. `RichText` modeli + `SpanAdjuster` + to'liq unit testlar
2. Blok ro'yxati (`LazyColumn`): paragraf, sarlavha, iqtibos, ro'yxat, ajratgich, sahifa uzilishi
3. Formatlash paneli (klaviatura ustida): B/I/U/S, rang, marker, shrift, o'lcham, tekislash
4. Blok amallari: qo'shish ("+" menyusi), o'chirish, surish, turini o'zgartirish, Enter/Backspace bilan bo'lish va birlashtirish
5. Rasm bloki: tanlash, kamera, siqish, ichki xotiraga saqlash, izoh va kenglik
6. Jadval bloki: tahrir, qator va ustun qo'shish/o'chirish
7. Undo/Redo
8. Bob ichida qidirish va almashtirish
9. So'zlar soni

**Tayyor:** barcha blok turlari ishlaydi, har bir o'zgarish avtomatik saqlanadi, undo/redo ishlaydi.

### 3-bosqich — Eksport
1. Umumiy `DocumentModel`: kitob → boblar → bloklar (eksportchilar uchun bitta kirish nuqtasi)
2. TXT va Markdown
3. HTML
4. EPUB 3
5. DOCX
6. PDF: sahifalash, rasmlar, jadvallar, header/footer, mundarija sahifa raqamlari bilan
7. Eksport ekrani: format, sozlamalar, progress, SAF orqali saqlash, ulashish
8. Har bir format uchun test: namunaviy kitob eksport qilinadi va fayl tuzilishi tekshiriladi. Qo'lda Word, Google Docs, PDF ko'ruvchi va EPUB o'qigichda ochib ko'riladi

**Tayyor:** 6 formatning hammasi telefonga saqlanadi va tegishli dasturlarda to'g'ri ochiladi.

### 4-bosqich — Muqova, mundarija, qidiruv, zaxira
1. Muqova: rasm yoki rang + matn shablon, javonda ko'rinadi
2. Avtomatik mundarija ekrani
3. Kitob bo'ylab FTS qidiruv va natijaga o'tish
4. `.dbook` backup: bitta kitob, barcha kitoblar; import

**Tayyor:** backup qilingan kitob boshqa telefonda import qilinganda rasmlari bilan to'liq tiklanadi.

### 5-bosqich — Xavfsizlik va sozlamalar
1. Sozlamalar ekrani: tema, til, standart shrift
2. PIN o'rnatish, o'zgartirish, o'chirish; qulf ekrani
3. Biometrik kirish
4. Avtoqulf vaqti, `FLAG_SECURE`

**Tayyor:** PIN yoqilgan bo'lsa, fonga o'tib qaytgandan keyin ilova qulflanadi.

### 6-bosqich — Sayqal va reliz
1. Katta kitob bilan tezlik testi (300+ sahifa, 100+ rasm)
2. Accessibility: kontrast, TalkBack yorliqlari
3. Ilova ikonkasi va splash
4. Release build: R8/minify, imzolash (keystore), `versionCode`/`versionName`
5. APK/AAB yig'ish; Play Market kerak bo'lsa, store listing va privacy policy (offline bo'lgani uchun oddiy bo'ladi)

## 7. Xavflar

| Xavf | Ehtimol | Yechim |
|---|---|---|
| Compose'da rich text tahriri xatoliklari (spanlar siljishi) | Yuqori | `SpanAdjuster` + unit testlar; zaxira: `compose-rich-editor` |
| PDF'da murakkab jadval va sahifa bo'linishi | O'rta | Jadval qatorlar bo'yicha sahifaga bo'linadi; bitta katak bo'linmaydi |
| DOCX'ning turli dasturlarda (Word, WPS, Google Docs) har xil ko'rinishi | O'rta | Minimal va standart OOXML; 3 dasturda tekshiriladi |
| Katta rasmlar sababli xotira (OOM) | O'rta | Saqlashda kichraytirish, Coil bilan subsampling |
| PIN unutilsa ma'lumotlar yo'qolishi | Past | Ogohlantirish + PIN yoqishdan oldin backup qilishni taklif qilish |

## 8. Hozircha kiritilmagan narsalar (keyingi versiyalar uchun)
- Bulut sinxronlash va bir nechta qurilma (internet talab qiladi)
- Mavjud DOCX/PDF faylni ilovaga import qilish
- Izohlar (footnote), havola va xatcho'plar
- Imlo tekshiruvi (tizim klaviaturasidagisi ishlaydi)
- Planshet uchun ikki panelli maket
