# KasKu — Aplikasi Pencatatan Keuangan Pribadi (Android)

Dokumen ini merangkum status pengembangan, arsitektur teknis, serta daftar fitur yang sudah selesai dibangun dan diverifikasi pada aplikasi Android **KasKu**.

---

## 1. Status Proyek Saat Ini

| Komponen | Status | Keterangan |
| :--- | :---: | :--- |
| **Build Aplikasi Android (Kotlin + Jetpack Compose)** | ✅ Selesai | Kompilasi Gradle sukses tanpa error |
| **Desain Tema, Tipografi & Ikon Aplikasi** | ✅ Selesai | Tema *Emerald Ledger & Warm Alabaster* (Light/Dark), font lokal *Plus Jakarta Sans* & *JetBrains Mono*, serta custom adaptive launcher icon |
| **Autentikasi Google Sign-In** | ✅ Selesai | Jetpack Credential Manager (`GetSignInWithGoogleOption` + auto sign-in) |
| **Database Cloud Firestore** | ✅ Selesai & Aktif | Terhubung ke Cloud Firestore Enterprise, sinkronisasi real-time sudah terverifikasi |
| **Firestore Security Rules** | ✅ Teruji & Ter-deploy | 8/8 pengujian emulator lulus & aturan sudah di-deploy ke backend |
| **Visualisasi Grafik Saldo Harian** | ✅ Selesai | Grafik garis saldo harian, grafik batang pengeluaran harian, dan grafik donat kategori |
| **Manajemen Kategori Pengeluaran** | ✅ Selesai | 10 kategori bawaan + CRUD kategori kustom (ikon, warna, batas anggaran) |
| **Target Tabungan (Saving Goals)** | ✅ Selesai | Progres target, rekomendasi nabung/bulan, dan tombol `+ Setor` (opsional potong saldo bulanan) |
| **Transaksi Berulang (Tagihan & Gaji)** | ✅ Selesai | Jadwal bulanan rutin (tanggal 1–31), status aktif/nonaktif, dan pencatatan 1-ketuk atomik |
| **Pengingat Harian & Peringatan Anggaran** | ✅ Selesai | Pengingat harian (`AlarmManager` inexact + izin `POST_NOTIFICATIONS`), banner & notifikasi batas 80%/100% |
| **Ekspor Laporan PDF** | ✅ Selesai | Generator A4 multi-halaman (Simpan via SAF & Bagikan via FileProvider) |
| **Build APK & CI/CD GitHub Releases** | ✅ Selesai | `gradle :app:assembleDebug` sukses + workflow otomatis `.github/workflows/release-apk.yml` |

- **Nama Aplikasi**: `KasKu`
- **Platform**: Android (Kotlin, Jetpack Compose, Material Design 3, Min SDK 24 / Target SDK 36)
- **Backend**: Firebase Authentication (Google Sign-In) & Cloud Firestore *(dapat diganti ke proyek Firebase sendiri saat di-fork)*

---

## 2. Fitur yang Sudah Dibangun

### A. Autentikasi & Keamanan Akun
- **Masuk dengan Google (Google Sign-In)** menggunakan Jetpack Credential Manager (`GetSignInWithGoogleOption`).
- **Auto Sign-In Senyap** saat aplikasi dibuka kembali bagi akun yang sudah pernah terotorisasi.
- **Auth Gating di Level Navigasi**: Layar utama dan listener database hanya aktif setelah pengguna berhasil login.
- **Keluar Akun (Sign-Out)** aman dengan pembersihan sesi kredensial (`NonCancellable`).

### B. Beranda & Pemantauan Saldo Harian (`Beranda`)
- **Navigasi Periode Bulanan**: Pindah antar bulan (`< Bulan Tahun >`) dan tombol pintas kembali ke bulan berjalan.
- **Kartu Ringkasan Saldo Bulanan**:
  - Menampilkan **Saldo Bulan Ini**, **Saldo Awal Bulan**, **Total Pemasukan**, dan **Total Pengeluaran**.
  - Pengaturan **Saldo Awal Bulan**, **Target Batas Pengeluaran Bulanan (opsional)**, dan **Target Batas Pengeluaran Mingguan (opsional)** per periode (`YYYY-MM`).
  - Indikator progres **Anggaran Bulanan & Mingguan** otomatis berubah warna saat mendekati (80%) atau melebihi (100%) batas.
