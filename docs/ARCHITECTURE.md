# Pixel Float — архітектура

Оверлей навколо вирізу камери, що показує вхідні сповіщення.
Особистий застосунок під один пристрій: Pixel 9 Pro, Android 17.

Документ описує структуру до написання коду. Він потребує підтвердження —
особливо розділ «Три рішення, що відхиляються від ТЗ».

---

## 1. Три рішення, що відхиляються від ТЗ

### 1.1 AccessibilityService стає основним хостом вікна, а не планом Б

У ТЗ: `TYPE_APPLICATION_OVERLAY` основний, accessibility — план Б для екрана блокування.

Перевірено в AOSP (`WindowManagerPolicy.getWindowLayerFromTypeLw`), z-шари:

| Вікно | Шар |
|---|---|
| `TYPE_APPLICATION_OVERLAY` | **11** |
| `TYPE_INPUT_METHOD` | 13 |
| `TYPE_STATUS_BAR` | **15** |
| `TYPE_NOTIFICATION_SHADE` (тут живе keyguard) | **17** |
| `TYPE_ACCESSIBILITY_OVERLAY` | **31** |

Наслідок ширший, ніж екран блокування: острівець стоїть саме в смузі статус-бара,
а статус-бар — шар 15. Тобто **системний годинник, батарея і значки мережі
малюватимуться поверх пігулок**, і шторка може перехоплювати дотики в цій смузі.
Це не крайній випадок, а щоденний — саме те, заради чого застосунок існує.

Шар 31 знімає всі три проблеми одразу: статус-бар, keyguard і клавіатуру.

Ціна: основним дозволом стає accessibility, а не «поверх інших застосунків».
Для APK зі збірки це означає ще й «Allow restricted settings» (див. 1.3).

`OverlayHost` — sealed interface, обидва шляхи реалізовані з першого дня,
перемикання — це значення в налаштуваннях, не рефакторинг.
**Це рішення потребує вашого підтвердження**, бо змінює модель дозволів.

### 1.2 Без Hilt

`@AndroidEntryPoint` на `NotificationListenerService` технічно працює (перевірено:
Hilt класифікує за `isAssignableFrom(Service)`, `ServiceGenerator` перекриває лише
`onCreate`, `onBind` не чіпає). Проблема не в цьому, а в Gradle-плагіні Hilt, який
ламався на кожній віхі AGP 9.

У застосунку ~6 об'єктів рівня процесу. Ручний `PixelFloatGraph` на `Application`
дає те саме, і не може зламати збірку, яку ви не можете відлагодити з телефона.
Якщо колись знадобиться Hilt — місця створення графа один-в-один лягають на `@Provides`.

Так само відкинуто `navigation-compose`: шість екранів у single-Activity — це
`sealed interface Screen` + `mutableStateOf` + `when`. Менше версій, які мусять збігатися.

### 1.3 «Allow restricted settings» — це крок онбордингу, а не примітка

APK, встановлений збоку (не з Play), впирається в «Обмежені налаштування»: перемикачі
доступу до сповіщень і accessibility **не працюють**, доки в «Про застосунок → ⋮ →
Дозволити обмежені налаштування» не зняти блок. Ви встановлюєте нову збірку постійно,
тож це перше, у що ви впретесь, і виглядатиме як зламаний застосунок.

`RestrictedSettings.kt` визначає стан і веде на потрібний екран; онбординг показує
цей крок **перед** обома дозволами.

---

## 2. Модульність: один модуль `:app`

Без `build-logic`, без convention-плагінів, без `:core`/`:feature`.

1. Немає локального середовища. Кожна помилка конфігурації Gradle = push + раунд CI
   з телефона. Межі модулів множать поверхню конфігурації (KSP, Compose-плагін,
   `compileSdk` на кожен модуль).
2. Межі модулів дають примус до дисципліни, але примушувати нема кого — розробник один.
3. Час CI визначається запуском JVM, KSP і Compose-компілятором, а не кількістю модулів.
   На ~90 файлах поділ на п'ять модулів робить холодну збірку **довшою**.
4. Природного шва немає: `:core:overlay` тягнув би за собою майже все.

Структуру тримає **пакет, а не Gradle-проєкт**, з односторонньою залежністю:

```
ui → overlay → notification → data → core
```

Єдиний виправданий модуль у майбутньому — `:baselineprofile` (і той потребує ПК).

---

## 3. Шари й власники

**L1 — платформа** (`service/`, `permission/`)
`PixelFloatListenerService` — джерело сповіщень, нуль логіки: читає ключ/прапорці/ranking
і штовхає в `NotificationBus`. `PixelFloatAccessibilityService` — **хост вікна**: єдине,
що вміє видати window context із токеном для `TYPE_ACCESSIBILITY_OVERLAY`.
`RebindReceiver` — `MY_PACKAGE_REPLACED` + `BOOT_COMPLETED` → статичний `requestRebind`.
`PackageChangeReceiver` — інвалідація кешу іконок і назв.

**L2 — дані** (`data/`)
`SettingsRepository` над типізованим `DataStore<AppSettings>` (kotlinx.serialization JSON).
`HistoryRepository` над Room — журнал лише на дозапис, обрізання при вставці.

