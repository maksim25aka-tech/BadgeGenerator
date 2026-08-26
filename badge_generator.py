
---

# Код на 8 языках программирования

## 1. Python (`badge_generator.py`)

```python
# badge_generator.py
import argparse
import csv
import json
import os
from PIL import Image, ImageDraw, ImageFont
import qrcode
from qrcode.image.styledpil import StyledPilImage
from qrcode.image.styles.moduledrawers import RoundedModuleDrawer
from qrcode.image.styles.colormasks import SolidFillColorMask
import io

class BadgeGenerator:
    def __init__(self, data, name, title=None, company=None, size=200,
                 color="#000000", logo_path=None, template=None):
        self.data = data
        self.name = name if name else data
        self.title = title
        self.company = company
        self.size = size
        self.color = color
        self.logo_path = logo_path
        self.template = self.load_template(template) if template else self.default_template()

    def default_template(self):
        return {
            "badge_width": 600,
            "badge_height": 400,
            "background": "#FFFFFF",
            "font_path": None,  # uses default
            "name_font_size": 40,
            "title_font_size": 24,
            "company_font_size": 28,
            "qr_position": (380, 80),
            "text_position": (50, 100),
            "text_color": "#000000"
        }

    def load_template(self, path):
        with open(path, 'r') as f:
            return json.load(f)

    def create_qr(self):
        qr = qrcode.QRCode(
            version=1,
            error_correction=qrcode.constants.ERROR_CORRECT_H,
            box_size=10,
            border=4,
        )
        qr.add_data(self.data)
        qr.make(fit=True)
        # Create styled image
        img = qr.make_image(
            image_factory=StyledPilImage,
            module_drawer=RoundedModuleDrawer(),
            color_mask=SolidFillColorMask(
                back_color=(255, 255, 255),
                front_color=self.color
            )
        ).convert('RGB')
        img = img.resize((self.size, self.size), Image.Resampling.LANCZOS)
        # Embed logo if provided
        if self.logo_path:
            logo = Image.open(self.logo_path).convert("RGBA")
            logo_size = int(self.size * 0.25)
            logo = logo.resize((logo_size, logo_size), Image.Resampling.LANCZOS)
            pos = ((self.size - logo_size) // 2, (self.size - logo_size) // 2)
            img.paste(logo, pos, logo)
        return img

    def generate(self, output_path):
        template = self.template
        # Create base badge
        badge = Image.new('RGB', (template['badge_width'], template['badge_height']),
                          template['background'])
        draw = ImageDraw.Draw(badge)

        # Load font (default if None)
        try:
            font_name = ImageFont.truetype(template.get('font_path', 'arial.ttf'),
                                           template['name_font_size'])
            font_title = ImageFont.truetype(template.get('font_path', 'arial.ttf'),
                                            template['title_font_size'])
            font_company = ImageFont.truetype(template.get('font_path', 'arial.ttf'),
                                              template['company_font_size'])
        except:
            font_name = ImageFont.load_default()
            font_title = font_name
            font_company = font_name

        # Draw text
        text_x, text_y = template['text_position']
        draw.text((text_x, text_y), self.name, fill=template['text_color'], font=font_name)
        if self.title:
            draw.text((text_x, text_y + template['name_font_size'] + 10),
                      self.title, fill=template['text_color'], font=font_title)
        if self.company:
            draw.text((text_x, text_y + template['name_font_size'] + template['title_font_size'] + 20),
                      self.company, fill=template['text_color'], font=font_company)

        # Paste QR
        qr_img = self.create_qr()
        qr_x, qr_y = template['qr_position']
        badge.paste(qr_img, (qr_x, qr_y))

        # Save
        badge.save(output_path)
        print(f"Badge saved to {output_path}")

def batch_generate(csv_path, output_dir, **kwargs):
    with open(csv_path, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            data = row.get('data', '')
            name = row.get('name', data)
            title = row.get('title', '')
            company = row.get('company', '')
            out_name = row.get('output', f"{name.replace(' ', '_')}.png")
            out_path = os.path.join(output_dir, out_name)
            gen = BadgeGenerator(data, name, title, company, **kwargs)
            gen.generate(out_path)

def main():
    parser = argparse.ArgumentParser(description="Генератор бейджей с QR-кодом")
    parser.add_argument("--data", required=True, help="Данные для QR-кода")
    parser.add_argument("--name", help="Имя на бейдже (если не указано, используется data)")
    parser.add_argument("--title", help="Должность")
    parser.add_argument("--company", help="Компания")
    parser.add_argument("--output", default="badge.png", help="Выходной файл (PNG/PDF)")
    parser.add_argument("--size", type=int, default=200, help="Размер QR-кода (пиксели)")
    parser.add_argument("--logo", help="Путь к логотипу для вставки в QR")
    parser.add_argument("--color", default="#000000", help="Цвет QR-кода (HEX)")
    parser.add_argument("--template", help="Файл шаблона (JSON)")
    parser.add_argument("--batch", help="CSV-файл для пакетной генерации")
    parser.add_argument("--output-dir", default=".", help="Директория для пакетного вывода")
    args = parser.parse_args()

    if args.batch:
        # Пакетный режим
        kwargs = {
            'size': args.size,
            'color': args.color,
            'logo_path': args.logo,
            'template': args.template
        }
        batch_generate(args.batch, args.output_dir, **kwargs)
    else:
        gen = BadgeGenerator(
            data=args.data,
            name=args.name or args.data,
            title=args.title,
            company=args.company,
            size=args.size,
            color=args.color,
            logo_path=args.logo,
            template=args.template
        )
        gen.generate(args.output)

if __name__ == "__main__":
    main()
