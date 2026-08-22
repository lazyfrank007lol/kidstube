# KidsTube — app "giả YouTube" chỉ phát playlist cố định

## Cách hoạt động
- WebView load 1 file HTML nội bộ (`app/src/main/assets/player.html`)
- Dùng YouTube IFrame Player API để phát video, nhưng **playlist bị khoá cứng** trong mảng `PLAYLIST` — trẻ không vào được trang chủ YouTube, không search, không xem gợi ý random.
- Video hết sẽ tự chuyển sang video **tiếp theo trong danh sách của bạn**, không rơi vào đề xuất của thuật toán.
- Nút Back hệ thống bị vô hiệu hoá trong `MainActivity.kt` — trẻ không thoát ra ngoài được.
- Muốn thoát: nhấn giữ (long-press) vào màn hình 3 lần liên tiếp → hiện hộp nhập mã PIN (mặc định `2468`, đổi trong `MainActivity.kt` dòng `PARENT_PIN`).

## Việc cần làm trước khi build
1. Mở `app/src/main/assets/player.html`, sửa mảng `PLAYLIST` thành các `videoId` thật (lấy từ URL `youtube.com/watch?v=XXXXXXXX`, phần `XXXXXXXX` chính là id).
2. Đổi `PARENT_PIN` trong `MainActivity.kt`.
3. (Tuỳ chọn) đổi icon app trong `res/mipmap` — không dùng logo YouTube thật để tránh vấn đề bản quyền/thương hiệu nếu bạn định chia sẻ app công khai; nếu chỉ dùng riêng trong nhà thì thoải mái.

## Build bằng GitHub Actions (giống workflow bạn hay dùng)
1. Tạo repo mới, push toàn bộ project này lên.
2. Vì project thiếu file nhị phân `gradle-wrapper.jar`, mở project này bằng Android Studio **một lần** (hoặc chạy `gradle wrapper` nếu có gradle local) để nó tự sinh ra — commit luôn 3 file: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`.
3. Push lên `main`, Action `.github/workflows/build.yml` sẽ tự chạy và build ra `app-debug.apk`, tải về ở tab Actions → Artifacts.
4. Cài file `.apk` đó lên TV Android / điện thoại → xong.

## Build local (nếu có Android Studio)
Mở thư mục này bằng Android Studio → Run ▶️ trên thiết bị/TV.

## Giới hạn
- Cần mạng để phát (nhúng qua YouTube IFrame API, không tải video offline).
- Icon "YouTube giả" trong README chỉ mang tính minh hoạ — không kèm logo YouTube thật trong project để tránh vi phạm thương hiệu khi chia sẻ mã nguồn.

## Disclaimer
YouTube is a trademark of Google LLC. This project is an independent, unofficial
tool that uses the publicly available YouTube IFrame Player API. It is not
affiliated with, endorsed by, sponsored by, or in any way officially connected
with YouTube or Google LLC.

## License
MIT — xem file [LICENSE](./LICENSE).
