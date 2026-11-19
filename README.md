<div align="center">

<a href="https://github.com/technicalnihilist-lgtm/ultimate-guard/stargazers"><img src="https://img.shields.io/badge/stars-2-5A31F4?style=for-the-badge&logo=github&logoColor=white" alt="stars: 2"></a>&nbsp;<a href="https://github.com/technicalnihilist-lgtm/ultimate-guard/network/members"><img src="https://img.shields.io/badge/forks-0-3FB950?style=for-the-badge" alt="forks: 0"></a>

<h3>Offline DNS filter for Android — real DNS packet engine, PIN lock, Device Owner app blocking</h3>

<img src="https://img.shields.io/badge/Kotlin-5865F2?style=flat-square" alt="Kotlin"> <img src="https://img.shields.io/badge/Zero%20trackers-3B3F4A?style=flat-square" alt="Zero trackers"> <img src="https://img.shields.io/badge/Open%20source-3FB950?style=flat-square" alt="Open source">

<a href="https://github.com/technicalnihilist-lgtm/ultimate-guard/releases/latest"><img src="https://img.shields.io/badge/DOWNLOAD-APK-6D5AE6?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"></a>

<b>Support the work</b>

<img src="https://img.shields.io/badge/Buy%20me%20a%20coffee-COMING%20SOON-4B5563?style=for-the-badge&logo=buymeacoffee&logoColor=white" alt="Buy me a coffee — coming soon"> <img src="https://img.shields.io/badge/UPI-COMING%20SOON-4B5563?style=for-the-badge&logo=googlepay&logoColor=white" alt="UPI — coming soon"> <img src="https://img.shields.io/badge/PayPal-COMING%20SOON-4B5563?style=for-the-badge&logo=paypal&logoColor=white" alt="PayPal — coming soon">

<sub>Donations aren't live yet — Paytm, UPI, PayPal and Buy&nbsp;Me&nbsp;a&nbsp;Coffee all show <b>coming soon</b>. Until then, a ⭐ star is the best support.</sub>

<a href="https://github.com/technicalnihilist-lgtm/ultimate-guard/releases/latest"><img src="https://img.shields.io/badge/VERSION-v1.0.0-6D5AE6?style=for-the-badge" alt="VERSION: v1.0.0"></a> <img src="https://img.shields.io/badge/DOWNLOADS-401-3FB950?style=for-the-badge" alt="DOWNLOADS: 401"> <a href="https://github.com/technicalnihilist-lgtm/ultimate-guard/releases"><img src="https://img.shields.io/badge/RELEASES-GITHUB-EC4899?style=for-the-badge&logo=github&logoColor=white" alt="RELEASES: GITHUB"></a>

<h4>Get it from</h4>

<a href="https://github.com/technicalnihilist-lgtm/ultimate-guard/releases"><img src="https://img.shields.io/badge/GitHub-Releases-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub Releases"></a> <a href="https://github.com/technicalnihilist-lgtm/ultimate-guard/archive/refs/heads/main.zip"><img src="https://img.shields.io/badge/Source-ZIP-0EA5E9?style=for-the-badge&logo=github&logoColor=white" alt="Source ZIP"></a>

</div>

---

---

<img src="https://raw.githubusercontent.com/technicalnihilist-lgtm/ultimate-guard/main/docs/icon.png" width="84" align="right" alt="Ultimate Guard icon">

## What actually works

- **A real DNS filtering engine.** A local `VpnService` tunnel parses IPv4 and
  IPv6 packets, decodes DNS queries, and either answers them itself (blocked
  names get `0.0.0.0` / `::`) or forwards them upstream and wraps the reply
  back into a correct IP/UDP packet with recomputed checksums.
- **Subdomain matching.** Blocking `doubleclick.net` blocks `ad.g.doubleclick.net`
  too, via a parent-domain walk, not a substring match.
- **Hardcoded-DNS interception.** The well-known public resolver addresses are
  routed into the tunnel, so an app that talks to `8.8.8.8` directly is still
  filtered (and its DoH/DoT attempts to those addresses are dropped).
- **3 blocklists + your own.** 711 ad domains, 162 tracker domains and 107
  adult domains are bundled (957 unique after de-duplication); add your own domains, an allowlist, or import any
  hosts-file / Adblock-style text file.
- **App blocking via Device Owner.** Selected apps are suspended with
  `DevicePolicyManager`, re-applied on boot and when a package is reinstalled.
- **PIN lock.** The whole UI is behind a salted PBKDF2 PIN, so the block
  settings cannot be changed by someone else holding the phone.
- **Live stats and a rolling activity log**, kept on the device.

## Install

```
adb install -r UltimateGuard-1.0.0.apk
```

