# Генератор бейджей с QR-кодом

Многоязычная утилита для создания персонализированных бейджей с QR-кодами.  
Идеально подходит для конференций, мероприятий, пропусков и визиток.

## Особенности
- Генерация QR-кода из произвольных данных (URL, контактная информация, текст).
- Создание бейджа с именем, должностью, компанией и QR-кодом.
- Настраиваемый размер QR-кода и цветовая схема.
- Вставка логотипа в центр QR-кода (опционально).
- Пакетная генерация из CSV-файла.
- Экспорт в PNG и PDF (где поддерживается).
- Настраиваемые шаблоны (размер бейджа, шрифты, расположение).
- Поддержка аргументов командной строки для автоматизации.

## Установка и запуск
Для каждого языка требуются соответствующие инструменты и зависимости.

### Запуск на разных языках

1. **Python**  
   Установка: `pip install qrcode[pil] pillow reportlab`  
   Запуск: `python badge_generator.py --data "John Doe" --name "John Doe" --title "Developer" --company "ACME" --output badge.png`

2. **JavaScript (Node.js)**  
   Установка: `npm install qrcode sharp commander`  
   Запуск: `node badge_generator.js --data "John Doe" --name "John Doe" --title "Developer" --company "ACME" --output badge.png`

3. **Go**  
   Установка: `go get github.com/skip2/go-qrcode`  
   Запуск: `go run badge_generator.go --data "John Doe" --name "John Doe" --title "Developer" --company "ACME" --output badge.png`

4. **Rust**  
   Добавьте `qrcode-generator`, `image`, `clap` в `Cargo.toml`.  
   Запуск: `cargo run -- --data "John Doe" --name "John Doe" --title "Developer" --company "ACME" --output badge.png`

5. **Java**  
   Используйте библиотеки ZXing (core, javase) и iText для PDF.  
   Сборка: `javac -cp zxing-core.jar:zxing-javase.jar:itextpdf.jar BadgeGenerator.java`  
   Запуск: `java -cp .;zxing-core.jar;zxing-javase.jar;itextpdf.jar BadgeGenerator --data "John Doe" --name "John Doe" --title "Developer" --company "ACME" --output badge.png`

6. **C# (.NET Core)**  
   Установка: `dotnet add package QRCoder` и `dotnet add package SixLabors.ImageSharp`  
   Запуск: `dotnet run -- --data "John Doe" --name "John Doe" --title "Developer" --company "ACME" --output badge.png`

7. **C++ (Linux)**  
   Требуется libqrencode, libpng, OpenCV или ImageMagick. Упрощённо: используем `qrencode` и `gd` или `cairo`.  
   Сборка: `g++ -std=c++11 -o badge_generator badge_generator.cpp -lqrencode -lpng -lz`  
   Запуск: `./badge_generator --data "John Doe" --name "John Doe" --title "Developer" --company "ACME" --output badge.png`

8. **Kotlin (JVM)**  
   Используйте ZXing и iText как в Java.  
   Сборка: `kotlinc -cp zxing-core.jar:zxing-javase.jar:itextpdf.jar BadgeGenerator.kt`  
   Запуск: `kotlin -cp .;zxing-core.jar;zxing-javase.jar;itextpdf.jar BadgeGeneratorKt --data "John Doe" --name "John Doe" --title "Developer" --company "ACME" --output badge.png`

## Использование

Общие аргументы (везде, где поддерживается):

- `--data <текст>` – данные для QR-кода (обязательно).
- `--name <текст>` – имя на бейдже (по умолчанию из data).
- `--title <текст>` – должность (опционально).
- `--company <текст>` – компания (опционально).
- `--output <файл>` – выходной файл (PNG или PDF, определяется расширением).
- `--size <число>` – размер QR-кода в пикселях (по умолчанию 200).
- `--logo <путь>` – файл логотипа для вставки в центр QR.
- `--color <цвет>` – цвет QR-кода (HEX, например #000000).
- `--batch <CSV>` – CSV-файл с колонками data, name, title, company для пакетной генерации.
- `--template <путь>` – путь к файлу шаблона (JSON с описанием макета).

Пример (Python):
```bash
python badge_generator.py --data "https://example.com" --name "John Doe" --title "Software Engineer" --company "ACME Inc." --output badge.png --size 300 --logo logo.png --color "#3366CC"
Пример вывода (PNG):

Изображение бейджа с текстом и QR-кодом.

Структура репозитория
text
/
├── README.md
├── badge_generator.py
├── badge_generator.js
├── badge_generator.go
├── badge_generator.rs
├── BadgeGenerator.java
├── BadgeGenerator.cs
├── badge_generator.cpp
└── BadgeGenerator.kt