- **Grafik Pemantauan Harian Interaktif**:
  - **Mode Saldo Harian (Line & Area Chart)**: Memantau pergerakan saldo penutupan dari tanggal 1 sampai akhir bulan; dapat diketuk atau digeser (*scrub*) untuk melihat saldo, total keluar, dan total masuk pada tanggal tertentu.
  - **Mode Keluar Harian (Bar Chart)**: Menampilkan lonjakan pengeluaran per hari beserta jumlah transaksi di tanggal yang dipilih.
- **Ringkasan Cepat**: Menampilkan kategori pengeluaran terbesar, hari dengan pengeluaran tertinggi, rata-rata pengeluaran harian, serta daftar transaksi terbaru.

### C. Pencatatan Transaksi Uang Keluar & Masuk (`Transaksi`)
- **Tambah, Edit, dan Hapus Transaksi** lewat modal *bottom sheet* lengkap dengan konfirmasi hapus.
- **Detail Transaksi**:
  - Tipe: **Pengeluaran (`EXPENSE`)** atau **Pemasukan (`INCOME`)**
  - Nominal Rupiah dilengkapi tombol cepat (`+25rb`, `+50rb`, `+100rb`, `+250rb`)
  - Keterangan tujuan uang keluar/masuk (maks. 100 karakter)
  - Pilihan **Kategori** sesuai tipe transaksi
  - **Metode Pembayaran**: *Tunai*, *Transfer Bank*, *E-Wallet*, *Kartu*
  - Pemilih **Tanggal** (*DatePicker*)
  - **Catatan tambahan** opsional (maks. 300 karakter)
- **Pencarian & Filter**:
  - Pencarian cepat berdasarkan keterangan, kategori, atau catatan.
  - Filter tipe (*Semua / Pengeluaran / Pemasukan*) dan filter berdasarkan kategori.
  - Pengelompokan daftar transaksi otomatis berdasarkan tanggal beserta total keluar harian.

### D. Kategori Pengeluaran & Pemasukan (`Kategori`)
- **10 Kategori Bawaan Otomatis** saat pertama kali login:
  - *Pengeluaran*: Makan & Minum, Transportasi, Belanja, Tagihan & Utilitas, Kesehatan, Hiburan, Pendidikan, Lainnya.
  - *Pemasukan*: Gaji, Bonus & Tambahan.
- **Grafik Donat Distribusi Pengeluaran** menampilkan persentase porsi tiap kategori.
- **Kelola Kategori Kustom & Batas Opsional**: Tambah, edit, dan hapus kategori dengan pilihan 20 ikon, 10 palet warna, tipe (*Pengeluaran/Pemasukan*), serta sakelar **batas anggaran bulanan per kategori (opsional)** dengan tombol cepat **Atur Batas** langsung di kartu kategori.
- **Pintasan Filter**: Ketuk kartu kategori untuk langsung membuka daftar transaksi pada kategori tersebut.

### E. Laporan Bulanan & Ekspor PDF (`Laporan`)
- **Pratinjau Laporan Eksekutif** langsung di dalam aplikasi (Saldo Awal, Pemasukan, Pengeluaran, Saldo Akhir, daftar kategori terbesar, dan pratinjau grafik saldo harian).
- **Generator PDF A4 Native (`PdfDocument`)** dengan dukungan banyak halaman (*multi-page pagination*), berisi:
  1. Header resmi **KasKu**, periode laporan, akun pengguna, dan waktu cetak.
  2. Ringkasan 4 kartu metrik utama (Saldo Awal, Pemasukan, Pengeluaran, Saldo Akhir).
  3. **Grafik Pergerakan Saldo Harian** yang digambar langsung secara vektor di dalam PDF.
  4. Tabel **Ringkasan Pengeluaran per Kategori** (jumlah transaksi, persentase, total Rupiah).
  5. Tabel **Rincian Seluruh Transaksi** (tanggal, keterangan + catatan, kategori, metode, nominal).