**L3 — домен** (`notification/`, `icon/`)
Впорядкований ланцюг фільтрів, мапер `StatusBarNotification → IslandNotification`,
дедуплікатор, `IslandQueueController` (максимум 3, четверте витісняє найстаріше,
дедлайн на `elapsedRealtime`). `NotificationActions` — єдине місце, де викликається
`PendingIntent.send`, `RemoteInput.addResultsToIntent`, `cancelNotification`.
Тестується без пристрою.

**L4 — оверлей** (`overlay/`)
`OverlayCoordinator` вирішує, **чи** має існувати острівець зараз.
`OverlayWindowController` — єдиний, хто викликає `addView`/`updateViewLayout`/`removeViewImmediate`.
Увесь шар — головний потік, `@MainThread`.

**L5 — застосунок** (`ui/`, `theme/`)
`MainActivity` + шість екранів. Пише налаштування, читає історію.
Оверлея не торкається — два винятки, обидва названі:
`OverlayCoordinator.showPreview()` (калібрування) і `OverlayCoordinator.replay(item)` (історія).

### Потік даних

```
onNotificationPosted            [головний потік, ~мкс]
  → NotificationBus (SharedFlow, DROP_OLDEST 16)
  → пайплайн на Dispatchers.Default:
        settings.awaitLoaded()            ← не читати .value до першого читання файлу
        FilterChain → Verdict
        NotificationMapper → IslandNotification
        HistoryRepository.insertAndTrim   [Dispatchers.IO]
  → withContext(Main.immediate) → IslandQueueController.offer()
  → StateFlow<IslandState>
  → OverlayCoordinator.reconcile(...)     [ЗБИРАЄТЬСЯ НА ГОЛОВНОМУ ПОТОЦІ]
  → OverlayWindowController.show/update/hide
  → IslandMotionState                     [фаза малювання, 120 Гц]
```

Зворотний потік: `IslandGestures → IslandQueueController` (черга) і
`IslandGestures → NotificationActions` (платформні дії). Оверлей ніколи не лізе
в Room чи DataStore напряму.

### Хто чим володіє

| Що | Єдиний власник |
|---|---|
| `addView` / `updateViewLayout` / `removeViewImmediate` | `OverlayWindowController` |
| Константа типу вікна + window context | `OverlayHost` (sealed interface) |
| Літерали прапорців `LayoutParams` | `OverlayLayoutParams` |
| `PendingIntent.send`, опції BAL | `NotificationActions` |
| Черга і таймер показу | `IslandQueueController` |
| Запис налаштувань | `SettingsRepository.update { it.copy(...) }` |
| Константи пружин | `MotionSpecs` |
| Запис у Room | `HistoryRepository` на `AppScope` |

`OverlayConfigurationWatcher` та `ImeFocusCoordinator` **приймають рішення**, але
не тримають `WindowManager` — вони викликають вузькі точки входу контролера:
`applyParams { }`, `relayoutForConfiguration(geometry)`, `setFocusable(Boolean)`.

---

## 4. Вікно

### 4.1 Тип і хост — одне місце

```kotlin
sealed interface OverlayHost {
    val windowType: Int
    val windowContext: Context      // мусить бути створений саме під цей тип
    val windowManager: WindowManager
    val display: Display

    class Accessibility(svc: PixelFloatAccessibilityService) : OverlayHost  // основний
    class AppOverlay(app: Application) : OverlayHost                        // запасний
}
```

