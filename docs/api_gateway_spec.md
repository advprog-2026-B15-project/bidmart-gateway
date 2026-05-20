# BidMart API Gateway Configuration & Endpoint Specification

Spesifikasi ini dirancang untuk diimplementasikan pada **Spring Cloud Gateway (SCG)** sebagai *single entry point* platform BidMart. Dokumen ini merangkum port, endpoint, visibilitas (Public vs Protected), header propagation, serta rute internal yang harus diblokir untuk menjaga keamanan sistem.

---

## 1. Topologi Layanan & Port Default
Berdasarkan konfigurasi masing-masing repositori, berikut adalah pemetaan port default dan penamaan layanan:

| Nama Layanan (Spring Application Name) | Repositori / Folder | Port Default | Context Path Utama |
| :--- | :--- | :---: | :--- |
| `bidmart-authentication` | `bidmart-authentication` | **8081** | `/api/auth/**`, `/api/users/**`, `/api/admin/**` |
| `bidmartcatalog` | `bidmart-catalog` | **8080** | `/api/listings/**`, `/api/categories/**` |
| `bidmart-auction` | `bidmart-auction` | **8083** | `/api/auctions/**` |
| `wallet` | `bidmart-wallet` | **8080** | `/api/wallet/**`, `/internal/wallet/**` |
| `bidmart-booking` | `bidmart-booking` | **8085** | `/api/bookings/**`, `/api/notifications/**` |

> [!WARNING]
> **Port Collision pada Localhost:**
> `bidmart-catalog` dan `bidmart-wallet` keduanya berjalan pada port default **8080**.
> - **Solusi Local Development:** Salah satu service harus dipindah portnya (misal, `bidmart-catalog` diubah ke `8082` atau `bidmart-wallet` ke `8084`) atau dijalankan menggunakan containerization dengan Service Discovery / DNS resolver internal (Docker Compose) agar tidak bentrok.

---

## 2. Spesifikasi Rute & Endpoint per Layanan

### A. Auth Service (`bidmart-authentication` - Port `8081`)
Mengelola pendaftaran, autentikasi, serta sesi user.

*   **Rute Publik (Bypass Authentication):**
    *   `POST /api/auth/register` : Registrasi akun baru.
    *   `POST /api/auth/verify-email` : Verifikasi email (menggunakan token di query param).
    *   `POST /api/auth/login` : Login kredensial.
    *   `POST /api/auth/refresh` : Refresh JWT Token.
    *   `POST /api/auth/logout` : Logout & menghapus refresh token.
    *   `POST /api/auth/forgot-password` : Request link reset password.
    *   `POST /api/auth/reset-password` : Reset password baru.
    *   `POST /api/auth/2fa/verify` : Verifikasi kode MFA TOTP.
*   **Rute Terproteksi (Butuh Verifikasi JWT):**
    *   `POST /api/auth/2fa/setup` : Setup kode 2FA.
    *   `POST /api/auth/2fa/confirm` : Konfirmasi aktivasi 2FA.
    *   `POST /api/auth/2fa/disable` : Nonaktifkan 2FA.
    *   `GET /api/auth/sessions` : Mendapatkan sesi aktif pengguna.
    *   `DELETE /api/auth/sessions/{sessionId}` : Menghapus sesi tertentu.
    *   `DELETE /api/auth/sessions` : Menghapus seluruh sesi pengguna.
    *   `GET /api/users/me` : Mengambil profil pengguna yang login.
*   **Rute Admin-Only (Butuh JWT & Role `ADMIN`):**
    *   `POST /api/admin/users/{id}/disable` : Memblokir pengguna dan mencabut seluruh sesinya.

---

### B. Catalog Service (`bidmart-catalog` - Port `8080`)
Mengelola listing katalog barang yang dilelang.