- **Dua Cara Ekspor (Tanpa Izin Penyimpanan Luas)**:
  - **Simpan ke Perangkat (PDF)** via *Storage Access Framework* (`CreateDocument`).
  - **Bagikan Laporan PDF** via `FileProvider` ke WhatsApp, Email, Google Drive, dan aplikasi lainnya.

### F. Rencana Keuangan: Target Tabungan & Transaksi Berulang (`Rencana`)
- **Target Tabungan (*Saving Goals*)**:
  - Buat, ubah, dan hapus target tabungan (mis. Dana Darurat, Liburan, Gadget) dengan nominal target, saldo terkumpul, tenggat waktu, ikon, warna, dan catatan.
  - Menampilkan **progres persentase**, sisa nominal, dan **rekomendasi setoran per bulan** berdasarkan sisa waktu hingga target.
  - Tombol cepat **`+ Setor`**: Menambah saldo tabungan dan secara opsional langsung mencatat transaksi pengeluaran pada bulan berjalan secara atomik (`WriteBatch`).
- **Transaksi Berulang (*Tagihan Rutin & Gaji*)**:
  - Kelola template tagihan bulanan (kos, listrik, internet, cicilan) maupun pemasukan rutin (gaji) lengkap dengan tanggal rutin (1–31), kategori, metode pembayaran, dan sakelar aktif/nonaktif.
  - Pelacakan `lastAppliedYearMonth` mencegah pencatatan ganda pada bulan yang sama.
  - **Banner Cepat di Beranda & Tab Rencana**: Tombol 1-ketuk **Terapkan Semua** atau **Catat Sekarang** untuk memasukkan transaksi rutin ke buku kas bulan terpilih secara atomik.

### G. Pengingat Harian & Peringatan Batas Anggaran
- **Pengingat Catat Harian**:
  - Sakelar aktif/nonaktif di tab **Laporan** dengan pilihan jam cepat (`08:00`, `12:00`, `19:00`, `20:00`, `21:00`) maupun *TimePicker* kustom serta tombol **Uji Notifikasi**.
  - Meminta izin runtime `POST_NOTIFICATIONS` secara ramah di Android 13+ dan menggunakan `AlarmManager.setInexactRepeating` sesuai kebijakan Google Play.
- **Peringatan Batas Anggaran (80% & 100%)**:
  - **Banner Peringatan di Beranda**: Muncul otomatis saat pengeluaran bulanan atau kategori mana pun mencapai >=80% (*Mendekati Batas*) atau >=100% (*Terlampaui*).
  - **Notifikasi Instan**: Mengirim notifikasi Android otomatis tepat ketika transaksi pengeluaran baru melewati ambang 80% atau 100%.

### H. UI/UX & Responsivitas
- Mendukung **Edge-to-Edge** penuh dengan penanganan *WindowInsets* (status bar & navigation bar).
- **Tata Letak Adaptif**: Menggunakan `NavigationBar` (bawah) di layar ponsel dan otomatis beralih ke `NavigationRail` (samping) pada layar lebar/tablet (`>= 600dp`).
- Mendukung **Mode Terang & Mode Gelap** mengikuti tema sistem.

---

## 3. Struktur Database Cloud Firestore

Seluruh data pengguna disimpan secara terisolasi di bawah path `/users/{userId}/...` dan dilindungi oleh **Firestore Security Rules** (hanya pemilik akun dengan `request.auth.uid == userId` yang dapat membaca/menulis datanya):

1. `users/{userId}/transactions/{transactionId}`
   - Menyimpan: `userId`, `title`, `amount`, `type`, `categoryId`, `categoryName`, `paymentMethod`, `note` *(opsional)*, `dateMillis`, `yearMonth`, `dayOfMonth`, `createdAt`, `updatedAt`.