* Тип вікна не може бути просто `Int`: `createWindowContext(type)` і `LayoutParams.type`
  мусять збігатися, а `TYPE_ACCESSIBILITY_OVERLAY` вимагає контексту, створеного самим
  `AccessibilityService` (він несе токен з'єднання) — інакше `BadTokenException`.
* Дволанковий `createWindowContext(type, options)` кидає `UnsupportedOperationException`
  у сервісі. Потрібен трипараметричний `createWindowContext(display, type, null)`.
* Accessibility-контекст створюється **заново в `onServiceConnected`** і обнуляється в
  `onUnbind`; кешувати його в графі не можна — токен протухає.
* `OverlayHostRegistry` арбітрує: сервіси публікують себе в мапу `тип → хост`,
  реєстр обирає за `AppSettings.overlayWindowType`, з падінням на будь-який доступний.

### 4.2 LayoutParams — єдиний файл із прапорцями

```kotlin
type    = host.windowType
format  = PixelFormat.TRANSLUCENT
gravity = Gravity.TOP or Gravity.START
width   = MATCH_PARENT
height  = geometry.windowHeightPx        // обчислена, ніколи не MATCH_PARENT
flags   = FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_HARDWARE_ACCELERATED
layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
fitInsetsTypes = 0
softInputMode  = SOFT_INPUT_STATE_UNSPECIFIED or SOFT_INPUT_ADJUST_NOTHING
title = "PixelFloat:island"              // це бачить dumpsys window — єдиний спосіб
                                         // діагностики без adb
```

`FLAG_NOT_TOUCH_MODAL` виставляється **явно**, хоч `FLAG_NOT_FOCUSABLE` його й додає:
коли для inline reply ми знімаємо `NOT_FOCUSABLE`, неявний `NOT_TOUCH_MODAL` теж зникає —
і смуга вгорі починає ковтати **всі** дотики на екрані. Телефон виглядає завислим.

### 4.3 Геометрія — рахується один раз

`OverlayGeometry` — чиста функція `(OrientationProfile, WindowMetrics, Density)`.

```
windowHeightPx = ceil(max(expandedBlockHeight, pillHeight) + headroom)
headroom       = 0.18 × entranceTravelPx
```

0.18 — не на око. Жорсткість у Compose це ω_n² при одиничній масі, перше перевищення
дорівнює `exp(-πζ/√(1-ζ²))`; при ζ=0.55 це **12.6 %**. Вікно рівно по межах пігулок
обріже відскок появи — у Preview це не видно, на пристрої видно одразу.

Вікно **не змінює розмір** під час показу. `updateViewLayout` викликається лише:
поворот/зміна конфігурації, коміт калібрування, перемикання фокусу для відповіді.

### 4.4 Дотики

```kotlin
composeView.rootSurfaceControl?.setTouchableRegion(region)   // null = все вікно
```

* **Згорнутий стан:** об'єднання прямокутників двох пігулок; проміжок виключений,
  тож свайп там і далі відкриває шторку.
* **Розгорнутий:** `null`.
* **Схований:** вікно видалене повністю.

Область рахується **аналітично** з `OverlayGeometry` + поточних значень `IslandMotionState`,
а не через `onGloballyPositioned`: пігулки рухаються `graphicsLayer`-лямбдами у фазі
малювання, і колбек компонування просто не спрацює. Область застосовується **одразу після
`addView`** (координати згорнутого стану відомі заздалегідь), а не після осідання пружини —
інакше кожне сповіщення на третину секунди блокує верхню смугу.

Класичний `ViewTreeObserver.InternalInsetsInfo` + `TOUCHABLE_INSETS_REGION` — **неможливий**:
`@hide` і `@UnsupportedAppUsage(maxTargetSdk = R)`, заблокований при targetSdk 37.

Окремий ризик для запасного шляху: `TYPE_APPLICATION_OVERLAY` — **недовірене** вікно
(`InputMonitor.isTrustedOverlay` його не містить, а `TYPE_ACCESSIBILITY_OVERLAY` містить).
Недовірене вікно з непрозорістю понад поріг змушує систему **викидати** дотик, призначений
застосунку під ним. Тобто на запасному шляху проміжок може бути не просто непрозорим, а
мертвою зоною. Це вимірюється в етапі 1.

### 4.5 Порядок показу і приховування

Показ:
1. `OverlayCoordinator` перевіряє: хост прив'язаний; черга не порожня; екран увімкнений;
   не пригнічено; статус-бар видимий (або дозволено повноекранний режим); профіль орієнтації
   увімкнений; політика екрана блокування.
2. Новий `OverlayViewOwner`: `performAttach()` → `performRestore(null)` → `CREATED`.
3. Новий `IslandRootView` (FrameLayout) + `ComposeView`; виставити `ViewTreeLifecycleOwner`,
   `ViewTreeSavedStateRegistryOwner`, `ViewTreeOnBackPressedDispatcherOwner`;
   `DisposeOnLifecycleDestroyed`; `setContent { ... }` з початковими значеннями анімації.
4. `addView` — головний потік, у try/catch.
5. `currentState = RESUMED`. **`CREATED` недостатньо**: годинник кадрів Compose стартує
   на паузі й знімається з неї на `ON_START`. Зупинка на `CREATED` дає порожнє застигле
   вікно без жодного винятку в лозі — найважчий баг проєкту.
6. `root.post { touchRegion(); frameRate.high(); motion.enter() }`.

Приховування: `motion.exit()` → чекаємо осідання → `removeViewImmediate` (ніколи не
`removeView`: він асинхронний, і повторне додавання того ж екземпляра кидає
`IllegalStateException`) → `DESTROYED` → викинути owner і view. Не перевикористовувати
жодного: `LifecycleRegistry` після `DESTROYED` не піднімається назад.

Виняток: коли ховаємо через «тап відкриває застосунок», `contentIntent.send()` іде
**до** розбирання вікна — інакше зникає підстава BAL «застосунок має видиме вікно».

### 4.6 Поворот, пригнічення, immersive, клавіатура

* **Поворот:** `windowContext.registerComponentCallbacks(...)` плюс
  `DisplayManager.DisplayListener` для розворотів на 180°. Перечитати `currentWindowMetrics`,
  перерахувати виріз **за поточним поворотом** (`getBoundingRectTop()` у ландшафті
  порожній — отвір переїжджає на бічний край), обрати профіль, `updateViewLayout`.
  Ніколи не видаляти й додавати заново — це перезапустить пружину появи.
  Також звіряти `densityDpi` і `fontScale`: зміна розміру екрана інвалідовує кеш іконок.
* **Immersive:** рішення про показ — це **опитування** до створення вікна:
  `windowManager.currentWindowMetrics.windowInsets.isVisible(Type.statusBars())` працює
  з window context без жодного view. Слухач на корені потрібен лише щоб **сховати вже
  показаний** острівець; з дебаунсом ~400 мс, бо в immersive-sticky статус-бар
  проблискує і острівець блимав би цілий фільм.
* **Екран вимкнено:** окремий `DisplayStateMonitor`. Сповіщення при вимкненому екрані
  не створює вікно і не запускає дедлайн — елемент чекає в черзі до вмикання екрана.
  Інакше кожне нічне сповіщення програвало б анімацію в темряву й тихо зникало.
* **Пригнічення:** `onWindowVisibilityChanged` на `IslandRootView` — колбека для
  `HIDE_OVERLAY_WINDOWS` не існує, видимість це єдиний канал.
* **Inline reply:** вхід — виставити прапорці (повернувши `NOT_TOUCH_MODAL`) →
  `updateViewLayout` → `post` на наступний кадр → `requestFocus` → показати клавіатуру.
  Вихід — сховати клавіатуру → зняти фокус → дочекатись анімації вставок → повернути
  `FLAG_NOT_FOCUSABLE`. `FLAG_ALT_FOCUSABLE_IM` не замінює: вікно з `NOT_FOCUSABLE`
  не взаємодіє з клавіатурою за жодних прапорців.

---

## 5. Анімація

### 5.1 Один прогрес

| Значення | Тип | Що рухає |
|---|---|---|
| `leftDrop`, `rightDrop` | `Animatable<Float>` | поява, зі зсувом 35 мс |
| `expandProgress` | `Animatable<Float>` 0→1 | зближення, схлопування проміжку, радіус, альфа |
| `dismissX` | `AnchoredDraggableState` | свайп-приховування |

**Злиття — це одне значення.** Позиції пігулок, ширина проміжку і радіус — усе
`lerp(collapsed, expanded, expandProgress)`. Розсинхрон структурно неможливий.
Дві незалежні пружини існують лише в появі — там зсув і є ефектом.

```kotlin
val APPEAR   = spring<Float>(dampingRatio = 0.55f, stiffness = 400f)   // T≈314 мс, +12.6 %
val EXPAND   = spring<Float>(dampingRatio = 0.75f, stiffness = 800f)
val COLLAPSE = spring<Float>(dampingRatio = 0.95f, stiffness = 1200f)  // швидко, без відскоку
const val STAGGER_MS = 35L
```

Іменовані константи Compose тут не використовуються навмисне: `DampingRatioLowBouncy`
(0.75) **менш** пружний за `DampingRatioMediumBouncy` (0.5) — назви інвертовані відносно
чисел, і на цьому легко втратити день.

### 5.2 Дисципліна перенацілювання

```kotlin
scope.launch(start = CoroutineStart.UNDISPATCHED) {
    if (staggerMs > 0) delay(staggerMs)
    animatable.animateTo(target, spec)      // initialVelocity = жива швидкість
}
```

`UNDISPATCHED` обов'язковий. `animateTo` бере `initialVelocity = velocity` у точці
виклику, **до** того як `MutatorMutex` скасує поточну анімацію (а скасування занулює
швидкість). При звичайному `launch` гонка виграється прибиранням, пігулка застигає
й розганяється заново — і рух перестає бути «пікселівським». Ніколи не `snapTo`/`stop`
перед `animateTo` — вони теж занулюють швидкість.

Зсув 35 мс застосовується **лише** на переході «сховано → видно». На перенацілюванні
зсув нульовий: затримати пігулку в польоті = смикнути її.

**Область анімації.** `Animatable.animateTo` заходить у `withFrameNanos`, який вимагає
`MonotonicFrameClock` у контексті — звичайний `CoroutineScope(Dispatchers.Main)` кине
`IllegalStateException` на першому ж русі. `rememberCoroutineScope()` теж не підходить:
його скасовує `removeViewImmediate`, і `motion.exit()`, на який чекає приховування,
ніколи не завершиться. Тому: **одна область на показ**, створена контролером як
`AndroidUiDispatcher.CurrentThread + SupervisorJob() + MotionDurationScale`, де масштаб
читає `Settings.Global.ANIMATOR_DURATION_SCALE`. `IslandMotionState` живе стільки ж,
скільки вікно — інакше друге сповіщення стартує з осілих значень і появи просто не буде.

### 5.3 Малювання і вміст

* **Фон:** один `drawBehind`. Поки проміжок > 0.5 px — два `RoundRect`; після злиття —
  один. Об'єднання аналітичне (пігулки ділять верхній і нижній край), тож `Path.op(Union)`
  120 разів на секунду — марна робота.
* **Згорнутий вміст:** іконка й крапка позиціонуються трансляціями `graphicsLayer`,
  альфа 1→0 на прогресі 0.00–0.35. Місце правої пігулки **завжди зарезервоване**
  в розкладці, навіть коли вона нічого не малює.
* **Розгорнутий вміст:** компонується і **міряється один раз** на фінальному розмірі
  в момент початку розгортання, далі проявляється `graphicsLayer { alpha; clip; shape }`.
  Розкладка тексту — найдорожча операція Compose, її не можна пускати в покадровий шлях.
  Саме тому відкинуто `Modifier.animateBounds`/LookaheadScope: він анімує **справжні**
  межі розкладки щокадру й подвоює вимірювання.
* **Гортання черги:** розгорнутий блок фіксованої висоти (заголовок 1 рядок, текст 2,
  еліпсис), тож форма стрибнути не може. `AnimatedContent` з `contentKey` і напрямком.
  Без `SizeTransform` — ступінь свободи прибрано, а не анімовано.
* `androidx.graphics.shapes.Morph` для злиття **неможливий**: `RoundedPolygon` — один
  замкнений контур, дві роз'єднані пігулки ним не виражаються.

### 5.4 120 Гц і зменшений рух

`FrameRateController` виставляє `setRequestedFrameRate(CATEGORY_HIGH)` на час анімації
й `NO_PREFERENCE` у спокої: з Android 15 покадровий регулятор занижує частоту для малих
шарів, і без цього ви ловитимете «просідання», яких немає.
`preferredRefreshRate`/`preferredDisplayModeId` не чіпаємо — перемикання режиму блимає.

Compose сам поважає `ANIMATOR_DURATION_SCALE` через `MotionDurationScale`. Дві дірки
латаємо руками: `delay()` не масштабується (обнуляємо зсув), а тривалість показу
масштабувати не можна — людині все одно треба 3 секунди, щоб прочитати.

---

## 6. Дані

### 6.1 Налаштування

Один типізований `DataStore<AppSettings>` на kotlinx.serialization JSON.
Не Preferences (чотири профілі калібрування × дві пігулки × п'ять чисел = 40 плоских
ключів без атомарного знімка) і не Proto (protoc на кожній збірці CI + `.proto`, які
доведеться редагувати з телефона).

```kotlin
@Serializable data class AppSettings(
    val onboardingComplete: Boolean = false,
    val whitelist: Set<String> = emptySet(),
    val durationMillis: Long = 3_000,
    val iconStyle: IconStyle = IconStyle.COLOR,          // COLOR | MONOCHROME
    val rightPill: RightPillContent = RightPillContent.DOT,
    val colorMode: ColorMode = ColorMode.DYNAMIC,
    val manualBackgroundArgb: Int? = null,
    val manualContentArgb: Int? = null,
    val dismissGesture: DismissGesture = DismissGesture.LEFT,  // LEFT | RIGHT | DISABLED
    val landscapeEnabled: Boolean = true,
    val showOverFullscreenApps: Boolean = false,
    val lockscreenPolicy: LockscreenPolicy = LockscreenPolicy.REDACT,
    val overlayWindowType: OverlayWindowType = OverlayWindowType.ACCESSIBILITY,
    val portrait: OrientationProfile = OrientationProfile(),
    val landscape: OrientationProfile = OrientationProfile(),
)
```

Правила, без яких це ламається:
* DataStore створюється **виключно** з графа, ніколи делегатом `by dataStore` водночас —
  два екземпляри на один файл кидають виняток.
* Тільки `updateData { it.copy(...) }`, ніколи «прочитати `.value`, потім записати знімок».
* Під час перетягування в калібруванні **не писати щокадру** — геометрія живе в
  `StateFlow` в'ю-моделі, коміт на відпусканні або дебаунс 250 мс.
* Пайплайн сповіщень **чекає** перше читання файлу (`awaitLoaded()`), а не читає `.value`.
  Інакше перші десятки мілісекунд після старту процесу — саме коли після оновлення APK
  прилітає пачка сповіщень — діють дефолти з порожнім білим списком.
* Область DataStore — `Dispatchers.IO`, не `Default` (там крутиться пайплайн).

### 6.2 Історія

```kotlin
@Entity(tableName = "notification_history")
data class NotificationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationKey: String,     // індекс, НЕ первинний ключ
    val packageName: String,         // рятує «відкрити застосунок», коли intent мертвий
    val appLabel: String,
    val title: String?, val text: String?,
    val postedAt: Long, val seenAt: Long?,
    val category: String?, val hadActions: Boolean, val hadReply: Boolean,
)
```

* `sbn.key` не може бути первинним ключем: він перевикористовується на кожному оновленні
  того самого сповіщення (40 повідомлень у чаті схлопнулись би в один рядок) і містить uid,
  тобто змінюється після перевстановлення застосунку.
* Іконки в базі не зберігаються. Room перевидає весь список на кожній інвалідації Flow;
  500 рядків × 64 КБ = ~30 МБ на емісію — у застосунку, чия суть у 120 Гц.
* Утримання: `insertAndTrim` в одній транзакції, 500 записів / 30 днів, на кожній вставці.
  Без WorkManager.
* Міграцій не буде: `fallbackToDestructiveMigration`. Історія сповіщень — розхідний матеріал,
  а писати міграції з телефона — найгірше витрачений час у цьому проєкті.
* Окрема таблиця `app_cache` (`@Upsert` за пакетом) тримає назви й дає їх для вже
  видалених застосунків.

### 6.3 Черга

Чисто в пам'яті, `@Singleton`, головний потік. Максимум 3, четверте витісняє найстаріше.
Таймер — явний дедлайн на `SystemClock.elapsedRealtime()`, тому пауза зберігає **залишок**;
`collectLatest { delay(duration) }` натомість щоразу відлічував би повні 3 секунди наново.
Утримання паузи іменовані: `pressing`, `expanded`, `replying`, `animating`, `suppressed`.
Нічого з цього не персиститься — відновлювати острівець після смерті процесу означало б
воскрешати сповіщення, з якими ви вже розібрались.

---

## 7. Сповіщення

**Фільтри** — впорядкований список іменованих предикатів з sealed-вердиктом
(`Show`, `UpdateInPlace`, `DropNotWhitelisted`, `DropSummary`, `DropOngoing`, `DropMedia`,
`DropOtherUser`, `DropByLockscreenPolicy`). У debug-збірці вердикт пишеться в кільцевий
буфер — коли «WhatsApp не показався», один рядок дає відповідь.

Порядок: не в білому списку → інший користувач (робочий профіль) → зведення групи
(`FLAG_GROUP_SUMMARY`) → постійні (`FLAG_ONGOING_EVENT`, `FLAG_FOREGROUND_SERVICE`) →
категорії (`CATEGORY_TRANSPORT`/`SERVICE`/`PROGRESS`) → MediaStyle (порівняння рядка
`EXTRA_TEMPLATE`, бо `Notification.isStyle()` — `@hide`) → політика екрана блокування.

Системний фільтр (`FLAG_FILTER_TYPE_ONGOING`, `META_DATA_DEFAULT_FILTER_TYPES`,
`migrateNotificationFilter`) не використовується: `getNotificationType()` ніколи не
повертає ONGOING, тобто відфільтрувати постійні ним **неможливо**, а `migrateNotificationFilter`
одноразовий і має форму чорного, а не білого списку.

**Дедуплікація** — окремий компонент з юніт-тестами: `sbn.key` + `FLAG_ONLY_ALERT_ONCE` +
`Ranking.getLastAudiblyAlertedMillis()` + `EXTRA_REMOTE_INPUT_HISTORY` + хеш вмісту.
Без цього острівець смикатиметься на «X набирає повідомлення», на тиках прогресу і на
власній відповіді, яку застосунок публікує назад.

**Перепідключення:** `requestRebind` — **статичний** метод. Трюк із
`setComponentEnabledSetting(DISABLED/ENABLED)` **заборонений**: `trimApprovedListsForInvalidServices`
резолвить компонент без `MATCH_DISABLED_COMPONENTS` і може назавжди відкликати дозвіл.
Плюс перевірка в `MainActivity.onResume` — симетрично для обох сервісів.

**Дії:** `PendingIntent.isCanceled()` не існує — мертвий intent ловиться лише
`catch (CanceledException)` при відправленні, з падінням на `getLaunchIntentForPackage`.
Блокування BAL при цьому **не кидає винятку** — воно просто пише рядок у лог, тому
`send` іде з `OnFinished` і з `ActivityOptions.setPendingIntentBackgroundActivityStartMode(...)`,
а в debug вмикається `StrictMode.detectBlockedBackgroundActivityLaunch()`.

**Свайп** прибирає елемент із черги **завжди**; `cancelNotification(key)` викликається
додатково лише коли `sbn.isClearable()`. Інакше жест мовчки не працював би на частині
сповіщень.

---

## 8. Дерево файлів

```
pixel_float/
├─ .github/workflows/build.yml          # debug APK + rolling prerelease "latest"
├─ .github/workflows/checks.yml         # lint + юніт-тести
├─ settings.gradle.kts · build.gradle.kts · gradle.properties
├─ gradlew (mode 100755) · gradlew.bat
├─ gradle/libs.versions.toml
├─ gradle/wrapper/gradle-wrapper.jar    # офіційний, sha256 звірений
├─ gradle/wrapper/gradle-wrapper.properties
├─ keystore/debug.keystore              # ФІКСОВАНИЙ ключ, у git
├─ docs/ARCHITECTURE.md
├─ README.md                            # порядок встановлення з телефона
└─ app/
   ├─ build.gradle.kts · proguard-rules.pro · schemas/
   └─ src/
      ├─ test/java/dev/fand1l/pixelfloat/      # фільтри, дедуп, черга, геометрія, серіалізація
      └─ main/
         ├─ AndroidManifest.xml
         ├─ res/values/strings.xml · values-uk/strings.xml
         ├─ res/xml/accessibility_service_config.xml · locales_config.xml · backup_rules.xml
         └─ java/dev/fand1l/pixelfloat/
            ├─ PixelFloatApp.kt · PixelFloatGraph.kt
            ├─ core/          AppScope.kt · Dispatchers.kt · DebugLog.kt · CrashRecorder.kt
            ├─ service/       PixelFloatListenerService.kt
            │                 PixelFloatAccessibilityService.kt
            │                 NotificationBus.kt · ServiceConnectionState.kt
            │                 RebindReceiver.kt · PackageChangeReceiver.kt
            ├─ permission/    PermissionState.kt · NotificationAccess.kt
            │                 AccessibilityAccess.kt · OverlayAccess.kt · RestrictedSettings.kt
            ├─ notification/  model/{IslandNotification,IslandAction,IslandState}.kt
            │                 filter/{FilterVerdict,NotificationFilters,NotificationTemplates,
            │                         LockscreenPolicy}.kt
            │                 NotificationMapper.kt · NotificationDeduper.kt
            │                 NotificationPipeline.kt · IslandQueueController.kt
            │                 NotificationActions.kt · BackgroundStartOptions.kt
            ├─ icon/          AppIconRepository.kt
            ├─ data/settings/ AppSettings.kt · AppSettingsSerializer.kt · SettingsRepository.kt
            ├─ data/db/       PixelFloatDatabase.kt · NotificationHistoryEntity.kt
            │                 NotificationHistoryDao.kt · AppCacheEntity.kt · AppCacheDao.kt
            ├─ data/history/  HistoryRepository.kt · Retention.kt
            ├─ overlay/       OverlayCoordinator.kt · OverlayHost.kt · OverlayHostRegistry.kt
            │                 OverlayWindowController.kt · OverlayLayoutParams.kt
            │                 OverlayViewOwner.kt · IslandRootView.kt · OverlayGeometry.kt
            │                 CutoutGeometryProvider.kt · TouchRegionController.kt
            │                 OverlayConfigurationWatcher.kt · SystemBarVisibilityMonitor.kt
            │                 OverlaySuppressionMonitor.kt · DisplayStateMonitor.kt
            │                 KeyguardMonitor.kt · ImeFocusCoordinator.kt · FrameRateController.kt
            │   motion/       MotionSpecs.kt · IslandPhase.kt · IslandMotionState.kt
            │                 IslandShape.kt · MotionScale.kt
            │   gesture/      IslandGestures.kt · DismissAnchors.kt · InteractionHold.kt
            │   ui/           IslandRoot.kt · IslandBackground.kt · CollapsedContent.kt
            │                 ExpandedContent.kt · QueueContentSwap.kt · InlineReplyField.kt
            ├─ theme/         ColorSchemeProvider.kt · IslandTheme.kt · PixelFloatTheme.kt
            └─ ui/            MainActivity.kt · Screen.kt · ViewModelFactory.kt
                              components/{PermissionCard,SectionHeader,ColorPickerRow}.kt
                              onboarding/ · calibration/ · whitelist/ · settings/ ·
                              history/ · debug/
```

---

## 9. Збірка і CI

* Один модуль, `compileSdk`/`targetSdk` 37, `minSdk` 36. Образ раннера GitHub уже містить
  `platforms;android-37.0` — preview-SDK не потрібен. Якщо етап 0 покаже інакше — відкат
  на 36 це один рядок.
* AGP 9 має **вбудований Kotlin**: плагін `org.jetbrains.kotlin.android` більше не
  застосовується, блоку `kotlinOptions {}` не існує, kapt несумісний. Тільки KSP.
* Kotlin пінується під KSP, а не навпаки: свіжий KSP — 2.3.11, версії під Kotlin 2.4
  немає. Це прямо суперечить «беремо найновіше» і тому записано в каталог коментарем.
* Room: схема експортується аргументом KSP, **без** Gradle-плагіна Room — на один плагін
  менше на неперевіреній поверхні AGP 9.
* **Фіксований debug-ключ у репозиторії.** Без нього кожна збірка підписується новим
  ключем, Android відмовляє в оновленні поверх, і ви щоразу перевстановлюєте застосунок —
  втрачаючи базу, налаштування, калібрування **і обидва дозволи**. Ніколи не додавати
  `applicationIdSuffix` для debug: дозвіл слухача прив'язаний до `ComponentName`.
* Wrapper: офіційний `gradle-wrapper.jar` завантажується з репозиторію gradle/gradle,
  sha256 звіряється з опублікованим `wrapperChecksum`, комітиться разом із `gradlew`
  у режимі 100755 (+ захисний `chmod +x` у workflow).
* Кожна збірка публікується ще й у rolling prerelease `latest` — щоб установлення з
  телефона було в один тап. Потребує `permissions: contents: write`.
* `--stacktrace` завжди; при провалі — вивантаження `build/reports/**` артефактом.
  Лог Actions — це вся ваша діагностика.

**Діагностика без adb.** `READ_LOGS` на пристрої не видається, тому з етапу 0 існують:
кільцевий буфер логів у пам'яті, збереження останнього падіння у файл і debug-екран,
що це показує. Усі `catch` в оверлеї пишуть туди. Без цього етап 1 нефальсифікований:
«нічого не з'явилось» не відрізнити від «сервіс не прив'язався», «вікно не додалось»
і «геометрія за межами екрана».

Інструментальних тестів немає: запускати їх нема на чому (емулятор у CI — 15 хв і
нестабільність, adb немає). Замість них — юніт-тести в CI і **самоперевірки на
debug-екрані**: лічильник кадрів після показу (0 = класичний баг «зупинились на CREATED»)
і кількість неутилізованих `OverlayViewOwner` (>1 у схованому стані = витік).

---

## 10. План етапів

| Етап | Обсяг | Що доводить |
|---|---|---|
| **0. Каркас збірки** | Порожній застосунок: build-файли, wrapper, keystore, workflow, Compose+M3 `setContent`, одна сутність Room, одне читання DataStore, обидва сервіси **оголошені в маніфесті** (без логіки), `values-uk`, кільцевий буфер логів. | AGP 9 + Kotlin + KSP + Room + Compose збираються на раннері; APK ставиться з телефона **і оновлюється поверх** без видалення. |
| **1a. Вікно існує** | `TYPE_APPLICATION_OVERLAY` зі звичайного контексту, одна непрозора смуга 40dp, кнопка show/hide у `MainActivity`. Послідовність ViewTree-власників. | `addView` працює, Compose малює, життєвий цикл доведений до `RESUMED`. Один дозвіл, одне питання. |
| **1b. Вікно там, де треба** | `PixelFloatAccessibilityService` як хост, дві плоскі пігулки збоку від вирізу з `CutoutGeometryProvider`, `setTouchableRegion`, перемикач типу вікна, спостерігач повороту. | Позиція в портреті й ландшафті; чи малює статус-бар поверх; чи видно на екрані блокування; чи проходить свайп у проміжку; чи не з'їдаються дотики. **Go/no-go для розділу 1.1.** |
| **2. Пайплайн** | Слухач, шина, фільтри, мапер, дедуп, черга з таймером, білий список, іконки (моно з відкотом), `RebindReceiver`, debug-екран. Статичний острівець із реальним вмістом. | Показуються лише дозволені; постійні відсіюються і лог називає фільтр; черга 3 з витісненням; сервіс сам відновлюється після оновлення APK. |
| **3. Рух** | `IslandMotionState`, `MotionSpecs`, злиття через один прогрес, зсув появи, згортання, гортання, `FrameRateController`. | Пружини, а не тривалості; злиття без розсинхрону; форма не стрибає; нове сповіщення в польоті перенацілює зі збереженням швидкості; `animator_duration_scale 0` миттєво в кінцевий стан. |
| **4. Жести й дії** | Тап/повторний тап, вісь+пороги, свайпи, кнопки дій, inline reply з перемиканням фокусу. | Другий тап відкриває застосунок (BAL не блокує); свайп прибирає й скасовує лише коли можна; таймер паузиться і продовжує **залишок**; клавіатура з'являється і фокус повертається. |
| **5. Калібрування й кольори** | Екран калібрування з живим прев'ю (edge-to-edge, той самий `OverlayGeometry`), профілі орієнтацій, вимкнення ландшафту, динамічні/ручні кольори. | Перетягування без запису щокадру; підказка з правильного краю вирізу; поворот міняє профіль без перестворення вікна; зміна шпалер перефарбовує острівець. |
| **6. Історія, білий список, онбординг** | Історія (відкрити / повторити / видалити / очистити), пошук у білому списку, повні налаштування, онбординг з «Allow restricted settings», uk+en. | Історія переживає оновлення APK і відкриває застосунок через launcher, коли intent мертвий; свіже встановлення налаштовується цілком з телефона. |
| **7. Полірування** *(потребує ПК)* | AGSL-метабол за прапорцем, baseline profile, Perfetto. | Виміряно, а не на око. Baseline profile без ПК не згенерувати — етап позначений як опційний. |

Ваша вимога «перша частина — сервіс і вікно з примітивною пігулкою» — це етапи 1a+1b.
Етап 0 перед ними неминучий: без зеленої збірки немає APK, який можна поставити.
Він маленький і одноразовий.

---

## 11. Ризики

| Ризик | Наслідок | Що робимо |
|---|---|---|
| Accessibility-оверлей не бере фокус для клавіатури | Inline reply неможливий на основному шляху | Вимірюється в 1b. Відкат: перемикати тип вікна на час відповіді, або відповідь лише через дію застосунку |
| `setTouchableRegion` не шанується для обраного типу | Проміжок ковтає дотики | Вимірюється в 1b. Відкат уже спроєктований: двостанне вікно (рости до розгортання, стискатись після згортання) — булеве поле, не переписування |
| «Обмежені налаштування» блокують обидва дозволи | Виглядає як зламаний застосунок | Перший крок онбордингу + README |
| Room 3 / AGP 9 / KSP не збираються разом | Блокує все | Етап 0 саме про це; відкат на Room 2.8.4 механічний і коштує один раунд CI |
| BAL мовчки блокує «тап відкриває застосунок» | Головна взаємодія іноді не працює, без винятку | `send` до розбирання вікна, `OnFinished`, StrictMode в debug, запасний launcher intent |
| Регулятор частоти занижує оверлей до 60 Гц | Ключова вимога не виконана | `CATEGORY_HIGH` на час анімації; перевірка вимірюванням. Якщо це платформна межа — чесно задокументувати |
| Accessibility-сервіс не піднявся після оновлення APK | Сповіщення тихо зникають | Відсутність хоста >2 с при непорожній черзі — помітний стан в UI, а не мовчання |
| OTP-сповіщення приходять уже відредагованими | Коди 2FA не видно | Це поведінка Android 15+ для недовірених слухачів; обійти без ПК не можна — документуємо |
| Пружина появи обрізається краєм вікна | Виглядає «дешево», у Preview не видно | 18 % запасу висоти, виведено з ζ=0.55 |

---

## 12. Питання, на які потрібна ваша відповідь

1. **Accessibility як основний тип вікна** (розділ 1.1) — приймаємо, чи лишаємо
   `TYPE_APPLICATION_OVERLAY` основним і миримося з тим, що значки статус-бара
   малюються поверх острівця?
2. **Екран блокування** — показувати вміст повністю, редагувати (іконка + назва
   застосунку) чи не показувати взагалі? За замовчуванням закладено редагування
   з урахуванням `Notification.visibility`.
3. **Робочий профіль** — сповіщення робочого профілю показувати чи відсіювати?
   За замовчуванням відсіюємо (білий список ведеться лише за іменем пакета).
