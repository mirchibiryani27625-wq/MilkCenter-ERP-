# MilkCenter ERP — Namaste India Dairy Manager

**MilkCenter ERP (Namaste India Dairy Manager)** एक आधुनिक Android (Kotlin + Jetpack Compose + Material 3) डेयरी कलेक्शन और प्रबंधन ERP ऐप है, जिसे **Firebase (`milkcenter-erp`)** और **Room Offline-First Cache** के साथ बनाया गया है।

---

## मुख्य विशेषताएँ (Key Features)

- **दूध संग्रहण एवं ऑटो दर गणना (Milk Collection & Rate Engine):** गाय (COW) और भैंस (BUFFALO) के दूध के लिए FAT, SNF, Water % और CLR के आधार पर सटीक दर गणना।
- **AI Smart Camera Scanner:** CameraX + ML Kit OCR + Firebase AI Logic की मदद से मिल्क एनालाइज़र / वज़न मशीन की स्क्रीन से ऑटोमैटिक डेटा स्कैन।
- **किसान प्रबंधन (Farmer Management):** किसान प्रोफ़ाइल, मोबाइल नंबर, गाँव का पता और बैंक खाता विवरण (IFSC/Account)।
- **शिफ्ट प्रबंधन (Morning & Evening Shifts):** सुबह और शाम की शिफ्ट के अलग-अलग लेजर और रसीद ट्रैकिंग।
- **भुगतान व खर्च प्रबंधन (Payments & Expenses):** किसानों के एडवांस/भुगतान, बकाया हिसाब और केंद्र के खर्च (बिजली, रखरखाव आदि) का रिकॉर्ड।
- **रिपोर्ट्स व बिलिंग (Reports & WhatsApp Billing):** दैनिक व मासिक कलेक्शन रिपोर्ट, किसान पासबुक और WhatsApp रसीद शेयरिंग।
- **सुरक्षा और क्लाउड सिंक (Firebase Auth, Firestore & Storage):**
  - Google Sign-In (`CredentialManager` + Firebase Auth)
  - Role-Based Access Control (`ADMIN`, `OPERATOR`, `STAFF`)
  - Cloud Firestore `(default)` डेटाबेस + सख्त `firestore.rules`
  - Offline-first Room स्थानीय कैश और ऑटोमैटिक क्लाउड सिंक

---

## प्रोजेक्ट तकनीकी विवरण (Technical Specs)

| प्रॉपर्टी | मान (Value) |
| --- | --- |
| **GitHub Repository** | `MilkCenter-ERP` |
| **App Name** | `Namaste India Dairy Manager` |
| **Application ID** | `Com.milk.Centre` |
| **Namespace** | `com.example` |
| **Min SDK / Target SDK / Compile SDK** | `24` (Android 7.0+) / `36` / `36` (`minorApiLevel = 1`) |
| **Language / UI** | Kotlin `2.2.10` • Jetpack Compose (Material 3) |
| **Build Toolchain** | AGP `9.1.1` • Gradle `9.3.1` • KSP `2.3.6` |
| **Firebase** | BoM `34.17.0` • Auth • Firestore `(default)` • Storage • AI Logic • App Check |

---

## डाउनलोड लिंक (GitHub Releases & Artifacts)

GitHub पर इस रिपॉजिटरी (`MilkCenter-ERP`) के `main` ब्रांच पर Push करने और GitHub Actions वर्कफ़्लो (`Build & Release MilkCenter ERP APK`) पूरा होने के बाद, **GitHub Releases** से सीधे डाउनलोड करें:

1. **Debug APK Install Download (Latest Release):**
   - `https://github.com/mirchibiriyani27625-wq/MilkCenter-ERP/releases/latest/download/MilkCenter-ERP-Debug.apk`
2. **Signed Release APK Install Download (Latest Release):**
   - `https://github.com/mirchibiriyani27625-wq/MilkCenter-ERP/releases/latest/download/MilkCenter-ERP-Release.apk`
3. **Complete Source Code ZIP (Latest Release):**
   - `https://github.com/mirchibiriyani27625-wq/MilkCenter-ERP/releases/latest/download/MilkCenter-ERP-SourceCode.zip`