Or copy the APK to the phone and tap it (allow "install unknown apps" for your
file manager first). Then:

1. Open the app and set a PIN.
2. Tap **Turn on protection** and accept Android's VPN prompt.
3. Optional: Settings → Network → VPN → Ultimate Guard → enable **Always-on VPN**
   so filtering survives reboots and app kills.

## Optional: Device Owner (for app blocking)

Android only lets an app suspend other apps if it is Device Owner, which
requires a device with no accounts, set up fresh:

```
adb shell dpm set-device-owner com.ultimateguard/.GuardAdminReceiver
```

Use a spare phone or an emulator. Removing Device Owner requires a factory
reset. Without Device Owner the app still filters DNS normally; app blocks are
stored and applied the moment ownership is granted.

## How it works

```
 app  ──DNS query──▶  10.111.222.2:53 (inside the tunnel)
                              │
                    FilteringVpnService
                              │
                 ┌────────────┴────────────┐
         name is blocked            name is allowed
                 │                         │
      answer 0.0.0.0 / ::        forward to upstream resolver
      (no network round trip)     (Cloudflare / Google / Quad9 / custom)
```

- The tunnel carries DNS only, plus the public resolver `/32`s when
  interception is on. All other traffic keeps using the normal network path,
  so there is no full-tunnel battery cost.
- Blocked `A` queries get `0.0.0.0`, `AAAA` gets `::`, other types get a
  NOERROR/NODATA answer — the client fails fast instead of retrying other
  resolvers.
- Upstream lookups run on a thread pool with a 3 s timeout, a fallback
  resolver, and a 30 s response cache.

## Layout

```
app/src/main/java/com/ultimateguard/
  core/DnsPacket.kt      DNS message parse/build (pure JVM, unit-tested)
  core/IpPacket.kt       IPv4/IPv6 + UDP parse/build, checksums (pure JVM)
  core/DomainMatcher.kt  suffix matching + whitelist (pure JVM)
  core/DnsEngine.kt      TUN read loop, block/forward decisions, cache
  core/BlocklistRepository.kt   assets + user lists + import
  core/Admin.kt          DevicePolicyManager wrapper (suspend, uninstall block)
  core/BlockStore.kt     append-only registry of blocked packages
  core/Prefs.kt, PinStore.kt, GuardState.kt, RecentLog.kt, AppInventory.kt
  FilteringVpnService.kt foreground VPN service
  MainActivity.kt        tabs + PIN gate
  ui/                    the four screens
```

## Tests

The packet engine and matcher are pure JVM code and are tested without a device:

```
./tools/run-tests.sh     # engine assertions + blocklist sanity checks
```

## Build from source

**Android Studio** — open the folder, JDK 17, AGP 8.7.3 / Kotlin 2.0.21, build
the `app` module.

**Command line** (`build.sh`, used to produce the APK here) — needs JDK 17, a
Kotlin compiler and Android SDK 35 in `PATH`/`ANDROID_HOME`; runs aapt2 → javac
(R.java) → kotlinc → d8 → zipalign → apksigner.

## Limits — read these

- **DNS is not a firewall.** An app that uses its own encrypted DNS (DoH/DoT)
  pointed at a server this app does not intercept can still resolve names.
  Blocking every DoH server would require a full-tunnel TCP relay.
- Ads served from the same domain as the content, and ads baked into app
  assets, cannot be blocked at the DNS level.
- Only one VPN can be active at a time; another VPN app will take the tunnel.
- The bundled lists are curated starter lists, not exhaustive ones. Import a
  bigger list you trust if you need broader coverage.
- App suspension needs Device Owner, and some system packages cannot be
  suspended. Never block Settings, System UI, Phone, or the installer.
- Nothing here survives a factory reset, and someone with recovery or ADB
  access can bypass it. This is a self-control and family-safety tool, not
  tamper-proof lockdown.

---

## 🌍 Languages

<sub>Donations are <b>coming soon</b> in every language below — nothing to pay today, nothing to click.</sub>

<div align="center">

<sub><b>हिन्दी · বাংলা · मराठी · తెలుగు · தமிழ் · ગુજરાતી · ಕನ್ನಡ · മലയാളം · ਪੰਜਾਬੀ · ଓଡ଼ିଆ · অসমীয়া · اردو</b></sub>

</div>

<details>
<summary><b>🌍 Open the same message in 53 languages</b></summary>

**🇮🇳 Indian languages**