*   **Rute Publik (Bypass Authentication - REST API):**
    *   `GET /api/listings` : Melihat katalog produk aktif (bisa filter pencarian/kategori).
    *   `GET /api/listings/{id}` : Mendapatkan detail listing produk.
    *   `GET /api/categories` : Mendapatkan seluruh kategori & sub-kategori.
*   **Rute Terproteksi (Butuh Verifikasi JWT - REST API):**
    *   `PUT /api/listings/{id}` : Mengupdate data listing (Hanya pembuat/Admin).
    *   `DELETE /api/listings/{id}` : Menghapus listing produk.
*   **Rute HTML Web View (Thymeleaf - Non-API):**
    *   `GET /listings` : Halaman daftar listing.
    *   `GET /listings/create` : Halaman form pembuatan listing.
    *   `POST /listings/create` : Action submit listing baru.
    *   `POST /listings/{id}/publish` : Action mempublikasikan listing.
    *   `GET /listings/{id}` : Halaman detail listing.
    *   `GET /listings/{id}/edit` : Halaman form edit listing.
    *   `POST /listings/{id}/edit` : Action submit edit listing.
    *   `POST /listings/{id}/delete` : Action menghapus listing.
*   **Rute Internal (Harus DIBLOKIR dari luar oleh Gateway):**
    *   `PATCH /api/listings/{id}/current-price` : Mengupdate harga terkini barang (hanya dipanggil internal oleh Auction service).
    *   `GET /api/listings/{id}/validate` : Validasi status listing sebelum bid (hanya dipanggil internal oleh Auction service).

---

### C. Auction Service (`bidmart-auction` - Port `8083`)
Mengelola siklus lelang, durasi, dan bidding.

*   **Rute Publik (Bypass Authentication):**
    *   `GET /api/auctions` : Melihat daftar lelang.
    *   `GET /api/auctions/{id}` : Mendapatkan detail lelang.
    *   `GET /api/auctions/{id}/bids` : Melihat riwayat bid pada lelang tersebut.
    *   `GET /api/auctions/{id}/stream` : **Server-Sent Events (SSE)** untuk memantau update bid secara real-time.
*   **Rute Terproteksi (Butuh Verifikasi JWT & Propagasi Header `X-User-Id`):**
    *   `POST /api/auctions` : Membuat lelang baru (DRAFT).
    *   `PATCH /api/auctions/{id}` : Update draf lelang sebelum aktif.
    *   `PATCH /api/auctions/{id}/activate` : Mengaktifkan lelang agar bisa di-bid.
    *   `POST /api/auctions/{id}/bids` : Mengajukan penawaran harga (bid).

---

### D. Wallet Service (`bidmart-wallet` - Port `8080` / custom)
Mengelola saldo dan transaksi pengguna.

*   **Rute Terproteksi (Butuh Verifikasi JWT & Propagasi Header `X-User-Id`):**
    *   `GET /api/wallet/{userId}` : Mengambil informasi saldo wallet milik user tersebut.
    *   `POST /api/wallet/{userId}/topup` : Mengisi saldo (top-up).
    *   `POST /api/wallet/{userId}/withdraw` : Menarik saldo.
    *   `GET /api/wallet/{userId}/transactions` : Melihat riwayat transaksi dompet.
*   **Rute Internal (Harus DIBLOKIR dari luar oleh Gateway):**
    *   `POST /internal/wallet/hold` : Menahan saldo penawar (dipanggil oleh Auction service).
    *   `POST /internal/wallet/release` : Melepas saldo penawar yang kalah (dipanggil oleh Auction/Booking service).
    *   `POST /internal/wallet/convert` : Mengubah status hold saldo pemenang lelang menjadi transaksi sukses.

---

### E. Booking Service (`bidmart-booking` - Port `8085`)
Mengelola pesanan pemenang lelang, status pengiriman, serta notifikasi.

