### Text shown on the "Deck Options" screen


## Top section

# Used in the deck configuration screen to show how many decks are used
# by a particular configuration group, eg "Group1 (used by 3 decks)"
deck-config-used-by-decks =
    { $decks ->
        [one] { $decks } ta dasta ishlatadi
       *[other] { $decks } ta dasta ishlatadi
    }
deck-config-default-name = Birlamchi
deck-config-title = Dasta parametrlari

## Daily limits section

deck-config-daily-limits = Kunlik limitlar
deck-config-new-limit-tooltip =
    Agar yangi kartalar mavjud boʻlsa, bir kunda joriy qilinadigan yangi kartalarning maksimal soni.
    Yangi materiallar sizning qisqa muddatli takrorlash ish yukingizni oshirishi sababli bu odatda
    takrorlash limitidan kamida 10 baravar kichikroq boʻlishi kerak.
deck-config-review-limit-tooltip =
    Agar kartalar takrorlash uchun tayyor boʻlsa,
    bir kunda koʻrsatiladigan takrorlanadigan kartalarining maksimal soni.
deck-config-limit-deck-v3 =
    Ichida boshqa dastalar boʻlgan dastani oʻrganayotganingizda, har bir ichki dastada oʻrnatilgan limit ushbu ichki dastadan toʻplangan kartalarning maksimal sonini belgilaydi.
    Tanlangan dastadagi limit jami koʻrsatiladigan kartalar sonini belgilaydi.
deck-config-limit-new-bound-by-reviews = Takrorlash limiti yangi kartalar limitiga taʼsir qiladi. Misol uchun, agar takrorlash limitingiz 200 ga oʻrnatilgan boʻlsa va navbatda 190 ta takrorlash kartalari boʻlsa, koʻpi bilan 10 ta yangi karta koʻrsatiladi. Takrorlash limitiga yetgan boʻlsangiz, yangi kartalar koʻrsatilmaydi.
deck-config-limit-interday-bound-by-reviews = Takrorlash limiti kunlararo oʻrganish kartalariga ham taʼsir qiladi. Limitni qoʻllashda avval kunlararo oʻrganish kartalari yigʻiladi, soʻngra takrorlash kartalari.
deck-config-tab-description =
    - `Andoza`: Limit ushbu andozadan foydalanadigan barcha dastalarga qoʻllaniladi.
    - `Bu dasta`: LImit faqat shu dastaga xosdir.
    - `Faqat bugun`: Ushbu dasta limitiga vaqtinchalik oʻzgartirish kiritish.
deck-config-new-cards-ignore-review-limit = Takrorlash limiti yangi kartalar limitiga taʼsir qilmaydi
deck-config-new-cards-ignore-review-limit-tooltip = Birlamchi sifatida, takrorlash limiti yangi kartalar uchun ham amal qiladi va takrorlash limitiga yetganida yangi kartalar koʻrsatilmaydi. Agar ushbu parametr yoqilgan boʻlsa, takrorlash limitidan qatʼi nazar, yangi kartalar koʻrsatilaveradi.
deck-config-apply-all-parent-limits = Limitlar yuqoridan boshlanadi
deck-config-apply-all-parent-limits-tooltip =
    Birlamchi sifatida, agar ichki dastadan oʻrganayotgan boʻlsangiz, uning bosh dasta kunlik limiti ichki dastaga qoʻllanilmaydi.
    Agar ushbu parametr yoqilgan boʻlsa, limitlar bosh dastadan boshlanadi. Bu dasta daraxti uchun umumiy karta limitini qoʻllagan holda xolis ichki dastalarni oʻrganmoqchi boʻlsangiz foydali boʻlishi mumkin.
deck-config-affects-entire-collection = Butun kolleksiyaga taʼsir qiladi.

## Daily limit tabs: please try to keep these as short as the English version,
## as longer text will not fit on small screens.

deck-config-shared-preset = Andoza
deck-config-deck-only = Bu dasta
deck-config-today-only = Faqat bugun

## New Cards section

deck-config-learning-steps = Oʻrganish bosqichlari
# Please don't translate `1m`, `2d`
-deck-config-delay-hint = Kechikishlar odatda daqiqalarda (masalan, `1m`) yoki kunlarda (masalan,`2d`) belgilanadi, lekin soatlarda (masalan,`1h`) yoki soniyalarda (masalan,`30s`) ham belgilash mumkin.
deck-config-learning-steps-tooltip =
    Boʻshliqlar bilan ajratilgan holda bir nechta kechikishlarni kiriting. Birinchi kechikish yangi kartada `Qaytadan` tugmasini bosganingizda qoʻllaniladi va bu birlamchi sifatida 1 daqiqa.
    `Yaxshi` tugmasi kartani keyingi bosqichga oʻtkazadi, bu birlamchi sifatida 10 daqiqa.
    Barcha bosqichlar bajarilgandan soʻng, karta takrorlash kartasiga aylanadi va boshqa kunda koʻrsatiladi. { -deck-config-delay-hint }