2. `users/{userId}/categories/{categoryId}`
   - Menyimpan: `userId`, `name`, `type`, `iconKey`, `colorHex`, `budgetLimit`, `createdAt`, `updatedAt`.
3. `users/{userId}/monthConfigs/{YYYY-MM}`
   - Menyimpan: `userId`, `yearMonth`, `openingBalance`, `monthlyBudget`, `weeklyBudget` *(opsional)*, `createdAt`, `updatedAt`.
4. `users/{userId}/savingGoals/{goalId}`
   - Menyimpan: `userId`, `name`, `targetAmount`, `currentAmount`, `targetDateMillis`, `iconKey`, `colorHex`, `note` *(opsional)*, `createdAt`, `updatedAt`.
5. `users/{userId}/recurringTemplates/{templateId}`
   - Menyimpan: `userId`, `title`, `amount`, `type`, `categoryId`, `categoryName`, `paymentMethod`, `dayOfMonth`, `active`, `lastAppliedYearMonth` *(opsional)*, `note` *(opsional)*, `createdAt`, `updatedAt`.

> **Catatan**: File PDF yang diekspor dibuat secara lokal di perangkat pengguna dan **tidak** diunggah ke server Firebase. Preferensi jam pengingat harian disimpan lokal di perangkat via `SharedPreferences`.

---

## 4. Verifikasi & Pengujian yang Sudah Dilakukan

- **Security Rules Unit Testing (`firestore.test.js`)**: **8/8 test lulus** terhadap Firestore Emulator (penolakan akses tanpa login, CRUD transaksi/kategori/konfigurasi bulan/target tabungan/transaksi rutin, penolakan payload tidak valid & mutasi field permanen, serta isolasi penuh antar pengguna).
- **Deploy Rules**: Aturan keamanan sudah ter-deploy ke database produksi Firebase.
- **Audit Keamanan Kode**: 0 temuan pola autentikasi terlarang (`signInAnonymously`, `signInWithEmailAndPassword`) maupun pemanggilan Firestore tanpa ID database.
- **Sinkronisasi Live**: Pencatatan kategori, pengaturan saldo awal, dan transaksi sudah terverifikasi masuk ke Cloud Firestore secara real-time.
- **Verifikasi Build APK**: `gradle :app:assembleDebug` berhasil membangun `app/build/outputs/apk/debug/app-debug.apk` tanpa error.

---

## 5. Panduan Instalasi untuk Pengguna & Developer (Fork + Firebase Sendiri)

Repositori ini terbuka untuk di-**fork**, dijalankan secara lokal, maupun dihubungkan ke **proyek Firebase milik Anda sendiri**.

### A. Untuk Pengguna (Pasang APK Langsung)
1. Buka tab **Releases** di repositori GitHub ini.
2. Unduh file **`KasKu-v*.apk`** versi terbaru.
3. Pasang di perangkat Android Anda (aktifkan izin *Install from unknown sources* jika diminta).

---

### B. Untuk Developer (Fork & Pasang Firebase Sendiri)