*   **Rute Terproteksi (Butuh Verifikasi JWT & Propagasi Header `X-User-Id` dan `X-User-Role`):**
    *   `GET /api/bookings/me` : Daftar booking milik pengguna.
    *   `GET /api/bookings/{id}` : Detail booking spesifik.
    *   `PATCH /api/bookings/{id}/shipment` : Update status pengiriman kurir (Hanya oleh `X-User-Role` = `SELLER`).
    *   `PATCH /api/bookings/{id}/confirm-delivery` : Konfirmasi barang telah sampai (Hanya oleh `X-User-Role` = `BUYER`).
    *   `GET /api/notifications/me` : Mengambil notifikasi user.
    *   `GET /api/notifications/stream` : **Server-Sent Events (SSE)** untuk real-time push notification.
    *   `GET /api/notifications/preferences/me` : Mengambil preferensi notifikasi (email vs in-app).
    *   `PATCH /api/notifications/preferences/me` : Update preferensi notifikasi.
    *   `PATCH /api/notifications/{id}/read` : Menandai notifikasi telah dibaca.
*   **Rute Dev/Internal (Harus DIBLOKIR di Production):**
    *   `POST /api/dev/events/winner-determined` : Mensimulasikan event penentuan pemenang (WinnerDetermined) untuk trigger pembuatan booking.

---

## 3. Rekomendasi Penerapan di Spring Cloud Gateway (SCG)

Untuk mengimplementasikan arsitektur di atas secara aman dan andal, berikut adalah aspek krusial yang wajib dikonfigurasi di SCG:

### 1. Centralized JWT Verification (`JwtAuthenticationFilter`)
Gateway bertugas melakukan dekripsi dan validasi JWT token menggunakan secret key:
*   **JWT Secret:** `${JWT_SECRET}` (Harus sama dengan kunci yang digunakan di `bidmart-authentication`).
*   **Header Injection:** Setelah validasi sukses, gateway mengekstrak klaim (claims) dari JWT dan memasukkannya ke dalam header untuk dikonsumsi downstream services:
    *   Ekstrak subjek/ID pengguna $\rightarrow$ Tambahkan header `X-User-Id`.
    *   Ekstrak klaim role/otoritas $\rightarrow$ Tambahkan header `X-User-Role`.

### 2. Aturan Keamanan Rute (Route Security Rules)
*   **Pemblokiran Akses Internal:**
    Gunakan predicate di gateway untuk menolak request luar yang mengarah ke path berikut:
    *   Path `/internal/**` (misal `/internal/wallet/**`).
    *   Path `/api/dev/**` (untuk production).
    Kembalikan status `403 Forbidden` jika ada client eksternal yang mencoba mengaksesnya.
*   **Pencocokan Rute Terproteksi:**
    Pastikan rute `/api/wallet/**`, `/api/bookings/**`, `/api/notifications/**`, dan beberapa POST/PATCH `/api/auctions/**` memiliki filter `JwtAuthenticationFilter` aktif.

### 3. Penanganan Server-Sent Events (SSE)
Layanan lelang (`/api/auctions/{id}/stream`) dan notifikasi (`/api/notifications/stream`) menggunakan SSE (`text/event-stream`). SCG harus dikonfigurasi agar:
*   Meneruskan header `Connection: keep-alive` dan `Cache-Control: no-cache` tanpa modifikasi.
*   Menonaktifkan buffering respons di gateway (misalnya dengan menyetel agar filter buffer dinonaktifkan khusus untuk rute SSE).
*   Mengatur timeout koneksi/baca proxy agar bernilai sangat tinggi atau dinonaktifkan pada rute streaming agar koneksi tidak diputus di tengah jalan.

### 4. Kebijakan CORS Global
Konfigurasikan gateway untuk menangani CORS agar frontend (Next.js) dapat berkomunikasi lancar:
```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "https://bidmart-frontend-url.vercel.app" # URL frontend
            allowedMethods:
              - GET
              - POST
              - PUT
              - PATCH
              - DELETE
              - OPTIONS
            allowedHeaders: "*"
            allowCredentials: true
```