> **नोट:** आप **Actions → Build & Release MilkCenter ERP APK → Artifacts** से भी `MilkCenter-ERP-Debug-APK`, `MilkCenter-ERP-Release-APK`, और `MilkCenter-ERP-SourceCode` डाउनलोड कर सकते हैं।

---

## Android डिवाइस पर APK इंस्टॉल करने के निर्देश

1. **APK डाउनलोड करें:** ऊपर दिए गए Release लिंक से `MilkCenter-ERP-Debug.apk` डाउनलोड करें।
2. **Unknown Sources अनुमति दें:** यदि पहली बार APK इंस्टॉल कर रहे हैं, तो अपने फोन में **Settings → Security / Apps → Install unknown apps** में जाकर ब्राउज़र या फाइल मैनेजर को अनुमति दें।
3. **इंस्टॉल करें:** डाउनलोड की गई `MilkCenter-ERP-Debug.apk` फाइल पर टैप करें और **Install** दबाएँ।
4. **लॉगिन और सिंक:** ऐप खोलें और अपने Google अकाउंट से Sign-In करें।

---

## GitHub Actions & Firebase Setup (नए रिपॉजिटरी के लिए)

सुरक्षा कारणों से `app/google-services.json`, `.env`, और `debug.keystore` को `.gitignore` में रखा गया है। GitHub Actions से APK बिल्ड चलाने के लिए ये चरण पूरे करें:

### 1. GitHub Repository Secrets जोड़ें
**GitHub Repository (`MilkCenter-ERP`) → Settings → Secrets and variables → Actions → New repository secret** में जाएँ:

| Secret नाम | विवरण |
| --- | --- |
| `GOOGLE_SERVICES_JSON_BASE64` | अपने कंप्यूटर/टर्मिनल में `base64 -w 0 app/google-services.json` चलाकर प्राप्त Base64 टेक्स्ट (या `GOOGLE_SERVICES_JSON` में कच्चा JSON) |
| `KEYSTORE_BASE64` *(वैकल्पिक — Release के लिए)* | `base64 -w 0 my-upload-key.jks` का आउटपुट |
| `STORE_PASSWORD` *(वैकल्पिक — Release के लिए)* | Release keystore का store password |
| `KEY_PASSWORD` *(वैकल्पिक — Release के लिए)* | Release keystore के key alias `upload` का password |

### 2. Firebase Console (`milkcenter-erp`) सेटअप जाँचें
- **Authentication → Sign-in method:** **Google** प्रोवाइडर Enable करें।
- **Project Settings → Android App (`Com.milk.Centre`):**
  - Debug APK पर Google Sign-In के लिए Debug SHA-1 व SHA-256 जोड़ें:
    - Debug SHA-1: `44:25:C8:BC:24:9B:CE:5D:61:A8:80:09:E1:3D:18:22:DF:8C:00:A0`
    - Debug SHA-256: `9E:39:18:5E:67:8F:4E:0B:89:61:AC:4B:C7:B2:AD:72:77:43:FA:B0:46:68:C4:DC:9B:40:3B:AB:EA:35:08:9D`
  - Release APK (`my-upload-key.jks`) के लिए:
    - Release SHA-1: `B1:9B:62:68:BD:44:45:3E:64:3F:43:88:19:4E:AE:8B:17:AF:D0:35`
    - Release SHA-256: `28:5C:38:50:5F:8D:1E:2B:31:3D:86:58:CE:94:CC:A1:BB:36:BC:75:E5:38:FC:DC:7D:A7:77:5D:85:56:BA:3B`
- **Cloud Firestore:** `(default)` डेटाबेस बनाएँ और रिपॉजिटरी की `firestore.rules` पब्लिश करें।
- **Firebase Storage:** रसीद/प्रोफ़ाइल अपलोड के लिए Storage Enable करें।

---

## लोकल बिल्ड निर्देश (Local Build)

सुनिश्चित करें कि `app/google-services.json` मौजूद है, फिर चलाएँ:

```bash
gradle :app:testDebugUnitTest :app:processDebugGoogleServices :app:compileDebugKotlin assembleDebug --no-daemon
```

उत्पन्न APK यहाँ मिलेगी:
`app/build/outputs/apk/debug/app-debug.apk`