- **हिन्दी** (Hindi) — ☕ जल्द ही आ रहा है। · ⭐ अगर आपको यह ऐप और हमारा काम पसंद है, तो कृपया रिपॉजिटरी को स्टार दें और हमें फॉलो करें।
- **বাংলা** (Bengali) — ☕ খুব শিগগিরই আসছে। · ⭐ যদি আপনার এই অ্যাপ এবং আমাদের কাজ ভালো লাগে, তবে অনুগ্রহ করে রিপোজিটরিটিকে একটি স্টার দিন এবং আমাদের ফলো করুন।
- **मराठी** (Marathi) — ☕ लवकरच येत आहे. · ⭐ जर तुम्हाला हे ॲप आणि आमचं काम आवडलं असेल, तर कृपया रिपॉझिटरीला स्टार द्या आणि आम्हाला फॉलो करा.
- **తెలుగు** (Telugu) — ☕ త్వరలో రాబోతోంది. · ⭐ మీకు ఈ యాప్, మా పని నచ్చితే, దయచేసి రిపోజిటరీకి ఒక స్టార్ ఇచ్చి మమ్మల్ని ఫాలో అవ్వండి.
- **தமிழ்** (Tamil) — ☕ விரைவில் வருகிறது. · ⭐ உங்களுக்கு இந்த ஆப்பும் எங்க வேலையும் பிடிச்சிருந்தா, தயவுசெஞ்சு இந்த ரெபாசிட்டரிக்கு ஒரு ஸ்டார்…
- **ગુજરાતી** (Gujarati) — ☕ ટૂંક સમયમાં આવી રહ્યું છે. · ⭐ જો તમને આ એપ અને અમારું કામ ગમતું હોય, તો પ્લીઝ રિપોઝિટરીને સ્ટાર આપો અને અમને ફોલો કરો.
- **ಕನ್ನಡ** (Kannada) — ☕ ಶೀಘ್ರದಲ್ಲೇ ಬರುತ್ತಿದೆ. · ⭐ ನಿಮಗೆ ಈ ಆ್ಯಪ್ ಮತ್ತು ನಮ್ಮ ಕೆಲಸ ಇಷ್ಟವಾದರೆ, ದಯವಿಟ್ಟು ರೆಪೊಸಿಟರಿಗೆ ಸ್ಟಾರ್ ಕೊಡಿ ಮತ್ತು ನಮ್ಮನ್ನು ಫಾಲೋ ಮಾಡಿ.
- **മലയാളം** (Malayalam) — ☕ ഉടൻ വരുന്നു. · ⭐ നിങ്ങൾക്ക് ഈ ആപ്പും ഞങ്ങളുടെ പ്രവർത്തനവും ഇഷ്ടപ്പെട്ടെങ്കിൽ, ദയവായി റെപ്പോസിറ്ററിക്ക് ഒരു സ്റ്റാർ…
- **ਪੰਜਾਬੀ** (Punjabi) — ☕ ਜਲਦੀ ਆ ਰਿਹਾ ਹੈ। · ⭐ ਜੇ ਤੁਹਾਨੂੰ ਇਹ ਐਪ ਅਤੇ ਸਾਡਾ ਕੰਮ ਪਸੰਦ ਹੈ, ਤਾਂ ਕਿਰਪਾ ਕਰਕੇ ਰਿਪੋਜ਼ਟਰੀ ਨੂੰ ਸਟਾਰ ਦਿਓ ਅਤੇ ਸਾਨੂੰ ਫਾਲੋ ਕਰੋ।
- **ଓଡ଼ିଆ** (Odia) — ☕ ଖୁବ୍ ଶୀଘ୍ର ଆସୁଛି। · ⭐ ଯଦି ଆପଣଙ୍କୁ ଏହି ଆପ୍ ଏବଂ ଆମର କାମ ଭଲ ଲାଗୁଛି, ତେବେ ଦୟାକରି ରିପୋଜିଟୋରୀକୁ ଗୋଟିଏ ଷ୍ଟାର୍ ଦିଅନ୍ତୁ ଏବଂ ଆମକୁ ଫଲୋ କରନ୍ତୁ।
- **অসমীয়া** (Assamese) — ☕ অতি সোনকালেই আহি আছে। · ⭐ যদি আপুনি এই এপটো আৰু আমাৰ কাম ভাল পায়, তেন্তে অনুগ্ৰহ কৰি ৰিপজিটৰীটোক এটা ষ্টাৰ দিয়ক আৰু আমাক ফ'ল' কৰক।
- **اردو** (Urdu) — ☕ جلد آرہا ہے۔ · ⭐ اگر آپ کو یہ ایپ اور ہمارا کام پسند آئے تو براہ کرم ریپازٹری کو سٹار دیں اور ہمیں فالو کریں۔
- **संस्कृतम्** (Sanskrit) — ☕ शीघ्रमेव आगच्छति।
- **मैथिली** (Maithili) — ☕ जल्दिये आबि रहल अछि।
- **कोंकणी** (Konkani) — ☕ रोखडेंच येता.
- **کٲشُر** (Kashmiri) — ☕ جَلدی یِوان چھُ۔
- **سنڌي** (Sindhi) — ☕ जल्द अची रह्यो आहे।
- **डोगरी** (Dogri) — ☕ जल्दी आ करदा ऐ।
- **ꯃꯤꯇꯩ ꯂꯣꯟ** (Manipuri) — ☕ ꯑꯊꯨꯕꯥ ꯃꯇꯝꯗꯥ ꯂꯥꯛꯀꯅꯤ꯫
- **बर’** (Bodo) — ☕ थाबैनो फैगोन।
- **ᱥᱟᱱᱛᱟᱲᱤ** (Santali) — ☕ ᱞᱚᱜᱚᱱ ᱦᱤᱡᱩᱜ ᱠᱟᱱᱟ᱾
- **नेपाली** (Nepali) — ☕ छिट्टै आउँदैछ। · ⭐ यदि तपाईंलाई यो एप र हाम्रो काम मन पर्‍यो भने, कृपया रिपोजिटरीलाई स्टार दिनुहोस् र हामीलाई फलो गर्नुहोस्।