deck-config-graduating-interval-tooltip = Yakuniy oʻrganish bosqichida `Yaxshi` tugmasi bosilgach, kartani yana koʻrsatishdan oldin kutish kerak boʻlgan kunlar soni.
deck-config-easy-interval-tooltip = `Oson` tugmasini bosib karta oʻrganishdan olib tashlangandan keyin yana koʻrsatilishidan oldin kutiladigan kunlar soni.
deck-config-new-insertion-order = Kiritish tartibi
deck-config-new-insertion-order-tooltip =
    Yangi kartalar qoʻshilganda yangi kartalar oʻrnini (muddati #) belgilaydi.
    Oʻrganish paytida birinchi navbatda raqami pastroq kartalar koʻrsatiladi. Ushbu parametrni oʻzgartirish yangi kartalarning mavjud oʻrnini avtomatik ravishda yangilaydi.
deck-config-new-insertion-order-sequential = Ketma-ket (avval eng eski kartalar)
deck-config-new-insertion-order-random = Tasodifiy
deck-config-new-insertion-order-random-with-v3 = V3 rejalashtiruvchisi uchun ketma-ket tartibni qoldirgani maʼqul va uni oʻrniga yangi kartalarni yigʻish tartibini sozlash yaxshiroqdir.

## Lapses section

deck-config-relearning-steps = Qayta oʻrganish bosqichlari
deck-config-relearning-steps-tooltip = Boʻshliqlar bilan ajratilgan holda nol yoki undan ortiq kechikishlar kiriting. Birlamchi sifatida, takrorlash kartasidagi `Yana` tugmasini bosish uni 10 daqiqadan soʻng yana koʻrsatadi. Agar kechikishlar belgilanmasa, kartani qayta oʻrganishga kiritmasdan, uning intervali oʻzgartiriladi. { -deck-config-delay-hint }
deck-config-leech-threshold-tooltip = Takrorlash kartasini yopishqoq sifatida belgilanishidan oldin `Yana` tugmasini necha marta bosish kerak. Yopishqoq kartalar bu vaqtingizni koʻp oladigan kartalardir. Agar karta yopishqoq sifatida belgilangansa, uni eslab qolishingizga yordam berish uchun uni qaytadan tahrirlashingiz, oʻchirib tashlashingiz yoki mnemonika ishlatganingiz maʼqul.
# See actions-suspend-card and scheduling-tag-only for the wording
deck-config-leech-action-tooltip =
    `Faqat teg qoʻyish`: qaydga 'leech' tegini qoʻshadi va qalqib chiquvchi xabar koʻrsatadi.
    
    `Kartani toʻxtatish`: qaydga teg qoʻyishdan tashqari tashqari, kartani qoʻlda toʻxtatishdan chiqarmaguningizcha yashiradi.

## Burying section

deck-config-bury-title = Koʻmish
deck-config-bury-new-siblings = Yangilarga aloqadorlarni koʻmish
deck-config-bury-review-siblings = Takrorlanganlarga aloqadorlarni koʻmish
deck-config-bury-interday-learning-siblings = Aloqador kunlararo oʻrganilayotgan kartalarni koʻmish
deck-config-bury-new-tooltip = Bitta qaydning boshqa `yangi` kartalari (masalan, teskari kartalar, aloqador boʻshliqni toʻldirish kartalari) keyingi kunga qoldiriladimi.
deck-config-bury-review-tooltip = Bitta qaydning boshqa `takrorlash` kartalari keyingi kunga qoldiriladimi.
deck-config-bury-interday-learning-tooltip = Bitta qaydning boshqa, intervali 1 kundan ortiq boʻlgan, `oʻrganish` kartalari keyingi kunga qoldiriladimi.
deck-config-bury-priority-tooltip =
    Anki kartalarni yigʻganda, u birinchi navbatda kunlik kartalarini, soʻngra kunlararo kartalarini, soʻngra takrorlash kartalarini va nihoyat yangi kartalarni toʻplaydi. Bu koʻmish qanday ishlashiga taʼsir qiladi:
    
    - Agar sizda barcha koʻmish parametrlari yoqilgan boʻlsa, ushbu roʻyxatda keladigan eng birinchi aloqador karta ko'rsatiladi. Masalan, yangi kartadan koʻra takrorlash kartasi koʻrsatiladi.
    - Roʻyxatdagi keyingi aloqador kartalar oldingi karta turlarini koʻma olmaydi. Misol uchun, agar yangi kartalarni koʻmishni oʻchirib qoʻysangiz va yangi kartani oʻrgansangiz, u har qanday kunlararo oʻrganish yoki takrorlash kartalarini koʻmmaydi va bir seansda aloqador takrorlash va yangi kartalarni koʻrishingiz mumkin.

## Gather order and sort order of cards

deck-config-ordering-title = Koʻrsatish tartibi
deck-config-new-gather-priority = Yangi kartalarni yigʻish tartibi
deck-config-new-gather-priority-tooltip-2 =
    `Dasta`: yuqoridan boshlab tartib boʻyicha, har bir ichki dastadagi kartalarni yigʻadi. Har bir ichki dastadagi kartalar oʻrni oʻsib borish tartibida yigʻiladi. Agar tanlangan dasta kunlik limitiga yetsa, barcha ichki dastalar tekshirilmay qolib yigʻish toʻxtatilishi mumkin. Bu tartib katta kolleksiyalarda eng tez ishlaydi va yuqoriga yaqinroq boʻlgan dastalarga ustuvorlik berishga imkon beradi.
    
    `Oʻrni oʻsib borish tartibi boʻyicha`: kartalar oʻrni oʻsib borish tartibi boʻyicha toʻplanadi (muddati #), bu odatda eng avval qoʻshilgan kartalar.
    
    `Oʻrni kamayib borish tartibi boʻyicha`: kartalar oʻrni kamayib borish tartibi boʻyicha toʻplanadi (muddati #), bu odatda eng yanqinda qoʻshilgan kartalar.
    
    `Tasodifiy qaydlar`: qaydlarni tasodifiy ravishda tanlaydi, soʻngra barcha kartalarini yigʻadi.
    
    "Tasodifiy kartalar": kartalarni tasodifiy tartibda toʻplaydi.
deck-config-new-card-sort-order = Yangi kartalar saralash tartibi
deck-config-new-card-sort-order-tooltip-2 =
    `Karta turi, keyin yig'ish tartibi boʻyicha`: karta turi raqami boʻyicha kartalarni koʻrsatadi. Har bir karta turidagi kartalar ular yigʻilgan tartibda koʻrsatiladi. Agar aloqador kartalarni koʻmish funksiyasi oʻchirilgan bo‘lsa, bu barcha old→orqa kartalar orqa→old kartalardan oldin koʻrsatilishini taʼminlaydi. Bu bitta qaydning barcha kartalari, bir-biriga juda yaqin boʻlmagan holda, bir seansda koʻrsatilishi uchun qoʻl keladi.
    
    `Yigʻish tartibi`: kartalar qanday yigʻilgan boʻlsa, xuddi shunday tartibda koʻrsatiladi. Agar aloqador kartalarni koʻmish funksiyasi oʻchirilgan boʻlsa, bu odatda barcha qayd kartalari ketma-ket koʻrsatilishiga olib keladi.
    
    `Karta turi boʻyicha, soʻng tasodifiy`: kartalar karta turi raqami tartibida koʻrsatiladi. Har bir karta turidagi kartalar tasodifiy tartibda koʻrsatiladi. Agar aloqador kartalar bir-biriga juda yaqin koʻrsatilishini istamasangiz, lekin kartalar tasodifiy tartibda koʻrsatilishini xohlasangiz, bu tartib sizga qoʻl kelishi mumkin.
    
    `Tasodify qaydlar, keyin karta turi boʻyicha`: qaydlarni tasodifiy ravishda tanlaydi, soʻngra barcha kartalarini tartibi boʻyicha koʻrsatadi.
    
    `Tasodifiy`: kartalarni tasodifiy tartibda koʻrsatadi.
deck-config-new-review-priority = Yangi/takrorlash tartibi
deck-config-new-review-priority-tooltip = Takrorlash kartalariga nisbatan yangi kartalar qachon koʻrsatilishi kerak.
deck-config-interday-step-priority = Kunlararo oʻrganish/takrorlash kartalar tartibi
deck-config-interday-step-priority-tooltip =
    Bir kunlik chegarasidan kesib oʻtgan (qayta) oʻrganilayotgan kartalar qachon koʻrsatilishi kerak.
    
    Takrorlash limiti har doim birinchi navbatda kunlararo oʻrganish kartalariga, keyin esa takrorlash kartalariga qoʻllaniladi. Ushbu parametr yigʻilgan kartalarning koʻrsatilish tartibini nazorat qiladi, lekin kunlararo oʻrganish kartalari har doim birinchi boʻlib yigʻiladi.
deck-config-review-sort-order = Takrorlanadiganlarni saralash tartibi
deck-config-review-sort-order-tooltip = Birlamchi tartib eng uzoq navbatda turgan kartalarga ustuvorlik beradi, shuning uchun agar sizda yiigʻilib qolgan kartalar boʻlsa, eng uzoq navbatda turgan kartalar birinchi boʻlib koʻrsatiladi. Agar muddati oʻtgan kartalarga bir necha kundan koʻproq vaqt ketadigan boʻlsa yoki kartalarni ichki dasta tartibida koʻrishni istasangiz, muqobil tartiblash uslubi afzalroq boʻlishi mumkin.
deck-config-display-order-will-use-current-deck = Anki siz oʻrganish uchun tanlagan dastadagi koʻrsatish tartibidan foydalanadi va undagi har qanday ichki dastadan emas.

## Gather order and sort order of cards – Combobox entries

# Gather new cards ordered by deck.
deck-config-new-gather-priority-deck = Dasta
# Gather new cards ordered by deck, then ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-deck-then-random-notes = Dasta boʻyicha, soʻng tasodify qaydlar
# Gather new cards ordered by position number, ascending (lowest to highest).
deck-config-new-gather-priority-position-lowest-first = Oʻrni oʻsib borish tartibi boʻyicha
# Gather new cards ordered by position number, descending (highest to lowest).
deck-config-new-gather-priority-position-highest-first = Oʻrni kamayib borish tartibi boʻyicha
# Gather the cards ordered by random notes, ensuring all cards of the same note are grouped together.
deck-config-new-gather-priority-random-notes = Tasodifiy qaydlar
# Gather new cards randomly.
deck-config-new-gather-priority-random-cards = Tasodifiy kartalar
# Sort the cards first by their type, in ascending order (alphabetically), then randomized within each type.
deck-config-sort-order-card-template-then-random = Karta turi boʻyicha, soʻng tasodifiy
# Sort the notes first randomly, then the cards by their type, in ascending order (alphabetically), within each note.
deck-config-sort-order-random-note-then-template = Tasodify qaydlar, keyin karta turi boʻyicha
# Sort the cards randomly.
deck-config-sort-order-random = Tasodifiy
# Sort the cards first by their type, in ascending order (alphabetically), then by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-template-then-gather = Karta turi, keyin yig'ish tartibi boʻyicha
# Sort the cards by the order they were gathered, in ascending order (oldest to newest).
deck-config-sort-order-gather = Yigʻish tartibi
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-mix-with-reviews = Takrorlanadiganlar bilan aralashtirish
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-after-reviews = Takrorlashdan keyin koʻrsatish
# How new cards or interday learning cards are mixed with review cards.
deck-config-review-mix-show-before-reviews = Takrorlashdan oldin koʻrsatish
# Sort the cards first by due date, in ascending order (oldest due date to newest), then randomly within the same due date.
deck-config-sort-order-due-date-then-random = Muddati boʻyicha, soʻng tasodify
# Sort the cards first by due date, in ascending order (oldest due date to newest), then by deck within the same due date.
deck-config-sort-order-due-date-then-deck = Muddati boʻyicha, soʻng dasta boʻyicha
# Sort the cards first by deck, then by due date in ascending order (oldest due date to newest) within the same deck.
deck-config-sort-order-deck-then-due-date = Dasta boʻyicha, soʻng muddati boʻyicha
# Sort the cards by the interval, in ascending order (shortest to longest).
deck-config-sort-order-ascending-intervals = Interval oʻsib borish tartibi boʻyicha
# Sort the cards by the interval, in descending order (longest to shortest).
deck-config-sort-order-descending-intervals = Interval kamayib borish tartibi boʻyicha
# Sort the cards by ease, in ascending order (lowest to highest ease).
deck-config-sort-order-ascending-ease = Osonlik oʻsib borish tartibi boʻyicha
# Sort the cards by ease, in descending order (highest to lowest ease).
deck-config-sort-order-descending-ease = Osonlik kamayib borish tartibi boʻyicha
# Sort the cards by difficulty, in ascending order (easiest to hardest).
deck-config-sort-order-ascending-difficulty = Avval oson kartalar
# Sort the cards by difficulty, in descending order (hardest to easiest).
deck-config-sort-order-descending-difficulty = Avval qiyin kartalar
# Sort the cards by retrievability percentage, in ascending order (0% to 100%, least retrievable to most easily retrievable).
deck-config-sort-order-retrievability-ascending = Xotirlanarlik oʻsib borish tartibi boʻyicha
# Sort the cards by retrievability percentage, in descending order (100% to 0%, most easily retrievable to least retrievable).
deck-config-sort-order-retrievability-descending = Xotirlanarlik kamayib borish tartibi boʻyicha

## Timer section

deck-config-timer-title = Taymerlar
deck-config-maximum-answer-secs = Javob berish uchun eng koʻp vaqt (soniyalarda)
deck-config-maximum-answer-secs-tooltip = Bitta takrorlashni qayd etish uchun maksimal vaqt, soniyalarda. Agar javob bu vaqtdan oshib ketadigan boʻlsa (masalan, siz ekrandan uzoqlashganingiz sababli), qayd etilgan vaqt siz oʻrnatgan chegara sifatida yozib olinadi.
deck-config-show-answer-timer-tooltip = Oʻrganish ekranida har bir kartani oʻrganishga sarflagan vaqtingizni hisoblaydigan taymerni koʻrsatadi.
deck-config-stop-timer-on-answer = Javob berganda ekrandagi taymerni toʻxtatish
deck-config-stop-timer-on-answer-tooltip =
    Javob koʻrsatilganda ekrandagi taymerni toʻxtatilishi kerakmi.
    Statistikaga taʼsir qilmaydi.

## Auto Advance section

deck-config-seconds-to-show-question = Savolni koʻrsatilish vaqti (soniyalarda)
deck-config-seconds-to-show-question-tooltip-3 = Avtomatik ilgarilash yoqilganda, savol amalini qoʻllashdan oldin kutish kerak boʻlgan vaqt (soniyalarda). Oʻchirish uchun 0 ga oʻrnating.
deck-config-seconds-to-show-answer = Javobni koʻrsatish vaqti (soniyalarda)
deck-config-seconds-to-show-answer-tooltip-2 = Avtomatik ilgarilash yoqilganda, javob amalini qoʻllashdan oldin kutish kerak boʻlgan vaqt (soniyalarda). Oʻchirish uchun 0 ga oʻrnating.
deck-config-question-action-show-answer = Javobini koʻrish
deck-config-question-action-show-reminder = Eslatma koʻrsatish
deck-config-question-action = Savoldan keyingi amal
deck-config-question-action-tool-tip = Savol koʻrsatilgandan keyin va taymer tugagandan keyin bajariladigan amal.
deck-config-answer-action = Javobdan keyingi amal
deck-config-answer-action-tooltip-2 = Javob koʻrsatilgandan keyin va taymer tugagandan keyin bajariladigan amal.
deck-config-wait-for-audio-tooltip-2 = Savol yoki javobdan keyingi amalni avtomatik bajarishdan oldin audio tugashini kutib turish.

## Audio section

deck-config-audio-title = Audio
deck-config-disable-autoplay = Audio avtomatik tarzda ijro etilmasin
deck-config-disable-autoplay-tooltip =
    Yoqilganda, Anki audioni avtomatik tarzda ijro etmaydi.
    Audio belgisini bosish orqali yoki qayta ijro etish amali  orqali uni oʻzingiz qoʻlda ijro etishingiz mumkin.
deck-config-skip-question-when-replaying = Javob audiosi ijro etilganda savol audiosini oʻtkazib yuborish
deck-config-always-include-question-audio-tooltip = Kartaning javob tomoniga qaraganda qayta ijro etish amali qoʻllanilganda, savol audiosi ham ijro etilishi kerakmi.

## Advanced section

deck-config-advanced-title = Kengaytirilgan
deck-config-maximum-interval-tooltip =
    Takrorlash kartasi kutadigan maksimal kunlar soni. Takrorlashlar limitiga yetganida, `Qiyin`, `Yaxshi` va `Oson` hammasi bir xil kechikishni beradi.
    Buni qanchalik qisqa oʻrnatsangiz, ish yukingiz shunchalik koʻp boʻladi.
deck-config-starting-ease-tooltip = Yangi kartalar uchun belgilangan osonlik koʻpaytiruvchisi. Birlamchi sifatida, yangi oʻrganilgan kartadagi `Yaxshi` tugmasi keyingi takrorlashni oldingi kechikishdan 2,5 baravar ortiq kechiktiradi.
deck-config-easy-bonus-tooltip = `Oson` deb baholaganingizda takrorlash kartasi intervaliga qoʻllaniladigan qoʻshimcha koʻpaytiruvchi.
deck-config-interval-modifier-tooltip = Ushbu koʻpaytiruvchi barcha takrorlashlar uchun qoʻllaniladi. Anki rejalashtiruvchisini yanada konservativ yoki aggressiv qilish uchun kichik tuzatishlar qilish mumkin. Ushbu parametrni oʻzgartirishdan oldin qoʻllanmani koʻring.
deck-config-hard-interval-tooltip = `Qiyin` deb javob berganda takrorlash intervaliga qoʻllaniladigan koʻpaytiruvchi.
deck-config-new-interval-tooltip = `Qaytadan` deb javob berganda takrorlash intervaliga qoʻllaniladigan koʻpaytiruvchi.
deck-config-minimum-interval-tooltip = Takrorlash kartasiga `Qaytadan` deb javob berganda beriladigan minimal interval.
deck-config-custom-scheduling = Rejalashtirishni moslash
deck-config-custom-scheduling-tooltip = Butun kolleksiyaga taʼsir qiladi. Ishlatishda ehtiyot boʻling!

## Easy Days section.

deck-config-easy-days-title = Yengil kunlar
deck-config-easy-days-monday = Dushanba
deck-config-easy-days-tuesday = Seshanba
deck-config-easy-days-wednesday = Chorshanba
deck-config-easy-days-thursday = Payshanba
deck-config-easy-days-friday = Juma
deck-config-easy-days-saturday = Shanba
deck-config-easy-days-sunday = Yakshanba
deck-config-easy-days-normal = Normal
deck-config-easy-days-reduced = Kamaytirilgan
deck-config-easy-days-minimum = Minimum
deck-config-easy-days-no-normal-days = Kamida bir kun '{ deck-config-easy-days-normal }' deb sozlangan boʻlishi kerak.
deck-config-easy-days-change = Agar '{ deck-config-reschedule-cards-on-change }' yoqilmagan boʻlsa, mavjud takrorlashlar qayta rejalashtiriladi.

## Adding/renaming

deck-config-add-group = Andoza qoʻshish
deck-config-name-prompt = Nomi
deck-config-rename-group = Andoza nomini oʻzgartirish
deck-config-clone-group = Andozani nusxalash

## Removing

deck-config-remove-group = Andozani oʻchirish
deck-config-will-require-full-sync = Soʻralgan oʻzgarish bir tomonlama sinxronlashni talab etadi. Agar boshqa qurilmada oʻzgarishlar kiritgan boʻlsangiz va ularni hali bu qurilmaga sinxronlamagan boʻlsangiz, davom etishdan sinxronlang.
deck-config-confirm-remove-name = { $name } oʻchirilsinmi?

## Other Buttons

deck-config-save-button = Saqlash
deck-config-save-to-all-subdecks = Barcha ichki dastalarga saqlash
deck-config-save-and-optimize = Barcha andozalarni optimllashtirish
deck-config-revert-button-tooltip = Bu sozlama birlamchi qiymatiga qaytarilsinmi?

## These strings are shown via the Description button at the bottom of the
## overview screen.

deck-config-description-new-handling = Anki 2.1.41+ qoʻllash uslubi
deck-config-description-new-handling-hint =
    Kirishni markdown sifatida qabul qiladi va HTML kiritishni tozalaydi. Yoqilganda tabriklar ekranida tavsif ham koʻrsatiladi.
    Anki 2.1.40 va undan pastki versiyalarda Markdown matn sifatida koʻrsatiladi.

## Warnings shown to the user

deck-config-daily-limit-will-be-capped =
    { $cards ->
        [one] Ona dasta limiti { $cards } ta karta boʻlgani uchun uchun bu limitni almashtiradi.
       *[other] Ona dasta limiti { $cards } ta karta boʻlgani uchun uchun bu limitni almashtiradi.
    }
deck-config-reviews-too-low =
    { $cards ->
        [one] Agar har kuni { $cards } ta yangi karta qoʻshsangiz, takrorlash limitingiz kamida { $expected } boʻlishi kerak.
       *[other] Agar har kuni { $cards } ta yangi karta qoʻshsangiz, takrorlash limitingiz kamida { $expected } boʻlishi kerak.
    }
deck-config-learning-step-above-graduating-interval = Bitiruv intervali kamida oxirgi oʻrganish bosqichiga teng boʻlishi kerak.
deck-config-good-above-easy = Oson intervali kamida bitiruv intervaliga teng boʻlishi kerak.
deck-config-relearning-steps-above-minimum-interval = Eng kam unutishlar intervali hech boʻlmaganda oxirgi qayta oʻrganish bosqichiga teng boʻlishi kerak.
deck-config-maximum-answer-secs-above-recommended = Har bir savol qisqa boʻlganda, Anki takrorlashlarni yanada samaraliroq rejalashtira oladi.
deck-config-too-short-maximum-interval = 6 oydan kam boʻlgan maksimal interval tavsiya etilmaydi.
deck-config-ignore-before-info = (Taxminan) { $included }/{ $totalCards } kartalar FSRS parametrlarini optimallashtirish uchun ishlatiladi.

## Selecting a deck

deck-config-which-deck = Qaysi dasta uchun parametrlarni koʻrishni xohlaysiz?

## Messages related to the FSRS scheduler

deck-config-updating-cards = Kartalar yangilanmoqda: { $current_cards_count }/{ $total_cards_count }...
deck-config-invalid-parameters = Taqdim etilgan FSRS parametrlar yaroqsiz. Birlamchi parametrlardan foydalanish uchun ularni boʻsh qoldiring.
deck-config-not-enough-history = Bu amalni bajarish uchun takrorlashlar tarixi yetarli emas.
deck-config-must-have-400-reviews =
    { $count ->
        [one] Faqat { $count } ta takrorlash topildi. Ushbu amal uchun kamida 400 ta takrorlashga ega boʻlishingiz kerak.
       *[other] Faqat { $count } ta takrorlash topildi. Ushbu amal uchun kamida 400 ta takrorlashga ega boʻlishingiz kerak.
    }
# Numbers that control how aggressively the FSRS algorithm schedules cards
deck-config-weights = FSRS parametrlari
deck-config-compute-optimal-weights = FSRS parametrlarini optimallashtirish
deck-config-optimize-button = Joriy andozani optimallashtirish
# Indicates that a given function or label, provided via the "text" variable, operates slowly.
deck-config-slow-suffix = { $text } (sekin)
deck-config-compute-button = Hisoblash
deck-config-ignore-before = Shu sanadan oldin takrorlangan kartalar inobatga olinmasin
deck-config-time-to-optimize = Oxirgi optimallashtirishdan beri ancha vaqt oʻtdi - "Barcha andozalarni optimllashtirish" tugmasidan foydalanish tavsiya etiladi.
deck-config-evaluate-button = Hisoblash
deck-config-desired-retention = Siz istagan eslab qolish nisbati
deck-config-historical-retention = Oldingi eslab qolish nisbati
deck-config-smaller-is-better = Kichik qiymatlar algoritm sizning takrorlashlar tarixingizga yaxshiroq mos kelganini bildiradi.
deck-config-steps-too-large-for-fsrs = FSRS yoqilgan boʻlsa, 1 kun yoki undan ortiq bosqichlar tavsiya etilmaydi.
deck-config-get-params = Parametrlarni olish
deck-config-complete = { $num }% yakunlandi.
deck-config-iterations = Iteratsiya: { $count }...
deck-config-reschedule-cards-on-change = Kartalar oʻzgarganda qayta rejalashtirish
deck-config-fsrs-tooltip =
    Butun kolleksiyaga taʼsir qiladi.
    
    Free Spaced Repetition Scheduler (FSRS) Anki eski SuperMemo 2 (SM-2) algoritmiga muqobildir.
    Kartani unutish ehtimolini aniqroq belgilagash orqali, u oldingi bilan bir xil vaqt ichida koʻproq materialni eslab qolishingizga yordam beradi. Bu sozlama barcha andozalar tomonidan qoʻllaniladi.
deck-config-desired-retention-tooltip = Birlamchi sifatida, Anki kartalarni qayta takrorlash uchun kelganda ularni eslab qolish ehtimoli 90% boʻlish maqsadida ularni rejalashtiradi. Agar ushbu qiymatni oshirsangiz, Anki ularni eslab qolish ehtimolini oshirish uchun kartalarni tez-tez koʻrsatadi. Agar qiymatni kamaytirsangiz, Anki kartalarni kamroq koʻrsatadi va siz ularni koʻpini unutasiz. Buni oʻzgartirishda konservativ boʻling - yuqori qiymatlar sizning ish yukingizni sezilarli darajada oshiradi va pastroq qiymatlar koʻp materiallarni unutganingizda tushkunlikka tushurishi mumkin.
deck-config-desired-retention-tooltip2 = Axborot oynasida koʻrsatilgan ish yuki qiymatlari noaniq taxmindir. Koʻproq aniqlik uchun simulyatordan foydalaning.
deck-config-historical-retention-tooltip =
    Takrorlash tarixining bir qismi yoʻq boʻlsa, FSRS shu boʻshliqlarni toʻldirishi kerak. Birlamchi sifatida, siz eski takrorlashlarni qilganingizda, materialning 90 foizini eslab qoldingiz deb hisoblaydi. Agar sizning eski eslab qolish nisbatingiz sezilarli darajada  90% dan yuqori yoki past boʻlsa, ushbu parametrni oʻzgartirish FSRSga yetishmayotgan takrorlashlarni yaxshiroq taxmin qilishga yordam beradi.
    
    Takrorlashlar tarixi ikki sababga koʻra toʻliq boʻlmasligi mumkin:
    1. Chunki siz 'Shu sanadan oldin takrorlangan kartalar inobatga olinmasin' parametrini yoqgansiz.
    2. Joy boʻshatish uchun oldingi takrorlash jurnallarini oʻchirib tashlagansiz yoki boshqa SRS dasturidan material import qilgansiz.
    
    Ikkinchisi juda kam uchraydi, shuning uchun siz birinchi variantni ishlatmasangiz, ehtimol bu variantni sozlashingiz shart emas.
deck-config-weights-tooltip2 = FSRS parametrlari kartalar qanday rejalashtirilishiga taʼsir qiladi. Anki birlamchi parametrlar bilan boshlaydi. Ushbu andoza qoʻllanilgan dastalarda oʻrganish samaradorligingizga mos kelishi uchun parametrlarni optimallashtirish uchun quyidagi parametrni ishlatishingiz mumkin.
deck-config-reschedule-cards-on-change-tooltip =
    Butun kolleksiyaga taʼsir qiladi va saqlanmaydi.
    
    Ushbu parametr FSRS yoqilganda yoki parametrlar optimallashtirilganda kartalar muddati oʻzgarishini nazorat qiladi. Birlamchi sifatida kartalar qayta rejalashtirilmaydi: kelajakdagi takrorlashlar yangi rejalashtirishdan foydalanadi, ammo ish yukingiz darhol oʻzgarmaydi. Qayta rejalashtirish yoqilgan boʻlsa, kartalar muddatlari oʻzgaradi.
deck-config-reschedule-cards-warning =
    Siz istagan eslab qolish nisbatingizga qarab, bu koʻp sonli kartalar muddatli boʻlib qolishiga olib kelishi mumkin, shuning uchun SM-2 dan birinchi marotaba oʻtishda tavsiya etilmaydi.
    
    Ushbu parametrni kamdan-kam hollarda ishlating, chunki u har bir kartangizga takrorlash yozuvini qoʻshadi va kolleksiyangiz hajmini oshiradi.
deck-config-ignore-before-tooltip-2 =
    Agar yoqilsa, taqdim etilgan sanadan oldin takrorlangan kartalar FSRS parametrlarini optimallashtirishda eʼtiborga olinmaydi.
    Agar siz boshqa birovning rejalashtirish maʼlumotlarini import qilgan boʻlsangiz yoki javob tugmalaridan foydalanish uslubingizni oʻzgartirgan boʻlsangiz, bu sizga qoʻl kelishi mumkin.
deck-config-compute-optimal-weights-tooltip2 =
    Optimallashtirish tugmasini bosganingizda, FSRS takrorlash tarixini tahlil qilib xotirangiz va oʻrganayotgan kontentingiz uchun optimal parametrlarni yaratadi. Agar sizning dastalaringiz subʼyektiv qiyinchiligi jihatdan juda farqlansa, ularga alohida andoza belgilash tavsiya etiladi, chunki oson dastalar va qiyin dastalar uchun parametrlar turli xil boʻladi. Parametrlarni tez-tez optimallashtirishning hojati yoʻq - bir necha oyda bir marta qilsangiz yetarli.
    
    Birlamchi sifatida, parametrlar joriy andozadan foydalangan holda barcha dastalarni takrorlash tarixidan hisoblab chiqiladi. Parametrlarni optimallashtirish uchun qaysi kartalar ishlatilishini oʻzgartirmoqchi boʻlsangiz, parametrlarni hisoblashdan oldin ixtiyoriy ravishda qidiruvni sozlashingiz mumkin.
deck-config-please-save-your-changes-first = Avval oʻzgartirishlaringizni saqlang.
deck-config-workload-factor-change =
    Taxminiy ish yuki: { $factor }x
    ({ $previousDR }% eslab qolish nisbati bilan solishtirganda)
deck-config-workload-factor-unchanged = Bu qiymat qanchalik baland boʻlsa, kartalar shunchalik tez-tez koʻrsatiladi.
deck-config-desired-retention-too-low = Siz istagan eslab qolish nisbati juda past, va juda uzun intervallarga olib kelishi mumkin.
deck-config-desired-retention-too-high = Siz istagan eslab qolish nisbati juda yuqori, bu juda qisqa intervallarga olib kelishi mumkin.
deck-config-percent-of-reviews =
    { $reviews ->
        [one] { $reviews } ta takrorlashlardan { $pct }%
       *[other] { $reviews } ta takrorlashlardan { $pct }%
    }
deck-config-percent-input = { $pct }%
# This message appears during FSRS parameter optimization.
deck-config-checking-for-improvement = Yaxshilash uchun tekshirilmoqda...
deck-config-optimizing-preset = { $current_count }/{ $total_count } andoza optimallashtirilmoqda...
deck-config-fsrs-must-be-enabled = Avval FSRS yoqilishi kerak.
deck-config-fsrs-params-optimal = Joriy FSRS parametrlari optimal koʻrinadi.
deck-config-fsrs-params-no-reviews = Hech qanday takrorlash yozuvi topilmadi. Ushbu andoza siz optimallashtirmoqchi boʻlgan barcha dastalarga (shu jumladan ichki dastalarga ham) oʻrnatilganligiga ishonch hosil qiling va qaytadan urinib koʻring.
deck-config-wait-for-audio = Audioni kutish
deck-config-show-reminder = Eslatma koʻrsatish
deck-config-answer-again = Qaytadan deb javob berish
deck-config-answer-hard = Qiyin deb javob berish
deck-config-answer-good = Yaxshi deb javob berish
deck-config-days-to-simulate = Simuylatsiya uchun kunlar soni
deck-config-desired-retention-below-optimal = Siz istagan eslab qolish nisbati optimal qiymatdan past. Uni oshirish tavsiya etiladi.
# Description of the y axis in the FSRS simulation
# diagram (Deck options -> FSRS) showing the total number of
# cards that can be recalled or retrieved on a specific date.
deck-config-fsrs-simulator-experimental = FSRS simulyatori (eksperimental)
deck-config-fsrs-simulate-desired-retention-experimental = FSRS istalgan eslab qolish nisbati simulyatori (eksperimental)
deck-config-fsrs-simulate-save-preset = Optimizatsiyadan soʻng simulyatorni ishga tushurishdan oldin dasta andozasini saqlang.
deck-config-fsrs-desired-retention-help-me-decide-experimental = Qaror qabul qilishga yordam (eksperimental)
deck-config-additional-new-cards-to-simulate = Simulyatsiya uchun qoʻshimcha yangi kartalar
deck-config-simulate = Simyulatsiya qilish
deck-config-clear-last-simulate = Oxirgi simulyatsiyani tozalash
deck-config-fsrs-simulator-radio-count = Takrorlashlar
deck-config-advanced-settings = Kengaytirilgan sozlamalar
deck-config-smooth-graph = Tekis grafik
deck-config-suspend-leeches = Yopishqoqlarni toʻxtatish
deck-config-save-options-to-preset = Oʻzgarishlarni andozaga saqlash
deck-config-save-options-to-preset-confirm = Joriy andozadagi parametrlarni simulyatorda oʻrnatilgan parametrlar bilan qayta yozilsinmi?
# Radio button in the FSRS simulation diagram (Deck options -> FSRS) selecting
# to show the total number of cards that can be recalled or retrieved on a
# specific date.
deck-config-fsrs-simulator-radio-memorized = Yodlandi
deck-config-fsrs-simulator-radio-ratio = Vaqt/yodanganlar nisbati
# $time here is pre-formatted e.g. "10 Seconds" 
deck-config-fsrs-simulator-ratio-tooltip = Har bir kartani yodlashga { $time } sarflandi

## Messages related to the FSRS scheduler’s health check. The health check determines whether the correlation between FSRS predictions and your memory is good or bad. It can be optionally triggered as part of the "Optimize" function.

# Checkbox
deck-config-health-check = Optimallashtirishda sogʻliqni tekshirish
# Message box showing the result of the health check
deck-config-fsrs-bad-fit-warning =
    Sogʻliqni tekshirish:
    Sizning xotirangizni tahmin qilish FSRS uchun qiyin. Tavsiyalar:
    
    - Yopishqoq kartalarni toʻxtatib turish yoki qayta shakllantiring.
    - Javob tugmalaridan doimiy ravishda bir xil foydalaning. Yodda tutingki, "Qiyin" bu oʻtmaydigan baho emas, balki oʻtadigan bahodir.
    - Yodlashdan oldin materialni tushunib oling.
    
    Agar siz ushbu tavsiyalarga amal qilsangiz, keyingi bir necha oy ichida unumdorligingiz odatda yaxshilanadi.
# Message box showing the result of the health check
deck-config-fsrs-good-fit =
    Sogʻliqni tekshirish:
    FSRS xotirangizga yaxshi moslasha oladi.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

deck-config-unable-to-determine-desired-retention = Minimal tavsiya etilgan eslab qolish nisbatini aniqlab boʻlmadi.
deck-config-predicted-minimum-recommended-retention = Minimal tavsiya etilgan eslab qolish nisbati: { $num }
deck-config-compute-minimum-recommended-retention = Minimal tavsiya etilgan eslab qolish nisbati
deck-config-compute-optimal-retention-tooltip4 = Ushbu vosita eng kam vaqt ichida eng koʻp materialni oʻrganishga yordam beradigan eslab qolish nisbatini topishga harakat qiladi. Hisoblangan raqam kerakli eslab qolish nisbatini oʻrnatishni hal qilishda maʻlumotnoma sifatida xizmat qilishi mumkin. Agar bunga erishish uchun koʻproq oʻqish vaqtini sarflashga tayyor boʻlsangiz, yuqoriroq eslab qolish nisbatini tanlashingiz mumkin. Istalgan eslab qolish nisbatini minimal darajadan pastga sozlash tavsiya etilmaydi, chunki bu koʻp unutishlarga va ish yukining oshishiga olib keladi.
deck-config-plotted-on-x-axis = (X oʻqi boʻyicha chizildi)
deck-config-a-100-day-interval =
    { $days ->
        [one] 100 kunlik interval { $days } kunga aylanadi.
       *[other] 100 kunlik interval { $days } kunga aylanadi.
    }
deck-config-fsrs-simulator-y-axis-title-time = Takorlash vaqti/kun
deck-config-fsrs-simulator-y-axis-title-count = Takrorlashlar soni/kun
deck-config-fsrs-simulator-y-axis-title-memorized = Jami yodlandi
deck-config-bury-siblings = Aloqadorlarni koʻmish
deck-config-do-not-bury = Aloqadorlar koʻmilmasin
deck-config-bury-if-new = Yangi boʻlsa koʻmilsin
deck-config-bury-if-new-or-review = Yangi yoki takrorlash boʻlsa koʻmilsin
deck-config-bury-if-new-review-or-interday = Agar yangi, takrorlash yoki kunlararo oʻrganish boʻlsa koʻmilsin
deck-config-bury-tooltip =
    Siblings are other cards from the same note (eg forward/reverse cards, or
    other cloze deletions from the same text).
    
    When this option is off, multiple cards from the same note may be seen on the same
    day. When enabled, Anki will automatically *bury* siblings, hiding them until the next
    day. This option allows you to choose which kinds of cards may be buried when you answer
    one of their siblings.
    
    When using the V3 scheduler, interday learning cards can also be buried. Interday
    
    
    learning cards are cards with a current learning step of one or more days.
    GROUP COMMENT NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.
deck-config-seconds-to-show-question-tooltip =
    When auto advance is activated, the number of seconds to wait before revealing the answer. Set to 0 to disable.
    GROUP COMMENT NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.
deck-config-answer-action-tooltip =
    The action to perform on the current card before automatically advancing to the next one.
    GROUP COMMENT NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.
deck-config-wait-for-audio-tooltip = Javobni koʻrsatish yoki keyingi savolga oʻtishdan oldin audio tugashini kutish.
deck-config-ignore-before-tooltip =
    If set, reviews before the provided date will be ignored when optimizing & evaluating FSRS parameters.
    
    
    This can be useful if you imported someone else's scheduling data, or have changed the way you use the answer buttons.
    GROUP COMMENT NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.
deck-config-compute-optimal-retention-tooltip =
    This tool assumes you're starting with 0 cards, and will attempt to calculate the amount of material you'll
    be able to retain in the given time frame. The estimated retention will greatly depend on your inputs, and
    if it significantly differs from 0.9, it's a sign that the time you've allocated each day is either too low
    or too high for the amount of cards you're trying to learn. This number can be useful as a reference, but it
    
    
    is not recommended to copy it into the desired retention field.
    GROUP COMMENT NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.
deck-config-health-check-tooltip1 =
    This will show a warning if FSRS struggles to adapt to your memory.
    GROUP COMMENT NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.
deck-config-health-check-tooltip2 =
    Health check is performed only when using Optimize Current Preset.
    GROUP COMMENT NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.
deck-config-compute-optimal-retention = Minimal tavsiya etilgan eslab qolish nisbatini hisoblash
deck-config-predicted-optimal-retention = Minimal tavsiya etilgan eslab qolish nisbati: { $num }
deck-config-weights-tooltip =
    FSRS parameters affect how cards are scheduled. Anki will start with default parameters. Once
    you've accumulated 1000+ reviews, you can use the option below to optimize the parameters to best
    
    
    match your performance in decks using this preset.
    GROUP COMMENT NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.
deck-config-compute-optimal-weights-tooltip =
    Once you've done 1000+ reviews in Anki, you can use the Optimize button to analyze your review history,
    and automatically generate parameters that are optimal for your memory and the content you're studying.
    If you have decks that vary wildly in difficulty, it is recommended to assign them separate presets, as
    the parameters for easy decks and hard decks will be different. There is no need to optimize your parameters
    frequently - once every few months is sufficient.
    
    By default, parameters will be calculated from the review history of all decks using the current preset. You can
    optionally adjust the search before calculating the parameters, if you'd like to alter which cards are used for
    
    
    optimizing the parameters.
    GROUP COMMENT NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.
deck-config-compute-optimal-retention-tooltip2 =
    This tool assumes that you’re starting with 0 learned cards, and will attempt to find the desired retention
    value that will lead to the most material learnt, in the least amount of time. This number can be used as a
    reference when deciding what to set your desired retention to. You may wish to choose a higher desired retention,
    if you’re willing to trade more study time for a greater recall rate. Setting your desired retention lower than
    
    
    the minimum is not recommended, as it will lead to more work without benefit.
    GROUP COMMENT NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.
deck-config-compute-optimal-retention-tooltip3 =
    This tool assumes that you’re starting with 0 learned cards, and will attempt to find the desired retention value 
    that will lead to the most material learnt, in the least amount of time. To accurately simulate your learning process, 
    this feature requires a minimum of 400+ reviews. The calculated number can serve as a reference when deciding what to 
    set your desired retention to. You may wish to choose a higher desired retention, if you’re willing to trade more study 
    time for a greater recall rate. Setting your desired retention lower than the minimum is not recommended, as it will 
    
    
    lead to a higher workload, because of the high forgetting rate.
    GROUP COMMENT NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.
deck-config-seconds-to-show-question-tooltip-2 = Avtomatik ilgarilash yoqilgan boʻlsa, javobni koʻrsatishdan oldin kutish vaqti (soniyalarda). Oʻchirish uchun 0 ga sozlang.
deck-config-invalid-weights = Birlamchi parametrlardan foydalanish uchun parametrlar boʻsh qolishi yoki vergul bilan ajratilgan 17 raqamdan iborat boʻlishi kerak.
deck-config-fsrs-on-all-clients = Iltimos, barcha Anki mijozlaringiz Anki(Mobile) 23.10+ yoki AnkiDroid 2.17+ ekanligiga ishonch hosil qiling. Mijozlaringizdan birining versiyasi eskiroq boʻlsa, FSRS toʻgʻri ishlamaydi.
deck-config-optimize-all-tip = "Saqlash" yonidagi pastga ochiladigan tugmani bosib, barcha andozalarni bir vaqtning oʻzida optimallashtirishingiz mumkin.