**Prasyarat:** Android Studio terbaru (atau CLI dengan **JDK 21**, **Android SDK API 36.1**, **Gradle 9.3.1**), serta [Firebase CLI](https://firebase.google.com/docs/cli) untuk deploy *Security Rules*.

#### 1. Fork & Clone Repositori
```bash
git clone https://github.com/<username-anda>/<nama-repo>.git
cd <nama-repo>
```

#### 2. Siapkan Keystore Debug Lokal
Agar penandatanganan konsisten dan SHA-1 bisa didaftarkan ke Firebase Anda:
```bash
keytool -genkeypair -v \
  -keystore debug.keystore \
  -storepass android \
  -alias androiddebugkey \
  -keypass android \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```
Lalu cek sidik jari **SHA-1** dan **SHA-256**:
```bash
keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android
```

#### 3. Buat & Konfigurasi Proyek Firebase Sendiri
1. Buka [Firebase Console](https://console.firebase.google.com/) → buat proyek baru.
2. Aktifkan **Authentication → Sign-in method → Google**.
3. Aktifkan **Cloud Firestore** (pilih mode *Production*, bisa memakai database `(default)` atau nama database kustom).
4. *(Opsional, disarankan untuk fork)* Ubah `applicationId` di `app/build.gradle.kts` (mis. `com.namaanda.kasku`) — biarkan `namespace = "com.example"` tetap sama.
5. Tambahkan **Android App** di **Project Settings → Your Apps** memakai `applicationId` tersebut, lalu masukkan **SHA-1** dan **SHA-256** dari langkah 2.
6. Unduh file **`google-services.json`** lalu timpa `app/google-services.json`.

#### 4. Sesuaikan ID Database Firestore
Buka `app/src/main/res/values/firebase_applet_config.xml` dan sesuaikan dengan nama database Firestore Anda (isi `(default)` jika memakai database standar Firebase):
```xml
<?xml version="1.0" encoding="utf-8" standalone="no"?>
<resources>
    <string name="firestore_database_id" translatable="false">(default)</string>
</resources>
```
Sesuaikan juga field `"database"` di `firebase.json` (mis. `"(default)"`) agar cocok saat deploy rules.

#### 5. Deploy & Uji Firestore Security Rules
Proyek ini sudah menyertakan aturan keamanan lengkap di `firestore.rules` dan 8 unit test di `firestore.test.js`:
```bash
# (Opsional) Uji aturan keamanan secara lokal dengan Firestore Emulator
firebase emulators:exec --only firestore --project demo-no-project "node --test firestore.test.js"

# Deploy aturan keamanan ke proyek Firebase Anda
firebase login
firebase use <FIREBASE_PROJECT_ID_ANDA>
firebase deploy --only firestore:rules
```

#### 6. Build APK Secara Lokal
```bash
gradle :app:assembleDebug
```
Hasil APK tersedia di: `app/build/outputs/apk/debug/app-debug.apk`.

---

### C. Build & Publish APK Otomatis via GitHub Actions
Workflow `.github/workflows/release-apk.yml` sudah siap dipakai di repo ini maupun di repo hasil fork:

1. **(Disarankan agar Google Sign-In aktif)** Simpan secret di **Settings → Secrets and variables → Actions**:
   - `DEBUG_KEYSTORE_BASE64`: isi `debug.keystore` dalam format base64 (`base64 -w 0 debug.keystore`) — SHA-1 harus terdaftar di Firebase.
   - *(Opsional untuk fork)* `GOOGLE_SERVICES_JSON_BASE64`: jika tidak ingin menaruh `app/google-services.json` langsung di repo publik.
   - *(Opsional untuk rilis produksi)* `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, dan `KEY_PASSWORD` (alias `upload`) agar workflow otomatis mem-build varian `:app:assembleRelease`.
2. Jalankan workflow:
   - Buka tab **Actions → Build & Release KasKu APK → Run workflow** lalu isi tag versi (mis. `v1.0.0`), **atau**
   - Push tag Git baru:
     ```bash
     git tag v1.0.0
     git push origin v1.0.0
     ```
3. File **`KasKu-v1.0.0.apk`** otomatis ter-build dan terpublikasi di halaman **Releases** GitHub.

---

## 6. Rencana Pengembangan Berikutnya (Opsional / Backlog)

- [x] **Target Tabungan (Saving Goals)** (Dana Darurat, Liburan, dll.)
- [x] **Transaksi Berulang Otomatis** (tagihan bulanan, langganan, dan gaji rutin)
- [x] **Pengingat Harian & Notifikasi Batas Anggaran**
- [ ] **Multi-Dompet / Kantong Saldo** (saldo terpisah untuk Tunai, Bank, E-Wallet + transfer antar dompet)
- [ ] **Perbandingan Antar Bulan & Grafik Tren 6 Bulan** (*Month-over-Month analytics*)
- [ ] **Ekspor CSV / Excel & Laporan Rentang Tanggal Kustom**
- [ ] **Kunci Biometrik (Sidik Jari / Face Unlock)**