**🌍 World languages**

- **العربية** (Arabic) — ☕ قريبًا. · ⭐ إذا أعجبك هذا التطبيق وعملنا، فيرجى منح المستودع نجمة ومتابعتنا.
- **简体中文** (Chinese (Simplified)) — ☕ 即将推出。
- **繁體中文** (Chinese (Traditional)) — ☕ 即將推出。
- **Čeština** (Czech) — ☕ Již brzy.
- **Dansk** (Danish) — ☕ Kommer snart.
- **Nederlands** (Dutch) — ☕ Binnenkort beschikbaar. · ⭐ Vind je deze app en ons werk leuk?
- **Filipino** (Filipino) — ☕ Malapit na.
- **Suomi** (Finnish) — ☕ Tulossa pian.
- **Français** (French) — ☕ Bientôt disponible. · ⭐ Si cette application et notre travail vous plaisent, mettez une étoile au dépôt et suivez-nous.
- **Deutsch** (German) — ☕ Bald verfügbar. · ⭐ Wenn dir diese App und unsere Arbeit gefallen, gib dem Repository einen Stern und folge uns.
- **Ελληνικά** (Greek) — ☕ Έρχεται σύντομα.
- **עברית** (Hebrew) — ☕ בקרוב.
- **Magyar** (Hungarian) — ☕ Hamarosan.
- **Bahasa Indonesia** (Indonesian) — ☕ Segera hadir. · ⭐ Jika kamu suka aplikasi ini dan karya kami, beri bintang pada repositori ini dan ikuti kami.
- **Italiano** (Italian) — ☕ In arrivo.
- **日本語** (Japanese) — ☕ 近日公開。 · ⭐ このアプリと私たちの仕事が気に入ったら、リポジトリにスターを付けてフォローしてください。
- **한국어** (Korean) — ☕ 곧 공개됩니다. · ⭐ 이 앱과 저희 작업이 마음에 드신다면 저장소에 스타를 눌러 주시고 팔로우해 주세요.
- **Bahasa Melayu** (Malay) — ☕ Akan datang.
- **Norsk** (Norwegian) — ☕ Kommer snart.
- **فارسی** (Persian) — ☕ به‌زودی.
- **Polski** (Polish) — ☕ Wkrótce. · ⭐ Jeśli podoba Ci się ta aplikacja i nasza praca, daj repozytorium gwiazdkę i obserwuj nas.
- **Português** (Portuguese) — ☕ Em breve.
- **Română** (Romanian) — ☕ În curând.
- **Русский** (Russian) — ☕ Скоро. · ⭐ Если вам нравится это приложение и наша работа, поставьте звёздочку репозиторию и подпишитесь на нас.
- **Español** (Spanish) — ☕ Muy pronto. · ⭐ Si te gusta esta app y nuestro trabajo, dale una estrella al repositorio y síguenos.
- **Kiswahili** (Swahili) — ☕ Inakuja hivi karibuni.
- **Svenska** (Swedish) — ☕ Kommer snart.
- **ไทย** (Thai) — ☕ เร็ว ๆ นี้
- **Türkçe** (Turkish) — ☕ Çok yakında.
- **Українська** (Ukrainian) — ☕ Незабаром.
- **Tiếng Việt** (Vietnamese) — ☕ Sắp ra mắt. · ⭐ Nếu bạn thích ứng dụng này và công việc của chúng tôi, hãy tặng kho lưu trữ một ngôi sao và theo…

</details>

